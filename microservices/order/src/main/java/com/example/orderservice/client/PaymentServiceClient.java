package com.example.orderservice.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;

@Component
public class PaymentServiceClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceClient.class);

    private final RestClient paymentServiceClient;

    public PaymentServiceClient(RestClient paymentServiceClient) {
        this.paymentServiceClient = paymentServiceClient;
    }

    public PaymentResult requestPayment(Long orderId, BigDecimal amount) {
        try {
            PaymentResponse response = paymentServiceClient.post()
                    .uri("/api/payments")
                    .body(new PaymentRequest(orderId, amount))
                    .retrieve()
                    .body(PaymentResponse.class);
            boolean success = response != null && "COMPLETED".equals(response.status());
            return new PaymentResult(success, response == null ? null : response.id());
        } catch (RestClientResponseException ex) {
            // payment-service returns 402 for declined payments and other 4xx/5xx for errors
            log.warn("Payment request failed for orderId={} status={}", orderId, ex.getStatusCode());
            return new PaymentResult(false, null);
        }
    }

    public record PaymentRequest(Long orderId, BigDecimal amount) {}
    public record PaymentResponse(Long id, Long orderId, BigDecimal amount, String status) {}
    public record PaymentResult(boolean success, Long paymentId) {}
}
