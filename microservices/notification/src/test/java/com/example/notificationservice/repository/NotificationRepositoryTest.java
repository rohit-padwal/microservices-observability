package com.example.notificationservice.repository;

import com.example.notificationservice.model.Notification;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void searchFiltersPagesAndCountsDeliveryStatuses() {
        notificationRepository.save(notification(51L, 61L, "30.00", "PAYMENT_SUCCESS", Notification.Status.SENT));
        notificationRepository.save(notification(51L, 62L, "40.00", "PAYMENT_FAILED", Notification.Status.FAILED));
        notificationRepository.save(notification(52L, 63L, "50.00", "PAYMENT_FAILED", Notification.Status.FAILED));

        var page = notificationRepository.search(Notification.Status.FAILED, null, 51L, "failed",
                new BigDecimal("35.00"), null, PageRequest.of(0, 1, Sort.by(Sort.Direction.ASC, "createdAt")));

        assertEquals(1, page.getTotalElements());
        assertEquals(62L, page.getContent().getFirst().getPaymentId());
        assertEquals(1, notificationRepository.countByStatus(Notification.Status.SENT));
        assertEquals(2, notificationRepository.countByStatus(Notification.Status.FAILED));
    }

    private Notification notification(Long orderId, Long paymentId, String amount,
                                       String type, Notification.Status status) {
        return Notification.builder().orderId(orderId).paymentId(paymentId)
                .amount(new BigDecimal(amount)).type(type).status(status).build();
    }
}