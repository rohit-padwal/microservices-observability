package com.example.fraudservice.service;

import com.example.fraudservice.model.FraudCheck;
import com.example.fraudservice.repository.FraudCheckRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Owns the demo risk model, persisted fraud decisions, and the narrow manual-review lifecycle. */
@Service
public class FraudDetectionService {

    private static final Logger log = LoggerFactory.getLogger(FraudDetectionService.class);

    private static final BigDecimal REVIEW_THRESHOLD = new BigDecimal("1000");
    private static final BigDecimal DECLINE_THRESHOLD = new BigDecimal("5000");
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORT_FIELDS = Set.of("id", "createdAt", "riskScore", "amount", "decision");

    private final FraudCheckRepository fraudCheckRepository;

    public FraudDetectionService(FraudCheckRepository fraudCheckRepository) {
        this.fraudCheckRepository = fraudCheckRepository;
    }

    /**
     * Toy risk model: larger amounts are inherently riskier, plus a little
     * random jitter so identical amounts don't always produce identical
     * verdicts (real fraud models factor in velocity, device fingerprint,
     * geo-mismatch, etc. — out of scope for this demo).
     */
    @Transactional
    public FraudCheck evaluate(Long orderId, Long paymentId, BigDecimal amount) {
        MDC.put("order_id", String.valueOf(orderId));
        MDC.put("payment_id", String.valueOf(paymentId));
        try {
            double jitter = ThreadLocalRandom.current().nextDouble(0, 0.15);
            double amountFactor = Math.min(1.0, amount.doubleValue() / DECLINE_THRESHOLD.doubleValue());
            double riskScore = Math.min(1.0, amountFactor + jitter);

            FraudCheck.Decision decision;
            if (amount.compareTo(DECLINE_THRESHOLD) >= 0 || riskScore > 0.85) {
                decision = FraudCheck.Decision.DECLINE;
            } else if (amount.compareTo(REVIEW_THRESHOLD) >= 0 || riskScore > 0.5) {
                decision = FraudCheck.Decision.REVIEW;
            } else {
                decision = FraudCheck.Decision.APPROVE;
            }

            FraudCheck check = FraudCheck.builder()
                    .orderId(orderId)
                    .paymentId(paymentId)
                    .amount(amount)
                    .riskScore(riskScore)
                    .decision(decision)
                    .build();

            FraudCheck saved = fraudCheckRepository.save(check);

            if (decision == FraudCheck.Decision.DECLINE) {
                MDC.put("error_code", "FRAUD_DECLINE");
                log.warn("Fraud check declined riskScore={}", riskScore);
            } else if (decision == FraudCheck.Decision.REVIEW) {
                log.warn("Fraud check flagged for review riskScore={}", riskScore);
            } else {
                log.info("Fraud check approved riskScore={}", riskScore);
            }
            return saved;
        } finally {
            MDC.remove("order_id");
            MDC.remove("payment_id");
            MDC.remove("error_code");
        }
    }

    @Transactional(readOnly = true)
    public Page<FraudCheck> search(FraudCheck.Decision decision, Long orderId, Long paymentId,
                                   BigDecimal minimumAmount, BigDecimal maximumAmount,
                                   int page, int size, String sortField, Sort.Direction direction) {
        // Prevent arbitrary property sorting and unbounded result loads from public query parameters.
        if (!SORT_FIELDS.contains(sortField)) throw new IllegalArgumentException("Unsupported fraud-check sort field");
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(direction, sortField));
        return fraudCheckRepository.search(decision, orderId, paymentId, minimumAmount, maximumAmount, pageable);
    }

    @Transactional(readOnly = true)
    public FraudCheck getById(Long id) {
        return fraudCheckRepository.findById(id).orElseThrow(() -> new FraudCheckNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public FraudStatistics getStatistics() {
        Double average = fraudCheckRepository.averageRiskScore();
        return new FraudStatistics(fraudCheckRepository.count(),
                fraudCheckRepository.countByDecision(FraudCheck.Decision.APPROVE),
                fraudCheckRepository.countByDecision(FraudCheck.Decision.REVIEW),
                fraudCheckRepository.countByDecision(FraudCheck.Decision.DECLINE), average == null ? 0 : average);
    }

    @Transactional
    public FraudCheck resolveReview(Long id, FraudCheck.Decision decision) {
        FraudCheck check = getById(id);
        // A human may resolve REVIEW, but approved/declined decisions are immutable audit outcomes.
        if (check.getDecision() != FraudCheck.Decision.REVIEW || decision == FraudCheck.Decision.REVIEW) {
            throw new FraudConflictException("Only review decisions can be resolved to APPROVE or DECLINE");
        }
        check.setDecision(decision);
        return fraudCheckRepository.save(check);
    }

    @Transactional
    public void deleteReview(Long id) {
        FraudCheck check = getById(id);
        if (check.getDecision() != FraudCheck.Decision.REVIEW) {
            throw new FraudConflictException("Terminal fraud decisions are immutable and cannot be deleted");
        }
        fraudCheckRepository.delete(check);
    }

    public record FraudStatistics(long totalChecks, long approved, long underReview,
                                  long declined, double averageRiskScore) {}

    public static class FraudCheckNotFoundException extends RuntimeException {
        public FraudCheckNotFoundException(Long id) { super("Fraud check not found: " + id); }
    }

    public static class FraudConflictException extends RuntimeException {
        public FraudConflictException(String message) { super(message); }
    }
}
