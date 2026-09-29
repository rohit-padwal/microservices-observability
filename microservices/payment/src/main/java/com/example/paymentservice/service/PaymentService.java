package com.example.paymentservice.service;

import com.example.paymentservice.client.FraudServiceClient;
import com.example.paymentservice.exception.PaymentNotFoundException;
import com.example.paymentservice.model.Payment;
import com.example.paymentservice.queue.NotificationDispatcher;
import com.example.paymentservice.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Orchestrates payment persistence, synchronous fraud decisions, simulated settlement, and queued notifications. */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORT_FIELDS = Set.of("id", "createdAt", "amount", "status");

    private final PaymentRepository paymentRepository;
    private final FraudServiceClient fraudServiceClient;
    private final NotificationDispatcher notificationDispatcher;

    public PaymentService(PaymentRepository paymentRepository,
                          FraudServiceClient fraudServiceClient,
                          NotificationDispatcher notificationDispatcher) {
        this.paymentRepository = paymentRepository;
        this.fraudServiceClient = fraudServiceClient;
        this.notificationDispatcher = notificationDispatcher;
    }

    /**
     * Full payment pipeline for one request:
     *   1. Persist as PENDING
     *   2. Synchronous fraud check (payment -> fraud -> back)
     *   3. Simulate settlement with the (fake) payment provider
     *   4. Persist final status
     *   5. Fire-and-forget notification via the async queue
     * This is the "Gateway -> Payment -> Fraud -> Database -> Notification"
     * path that shows up as a single trace in Tempo.
     */
    @Transactional
    public Payment processPayment(Payment payment) {
        MDC.put("order_id", String.valueOf(payment.getOrderId()));
        try {
            payment.setStatus(Payment.PaymentStatus.PENDING);
            Payment saved = paymentRepository.save(payment);
            MDC.put("payment_id", String.valueOf(saved.getId()));
            log.info("Payment received amount={}", saved.getAmount());

            FraudServiceClient.FraudCheckResult fraudResult =
                    fraudServiceClient.checkPayment(saved.getOrderId(), saved.getId(), saved.getAmount());

            boolean declined;
            String declineReason = null;

            // Fraud failures are fail-closed: do not settle when screening is unavailable or declines the request.
            if (!fraudResult.approved()) {
                declined = true;
                declineReason = "FRAUD_" + fraudResult.decision();
            } else {
                // Simulate calling out to a real payment provider (Stripe/Adyen/etc.),
                // which occasionally declines for reasons unrelated to fraud.
                declined = ThreadLocalRandom.current().nextInt(100) < 10;
                if (declined) {
                    declineReason = "PROVIDER_DECLINED";
                }
            }

            saved.setStatus(declined ? Payment.PaymentStatus.FAILED : Payment.PaymentStatus.COMPLETED);
            Payment finalPayment = paymentRepository.save(saved);

            if (declined) {
                MDC.put("error_code", declineReason);
                log.warn("Payment declined reason={}", declineReason);
            } else {
                log.info("Payment completed");
            }

            notificationDispatcher.dispatchPaymentCompleted(
                    finalPayment.getId(), finalPayment.getOrderId(), finalPayment.getAmount(), !declined,
                    currentAuthorizationHeader());

            return finalPayment;
        } finally {
            MDC.remove("order_id");
            MDC.remove("payment_id");
            MDC.remove("error_code");
        }
    }

    /** Capture auth before queueing because worker threads do not inherit the servlet request context. */
    private String currentAuthorizationHeader() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        }
        return null;
    }

    @Transactional(readOnly = true)
    public Page<Payment> searchPayments(Payment.PaymentStatus status, Long orderId,
                                        BigDecimal minimumAmount, BigDecimal maximumAmount,
                                        int page, int size, String sortField, Sort.Direction direction) {
        // Cap page size and restrict sort properties so HTTP input never becomes arbitrary JPA sort metadata.
        if (!SORT_FIELDS.contains(sortField)) throw new IllegalArgumentException("Unsupported payment sort field");
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(direction, sortField));
        return paymentRepository.search(status, orderId, minimumAmount, maximumAmount, pageable);
    }

    @Transactional(readOnly = true)
    public Payment getPaymentById(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public PaymentStatistics getStatistics() {
        BigDecimal completedAmount = paymentRepository.sumAmountByStatus(Payment.PaymentStatus.COMPLETED);
        return new PaymentStatistics(paymentRepository.count(),
                paymentRepository.countByStatus(Payment.PaymentStatus.PENDING),
                paymentRepository.countByStatus(Payment.PaymentStatus.COMPLETED),
                paymentRepository.countByStatus(Payment.PaymentStatus.FAILED),
                completedAmount == null ? BigDecimal.ZERO : completedAmount);
    }

    @Transactional
    public Payment updateStatus(Long id, Payment.PaymentStatus status) {
        Payment payment = getPaymentById(id);
        // Settled money is audit data: the only administrative transition allowed is pending -> failed.
        if (payment.getStatus() != Payment.PaymentStatus.PENDING || status != Payment.PaymentStatus.FAILED) {
            throw new PaymentConflictException("Only pending payments may be administratively marked failed");
        }
        payment.setStatus(status);
        return paymentRepository.save(payment);
    }

    @Transactional
    public void deletePendingPayment(Long id) {
        Payment payment = getPaymentById(id);
        // Preserve completed/failed financial history; deletion is limited to an unsettled record.
        if (payment.getStatus() != Payment.PaymentStatus.PENDING) {
            throw new PaymentConflictException("Completed payment records cannot be deleted");
        }
        paymentRepository.delete(payment);
    }

    public record PaymentStatistics(long totalPayments, long pendingPayments, long completedPayments,
                                    long failedPayments, BigDecimal completedAmount) {}

    public static class PaymentConflictException extends RuntimeException {
        public PaymentConflictException(String message) { super(message); }
    }
}
