package com.example.fraudservice.repository;

import com.example.fraudservice.model.FraudCheck;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class FraudCheckRepositoryTest {

    @Autowired
    private FraudCheckRepository fraudCheckRepository;

    @Test
    void searchFiltersPagesAndAggregatesRiskDecisions() {
        fraudCheckRepository.save(check(31L, 41L, "200.00", 0.2, FraudCheck.Decision.APPROVE));
        fraudCheckRepository.save(check(31L, 42L, "1200.00", 0.7, FraudCheck.Decision.REVIEW));
        fraudCheckRepository.save(check(32L, 43L, "6000.00", 0.9, FraudCheck.Decision.DECLINE));

        var page = fraudCheckRepository.search(FraudCheck.Decision.REVIEW, 31L, null,
                new BigDecimal("1000.00"), null, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "riskScore")));

        assertEquals(1, page.getTotalElements());
        assertEquals(42L, page.getContent().getFirst().getPaymentId());
        assertEquals(1, fraudCheckRepository.countByDecision(FraudCheck.Decision.REVIEW));
        assertEquals(0.6, fraudCheckRepository.averageRiskScore(), 0.0001);
    }

    private FraudCheck check(Long orderId, Long paymentId, String amount, double score, FraudCheck.Decision decision) {
        return FraudCheck.builder().orderId(orderId).paymentId(paymentId).amount(new BigDecimal(amount))
                .riskScore(score).decision(decision).build();
    }
}