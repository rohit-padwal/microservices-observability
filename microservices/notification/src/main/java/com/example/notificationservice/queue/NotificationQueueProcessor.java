package com.example.notificationservice.queue;

import com.example.notificationservice.model.Notification;
import com.example.notificationservice.repository.NotificationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.*;

/**
 * Simulates a worker pool draining a notification queue (in place of a real
 * broker/consumer group). The queue-depth gauge is what the "Queue backlog"
 * alert and the Infrastructure dashboard chart.
 */
@Component
public class NotificationQueueProcessor {

    private static final Logger log = LoggerFactory.getLogger(NotificationQueueProcessor.class);
    private static final int QUEUE_CAPACITY = 2000;

    private final NotificationRepository notificationRepository;
    private final MeterRegistry meterRegistry;

    private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private ThreadPoolExecutor executor;

    public NotificationQueueProcessor(NotificationRepository notificationRepository,
                                      MeterRegistry meterRegistry) {
        this.notificationRepository = notificationRepository;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    void init() {
        // Small, deliberately modest pool: under heavy load the queue will
        // back up and the gauge will climb — that's the point of the demo.
        executor = new ThreadPoolExecutor(
                2, 3, 60, TimeUnit.SECONDS, queue,
                new ThreadPoolExecutor.CallerRunsPolicy());
        meterRegistry.gauge("notification.processing.queue.size", queue, BlockingQueue::size);
    }

    public void enqueue(Long paymentId, Long orderId, BigDecimal amount, String type) {
        executor.submit(() -> process(paymentId, orderId, amount, type));
    }

    private void process(Long paymentId, Long orderId, BigDecimal amount, String type) {
        MDC.put("payment_id", String.valueOf(paymentId));
        MDC.put("order_id", String.valueOf(orderId));
        try {
            // Simulate variable-latency delivery to an email/SMS provider.
            Thread.sleep(ThreadLocalRandom.current().nextInt(50, 300));

            boolean delivered = ThreadLocalRandom.current().nextInt(100) < 95;
            Notification notification = Notification.builder()
                    .paymentId(paymentId)
                    .orderId(orderId)
                    .amount(amount)
                    .type(type)
                    .status(delivered ? Notification.Status.SENT : Notification.Status.FAILED)
                    .build();
            notificationRepository.save(notification);

            if (delivered) {
                log.info("Notification sent type={}", type);
            } else {
                MDC.put("error_code", "NOTIFICATION_DELIVERY_FAILED");
                log.warn("Notification delivery failed type={}", type);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Notification processing interrupted paymentId={}", paymentId);
        } finally {
            MDC.remove("payment_id");
            MDC.remove("order_id");
            MDC.remove("error_code");
        }
    }

    @PreDestroy
    void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}
