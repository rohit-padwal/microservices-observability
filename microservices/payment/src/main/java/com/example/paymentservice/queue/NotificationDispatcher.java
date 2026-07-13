package com.example.paymentservice.queue;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.concurrent.*;

/**
 * Fire-and-forget dispatch of "payment completed/failed" events to
 * notification-service, decoupled from the request thread via a bounded
 * in-memory queue.
 *
 * This stands in for a real broker (Kafka/RabbitMQ/SQS) in this demo:
 * the queue depth gauge below is exactly the kind of signal you'd otherwise
 * get from a broker's consumer-lag metric, and it's what the
 * "Queue backlog" alert watches.
 */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);
    private static final int QUEUE_CAPACITY = 1000;

    private final RestClient notificationServiceClient;
    private final MeterRegistry meterRegistry;

    private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private ThreadPoolExecutor executor;

    public NotificationDispatcher(@Qualifier("notificationServiceRestClient") RestClient notificationServiceClient, MeterRegistry meterRegistry) {
        this.notificationServiceClient = notificationServiceClient;
        this.meterRegistry = meterRegistry;
    }

    @jakarta.annotation.PostConstruct
    void init() {
        executor = new ThreadPoolExecutor(
                2, 4, 60, TimeUnit.SECONDS, queue,
                new ThreadPoolExecutor.CallerRunsPolicy());
        meterRegistry.gauge("notification.queue.size", queue, BlockingQueue::size);
    }

    public void dispatchPaymentCompleted(Long paymentId, Long orderId, BigDecimal amount, boolean success) {
        executor.submit(() -> send(paymentId, orderId, amount, success));
    }

    private void send(Long paymentId, Long orderId, BigDecimal amount, boolean success) {
        try {
            notificationServiceClient.post()
                    .uri("/api/notifications")
                    .body(new NotificationRequest(
                            paymentId, orderId, amount,
                            success ? "PAYMENT_SUCCESS" : "PAYMENT_FAILED"))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.error("Failed to dispatch notification for paymentId={}", paymentId, ex);
        }
    }

    @PreDestroy
    void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    public record NotificationRequest(Long paymentId, Long orderId, BigDecimal amount, String type) {}
}
