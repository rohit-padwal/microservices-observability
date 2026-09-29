package com.example.notificationservice.controller;

import com.example.notificationservice.model.Notification;
import com.example.notificationservice.service.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** HTTP boundary for enqueueing notifications and querying delivery records owned by the worker. */
@RestController
@Validated
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Validates and enqueues delivery work without waiting for the simulated provider.
     * @param request payment/order/amount and an allowed notification type
     * @return {@code 202 Accepted}; the worker later persists SENT or FAILED
     */
    @PostMapping
    public ResponseEntity<Void> requestNotification(@Valid @RequestBody NotificationRequest request) {
        // Queue work and answer immediately; delivery remains owned by the notification worker.
        notificationService.enqueue(request.paymentId(), request.orderId(), request.amount(), request.type());
        return ResponseEntity.accepted().build();
    }

    /**
     * Searches persisted delivery records; filtering and pagination happen in the database.
     * @param page zero-based page index
     * @param size requested rows, bounded to 1..100
     * @param sort allow-listed entity property
     * @return stable page DTO with delivery records and total counts
     */
    @GetMapping
    public PageResponse<NotificationResponse> search(
            @RequestParam(required = false) Notification.Status status,
            @RequestParam(required = false) @Positive Long paymentId,
            @RequestParam(required = false) @Positive Long orderId,
            @RequestParam(required = false) @Size(max = 80) String type,
            @RequestParam(required = false) @Positive BigDecimal minimumAmount,
            @RequestParam(required = false) @Positive BigDecimal maximumAmount,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt")
            @Pattern(regexp = "id|createdAt|amount|status|type") String sort,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction) {
        return PageResponse.from(notificationService.search(status, paymentId, orderId, type,
                minimumAmount, maximumAmount, page, size, sort, direction).map(NotificationResponse::from));
    }

    /** Returns global SENT/FAILED counts; available to ADMIN only. */
    @GetMapping("/statistics")
    public NotificationService.NotificationStatistics getStatistics() {
        return notificationService.getStatistics();
    }

    @GetMapping("/{id}")
    public NotificationResponse getById(@PathVariable @Positive Long id) {
        return NotificationResponse.from(notificationService.getById(id));
    }

    @GetMapping("/payment/{paymentId}")
    public PageResponse<NotificationResponse> getByPaymentId(@PathVariable @Positive Long paymentId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(notificationService.search(null, paymentId, null, null, null, null,
                page, size, "createdAt", Sort.Direction.DESC).map(NotificationResponse::from));
    }

    @GetMapping("/order/{orderId}")
    public PageResponse<NotificationResponse> getByOrderId(@PathVariable @Positive Long orderId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(notificationService.search(null, null, orderId, null, null, null,
                page, size, "createdAt", Sort.Direction.DESC).map(NotificationResponse::from));
    }

    /** Enqueues a fresh attempt for a failed record; it does not rewrite the previous delivery result. */
    @PostMapping("/{id}/retry")
    public ResponseEntity<Void> retry(@PathVariable @Positive Long id) {
        Notification notification = notificationService.retry(id);
        notificationService.enqueue(notification.getPaymentId(), notification.getOrderId(),
                notification.getAmount(), notification.getType());
        return ResponseEntity.accepted().build();
    }

    /** Corrects metadata only while the prior delivery failed; successfully sent history is immutable. */
    @PutMapping("/{id}")
    public NotificationResponse correctFailed(@PathVariable @Positive Long id,
                                              @Valid @RequestBody CorrectNotificationRequest request) {
        return NotificationResponse.from(notificationService.updateFailed(id, request.paymentId(),
                request.orderId(), request.amount(), request.type()));
    }

    /** Deletes an identified notification record; the security filter restricts this action to ADMIN. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        notificationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Accepted event shape; the worker, not the API caller, supplies the eventual SENT/FAILED state. */
    public record NotificationRequest(@NotNull @Positive Long paymentId,
                                     @NotNull @Positive Long orderId,
                                     @NotNull @Positive BigDecimal amount,
                                     @NotBlank @Size(max = 80)
                                     @Pattern(regexp = "PAYMENT_SUCCESS|PAYMENT_FAILED") String type) {}

    /** Admin correction payload; the service rejects updates to records not in FAILED state. */
    public record CorrectNotificationRequest(@NotNull @Positive Long paymentId,
                                             @NotNull @Positive Long orderId,
                                             @NotNull @Positive BigDecimal amount,
                                             @NotBlank @Size(max = 80)
                                             @Pattern(regexp = "PAYMENT_SUCCESS|PAYMENT_FAILED") String type) {}

    /** Read DTO; status and createdAt are worker/database-owned and cannot be set through request bodies. */
    public record NotificationResponse(Long id, Long paymentId, Long orderId, BigDecimal amount,
                                       String type, Notification.Status status, Instant createdAt) {
        static NotificationResponse from(Notification notification) {
            return new NotificationResponse(notification.getId(), notification.getPaymentId(),
                    notification.getOrderId(), notification.getAmount(), notification.getType(),
                    notification.getStatus(), notification.getCreatedAt());
        }
    }

    /** Keep page JSON stable across services instead of serializing Spring Data implementation details. */
    /** Stable page envelope so clients are independent of Spring Data's internal serialization format. */
    public record PageResponse<T>(List<T> content, int number, int size, int totalPages, long totalElements) {
        static <T> PageResponse<T> from(Page<T> page) {
            return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                    page.getTotalPages(), page.getTotalElements());
        }
    }
}