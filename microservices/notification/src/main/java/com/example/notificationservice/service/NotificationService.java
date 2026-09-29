package com.example.notificationservice.service;

import com.example.notificationservice.model.Notification;
import com.example.notificationservice.queue.NotificationQueueProcessor;
import com.example.notificationservice.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;

@Service
public class NotificationService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORT_FIELDS = Set.of("id", "createdAt", "amount", "status", "type");

    private final NotificationQueueProcessor queueProcessor;
    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationQueueProcessor queueProcessor,
                               NotificationRepository notificationRepository) {
        this.queueProcessor = queueProcessor;
        this.notificationRepository = notificationRepository;
    }

    public void enqueue(Long paymentId, Long orderId, BigDecimal amount, String type) {
        queueProcessor.enqueue(paymentId, orderId, amount, type);
    }

    @Transactional(readOnly = true)
    public Page<Notification> search(Notification.Status status, Long paymentId, Long orderId, String type,
                                     BigDecimal minimumAmount, BigDecimal maximumAmount,
                                     int page, int size, String sortField, Sort.Direction direction) {
        if (!SORT_FIELDS.contains(sortField)) throw new IllegalArgumentException("Unsupported notification sort field");
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(direction, sortField));
        String normalizedType = type == null || type.isBlank() ? null : type.trim();
        return notificationRepository.search(status, paymentId, orderId, normalizedType,
                minimumAmount, maximumAmount, pageable);
    }

    @Transactional(readOnly = true)
    public Notification getById(Long id) {
        return notificationRepository.findById(id).orElseThrow(() -> new NotificationNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Notification retry(Long id) {
        Notification notification = getById(id);
        if (notification.getStatus() != Notification.Status.FAILED) {
            throw new NotificationConflictException("Only failed notifications can be retried");
        }
        return notification;
    }

    @Transactional
    public Notification updateFailed(Long id, Long paymentId, Long orderId, BigDecimal amount, String type) {
        Notification notification = getById(id);
        if (notification.getStatus() != Notification.Status.FAILED) {
            throw new NotificationConflictException("Only failed notification records can be corrected");
        }
        notification.setPaymentId(paymentId);
        notification.setOrderId(orderId);
        notification.setAmount(amount);
        notification.setType(type);
        return notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public NotificationStatistics getStatistics() {
        return new NotificationStatistics(notificationRepository.count(),
                notificationRepository.countByStatus(Notification.Status.SENT),
                notificationRepository.countByStatus(Notification.Status.FAILED));
    }

    @Transactional
    public void delete(Long id) {
        notificationRepository.delete(getById(id));
    }

    public static class NotificationNotFoundException extends RuntimeException {
        public NotificationNotFoundException(Long id) { super("Notification not found: " + id); }
    }

    public static class NotificationConflictException extends RuntimeException {
        public NotificationConflictException(String message) { super(message); }
    }

    public record NotificationStatistics(long total, long sent, long failed) {}
}