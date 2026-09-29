package com.example.fraudservice.service;

import com.example.fraudservice.model.FraudCheck;
import com.example.fraudservice.repository.FraudCheckRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudDetectionServiceTest {

    @Mock private FraudCheckRepository fraudCheckRepository;
    @InjectMocks private FraudDetectionService fraudDetectionService;

    @Test
    void reviewCanBeResolvedButTerminalDecisionCannotBeRewritten() {
        FraudCheck review = check(FraudCheck.Decision.REVIEW);
        when(fraudCheckRepository.findById(2L)).thenReturn(Optional.of(review));
        when(fraudCheckRepository.save(review)).thenReturn(review);

        FraudCheck resolved = fraudDetectionService.resolveReview(2L, FraudCheck.Decision.APPROVE);

        assertEquals(FraudCheck.Decision.APPROVE, resolved.getDecision());
        assertThrows(FraudDetectionService.FraudConflictException.class,
                () -> fraudDetectionService.resolveReview(2L, FraudCheck.Decision.DECLINE));
    }

    @Test
    void terminalFraudDecisionCannotBeDeleted() {
        when(fraudCheckRepository.findById(3L)).thenReturn(Optional.of(check(FraudCheck.Decision.DECLINE)));

        assertThrows(FraudDetectionService.FraudConflictException.class, () -> fraudDetectionService.deleteReview(3L));
        verify(fraudCheckRepository, never()).delete(any(FraudCheck.class));
    }

    private FraudCheck check(FraudCheck.Decision decision) {
        return FraudCheck.builder().orderId(1L).paymentId(2L)
                .amount(new java.math.BigDecimal("1200")).riskScore(0.7).decision(decision).build();
    }
}