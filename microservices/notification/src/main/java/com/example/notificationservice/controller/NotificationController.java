package com.example.notificationservice.controller;

import com.example.notificationservice.model.Notification;
import com.example.notificationservice.queue.NotificationQueueProcessor;
import com.example.notificationservice.repository.NotificationRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationQueueProcessor queueProcessor;
    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationQueueProcessor queueProcessor,
                                  NotificationRepository notificationRepository) {
        this.queueProcessor = queueProcessor;
        this.notificationRepository = notificationRepository;
    }

    /**
     * Accepts the notification request and enqueues it for async processing,
     * returning immediately — the caller (payment-service) doesn't block
     * waiting for the "email" to actually go out.
     */
    @PostMapping
    public ResponseEntity<Void> requestNotification(@RequestBody NotificationRequest request) {
        queueProcessor.enqueue(request.paymentId(), request.orderId(), request.amount(), request.type());
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/payment/{paymentId}")
    public List<Notification> getByPaymentId(@PathVariable Long paymentId) {
        return notificationRepository.findByPaymentId(paymentId);
    }

    @GetMapping("/order/{orderId}")
    public List<Notification> getByOrderId(@PathVariable Long orderId) {
        return notificationRepository.findByOrderId(orderId);
    }

    public record NotificationRequest(
            @NotNull Long paymentId,
            @NotNull Long orderId,
            @NotNull BigDecimal amount,
            @NotBlank String type) {}
}
