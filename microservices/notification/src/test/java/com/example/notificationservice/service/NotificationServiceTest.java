package com.example.notificationservice.service;

import com.example.notificationservice.model.Notification;
import com.example.notificationservice.queue.NotificationQueueProcessor;
import com.example.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationQueueProcessor queueProcessor;
    @Mock private NotificationRepository notificationRepository;
    @InjectMocks private NotificationService notificationService;

    @Test
    void failedDeliveryMayBeCorrectedAndRetried() {
        Notification failed = notification(Notification.Status.FAILED);
        when(notificationRepository.findById(8L)).thenReturn(Optional.of(failed));
        when(notificationRepository.save(failed)).thenReturn(failed);

        Notification corrected = notificationService.updateFailed(8L, 13L, 21L,
                new BigDecimal("56.00"), "PAYMENT_SUCCESS");
        Notification retry = notificationService.retry(8L);

        assertEquals(13L, corrected.getPaymentId());
        assertEquals(new BigDecimal("56.00"), corrected.getAmount());
        assertEquals(Notification.Status.FAILED, retry.getStatus());
    }

    @Test
    void sentDeliveryIsImmutable() {
        when(notificationRepository.findById(9L)).thenReturn(Optional.of(notification(Notification.Status.SENT)));

        assertThrows(NotificationService.NotificationConflictException.class,
                () -> notificationService.retry(9L));
        assertThrows(NotificationService.NotificationConflictException.class,
                () -> notificationService.updateFailed(9L, 1L, 1L, BigDecimal.TEN, "PAYMENT_FAILED"));
    }

    private Notification notification(Notification.Status status) {
        return Notification.builder().paymentId(3L).orderId(5L).amount(new BigDecimal("20.00"))
                .type("PAYMENT_FAILED").status(status).build();
    }
}