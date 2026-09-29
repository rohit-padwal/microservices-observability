package com.example.paymentservice.service;

import com.example.paymentservice.client.FraudServiceClient;
import com.example.paymentservice.model.Payment;
import com.example.paymentservice.queue.NotificationDispatcher;
import com.example.paymentservice.repository.PaymentRepository;
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
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private FraudServiceClient fraudServiceClient;
    @Mock private NotificationDispatcher notificationDispatcher;
    @InjectMocks private PaymentService paymentService;

    @Test
    void administratorMayResolvePendingPaymentAsFailed() {
        Payment payment = payment(Payment.PaymentStatus.PENDING);
        when(paymentRepository.findById(4L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);

        Payment updated = paymentService.updateStatus(4L, Payment.PaymentStatus.FAILED);

        assertEquals(Payment.PaymentStatus.FAILED, updated.getStatus());
        verify(paymentRepository).save(payment);
    }

    @Test
    void settledPaymentsCannotBeRewrittenOrDeleted() {
        Payment payment = payment(Payment.PaymentStatus.COMPLETED);
        when(paymentRepository.findById(4L)).thenReturn(Optional.of(payment));

        assertThrows(PaymentService.PaymentConflictException.class,
                () -> paymentService.updateStatus(4L, Payment.PaymentStatus.FAILED));
        assertThrows(PaymentService.PaymentConflictException.class, () -> paymentService.deletePendingPayment(4L));
        verify(paymentRepository, never()).delete(any(Payment.class));
    }

    private Payment payment(Payment.PaymentStatus status) {
        Payment payment = new Payment();
        payment.setOrderId(12L);
        payment.setAmount(new BigDecimal("45.00"));
        payment.setStatus(status);
        return payment;
    }
}