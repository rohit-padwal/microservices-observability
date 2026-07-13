package com.example.fraudservice.service;

import com.example.fraudservice.model.FraudCheck;
import com.example.fraudservice.repository.FraudCheckRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class FraudDetectionService {

    private static final Logger log = LoggerFactory.getLogger(FraudDetectionService.class);

    private static final BigDecimal REVIEW_THRESHOLD = new BigDecimal("1000");
    private static final BigDecimal DECLINE_THRESHOLD = new BigDecimal("5000");

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
}
