package com.example.paymentservice.repository;

import com.example.paymentservice.model.Payment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class PaymentRepositoryTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void searchFiltersPagesAndAggregatesCompletedPayments() {
        paymentRepository.save(payment(21L, "30.00", Payment.PaymentStatus.COMPLETED));
        paymentRepository.save(payment(21L, "50.00", Payment.PaymentStatus.COMPLETED));
        paymentRepository.save(payment(22L, "50.00", Payment.PaymentStatus.FAILED));

        var page = paymentRepository.search(Payment.PaymentStatus.COMPLETED, 21L,
                new BigDecimal("40.00"), null, PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "amount")));

        assertEquals(1, page.getTotalElements());
        assertEquals(new BigDecimal("50.00"), page.getContent().getFirst().getAmount());
        assertEquals(2, paymentRepository.countByStatus(Payment.PaymentStatus.COMPLETED));
        assertEquals(new BigDecimal("80.00"), paymentRepository.sumAmountByStatus(Payment.PaymentStatus.COMPLETED));
    }

    private Payment payment(Long orderId, String amount, Payment.PaymentStatus status) {
        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setAmount(new BigDecimal(amount));
        payment.setStatus(status);
        return payment;
    }
}