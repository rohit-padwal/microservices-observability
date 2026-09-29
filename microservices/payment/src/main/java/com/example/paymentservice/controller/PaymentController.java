package com.example.paymentservice.controller;

import com.example.paymentservice.model.Payment;
import com.example.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RestController
@Validated
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> processPayment(@Valid @RequestBody CreatePaymentRequest request) {
        Payment payment = new Payment();
        payment.setOrderId(request.orderId());
        payment.setAmount(request.amount());
        Payment result = paymentService.processPayment(payment);
        HttpStatus status = result.getStatus() == Payment.PaymentStatus.FAILED
                ? HttpStatus.PAYMENT_REQUIRED
                : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(PaymentResponse.from(result));
    }

    @GetMapping
    public PageResponse<PaymentResponse> searchPayments(
            @RequestParam(required = false) Payment.PaymentStatus status,
            @RequestParam(required = false) @Positive Long orderId,
            @RequestParam(required = false) @Positive BigDecimal minimumAmount,
            @RequestParam(required = false) @Positive BigDecimal maximumAmount,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt") @Pattern(regexp = "id|createdAt|amount|status") String sort,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction) {
        Page<PaymentResponse> result = paymentService.searchPayments(status, orderId, minimumAmount,
                maximumAmount, page, size, sort, direction).map(PaymentResponse::from);
        return PageResponse.from(result);
    }

    @GetMapping("/statistics")
    public PaymentService.PaymentStatistics getStatistics() {
        return paymentService.getStatistics();
    }

    @GetMapping("/{id}")
    public PaymentResponse getPaymentById(@PathVariable @Positive Long id) {
        return PaymentResponse.from(paymentService.getPaymentById(id));
    }

    @GetMapping("/order/{orderId}")
    public PageResponse<PaymentResponse> getPaymentsByOrderId(
            @PathVariable @Positive Long orderId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt") @Pattern(regexp = "id|createdAt|amount|status") String sort,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction) {
        Page<PaymentResponse> result = paymentService.searchPayments(null, orderId, null, null,
                page, size, sort, direction).map(PaymentResponse::from);
        return PageResponse.from(result);
    }

    @PatchMapping("/{id}/status")
    public PaymentResponse updateStatus(@PathVariable @Positive Long id,
                                        @Valid @RequestBody UpdatePaymentStatusRequest request) {
        return PaymentResponse.from(paymentService.updateStatus(id, request.status()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePendingPayment(@PathVariable @Positive Long id) {
        paymentService.deletePendingPayment(id);
        return ResponseEntity.noContent().build();
    }

    public record CreatePaymentRequest(@NotNull @Positive Long orderId,
                                       @NotNull @Positive BigDecimal amount) {}

    public record UpdatePaymentStatusRequest(@NotNull Payment.PaymentStatus status) {}

    public record PaymentResponse(Long id, Long orderId, BigDecimal amount,
                                  Payment.PaymentStatus status, Instant createdAt) {
        static PaymentResponse from(Payment payment) {
            return new PaymentResponse(payment.getId(), payment.getOrderId(), payment.getAmount(),
                    payment.getStatus(), payment.getCreatedAt());
        }
    }

    public record PageResponse<T>(List<T> content, int number, int size, int totalPages, long totalElements) {
        static <T> PageResponse<T> from(Page<T> page) {
            return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                    page.getTotalPages(), page.getTotalElements());
        }
    }
}