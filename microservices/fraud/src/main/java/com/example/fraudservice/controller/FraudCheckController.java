package com.example.fraudservice.controller;

import com.example.fraudservice.model.FraudCheck;
import com.example.fraudservice.service.FraudDetectionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/** Exposes risk evaluations and bounded review/search APIs without exposing persistence entities directly. */
@RestController
@Validated
@RequestMapping("/api/fraud-checks")
public class FraudCheckController {

    private final FraudDetectionService fraudDetectionService;

    public FraudCheckController(FraudDetectionService fraudDetectionService) {
        this.fraudDetectionService = fraudDetectionService;
    }

    @PostMapping
    public ResponseEntity<FraudCheckResponse> checkPayment(@Valid @RequestBody FraudCheckRequest request) {
        FraudCheck check = fraudDetectionService.evaluate(request.orderId(), request.paymentId(), request.amount());
        return ResponseEntity.status(HttpStatus.OK).body(FraudCheckResponse.from(check));
    }

    /** Filters are applied in SQL; page is zero-based, size is bounded, and sort is restricted to known fields. */
    @GetMapping
    public PageResponse<FraudCheckResponse> search(
            @RequestParam(required = false) FraudCheck.Decision decision,
            @RequestParam(required = false) @Positive Long orderId,
            @RequestParam(required = false) @Positive Long paymentId,
            @RequestParam(required = false) @Positive BigDecimal minimumAmount,
            @RequestParam(required = false) @Positive BigDecimal maximumAmount,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt")
            @Pattern(regexp = "id|createdAt|riskScore|amount|decision") String sort,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction) {
        return PageResponse.from(fraudDetectionService.search(decision, orderId, paymentId,
                minimumAmount, maximumAmount, page, size, sort, direction).map(FraudCheckResponse::from));
    }

    @GetMapping("/statistics")
    public FraudDetectionService.FraudStatistics getStatistics() {
        return fraudDetectionService.getStatistics();
    }

    @GetMapping("/{id}")
    public FraudCheckResponse getById(@PathVariable @Positive Long id) {
        return FraudCheckResponse.from(fraudDetectionService.getById(id));
    }

    @GetMapping("/payment/{paymentId}")
    public PageResponse<FraudCheckResponse> getByPaymentId(@PathVariable @Positive Long paymentId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(fraudDetectionService.search(null, null, paymentId, null, null,
                page, size, "createdAt", Sort.Direction.DESC).map(FraudCheckResponse::from));
    }

    @GetMapping("/order/{orderId}")
    public PageResponse<FraudCheckResponse> getByOrderId(@PathVariable @Positive Long orderId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(fraudDetectionService.search(null, orderId, null, null, null,
                page, size, "createdAt", Sort.Direction.DESC).map(FraudCheckResponse::from));
    }

    @PatchMapping("/{id}/decision")
    public FraudCheckResponse resolveReview(@PathVariable @Positive Long id,
            @Valid @RequestBody ResolveFraudReviewRequest request) {
        return FraudCheckResponse.from(fraudDetectionService.resolveReview(id, request.decision()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReview(@PathVariable @Positive Long id) {
        fraudDetectionService.deleteReview(id);
        return ResponseEntity.noContent().build();
    }

    /** Only payment/order facts enter the risk engine; clients cannot submit the resulting decision or score. */
    public record FraudCheckRequest(@NotNull @Positive Long orderId,
                                    @NotNull @Positive Long paymentId,
                                    @NotNull @Positive BigDecimal amount) {}

    public record ResolveFraudReviewRequest(@NotNull FraudCheck.Decision decision) {}

    /** Stable read DTO for the audit result produced by the fraud service. */
    public record FraudCheckResponse(Long id, Long orderId, Long paymentId, BigDecimal amount,
                                     String decision, double riskScore, java.time.Instant createdAt) {
        static FraudCheckResponse from(FraudCheck check) {
            return new FraudCheckResponse(check.getId(), check.getOrderId(), check.getPaymentId(),
                    check.getAmount(), check.getDecision().name(), check.getRiskScore(), check.getCreatedAt());
        }
    }

    public record PageResponse<T>(List<T> content, int number, int size, int totalPages, long totalElements) {
        static <T> PageResponse<T> from(Page<T> page) {
            return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                    page.getTotalPages(), page.getTotalElements());
        }
    }
}
