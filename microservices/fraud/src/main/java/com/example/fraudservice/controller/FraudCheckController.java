package com.example.fraudservice.controller;

import com.example.fraudservice.model.FraudCheck;
import com.example.fraudservice.repository.FraudCheckRepository;
import com.example.fraudservice.service.FraudDetectionService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/fraud-checks")
public class FraudCheckController {

    private final FraudDetectionService fraudDetectionService;
    private final FraudCheckRepository fraudCheckRepository;

    public FraudCheckController(FraudDetectionService fraudDetectionService,
                                FraudCheckRepository fraudCheckRepository) {
        this.fraudDetectionService = fraudDetectionService;
        this.fraudCheckRepository = fraudCheckRepository;
    }

    @PostMapping
    public ResponseEntity<FraudCheckResponse> checkPayment(@RequestBody FraudCheckRequest request) {
        FraudCheck check = fraudDetectionService.evaluate(request.orderId(), request.paymentId(), request.amount());
        return ResponseEntity.status(HttpStatus.OK).body(
                new FraudCheckResponse(check.getId(), check.getDecision().name(), check.getRiskScore()));
    }

    @GetMapping("/payment/{paymentId}")
    public List<FraudCheck> getByPaymentId(@PathVariable Long paymentId) {
        return fraudCheckRepository.findByPaymentId(paymentId);
    }

    @GetMapping("/order/{orderId}")
    public List<FraudCheck> getByOrderId(@PathVariable Long orderId) {
        return fraudCheckRepository.findByOrderId(orderId);
    }

    public record FraudCheckRequest(
            @NotNull Long orderId,
            @NotNull Long paymentId,
            @NotNull @Positive BigDecimal amount) {}

    public record FraudCheckResponse(Long id, String decision, double riskScore) {}
}
