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

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

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
                    finalPayment.getId(), finalPayment.getOrderId(), finalPayment.getAmount(), !declined);

            return finalPayment;
        } finally {
            MDC.remove("order_id");
            MDC.remove("payment_id");
            MDC.remove("error_code");
        }
    }

    @Transactional(readOnly = true)
    public List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Payment getPaymentById(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Payment> getPaymentsByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId);
    }
}
