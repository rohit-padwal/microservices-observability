package com.example.paymentservice.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;

@Component
public class FraudServiceClient {

    private static final Logger log = LoggerFactory.getLogger(FraudServiceClient.class);

    private final RestClient fraudServiceClient;

    public FraudServiceClient(@Qualifier("fraudServiceRestClient") RestClient fraudServiceClient) {
        this.fraudServiceClient = fraudServiceClient;
    }

    /**
     * Synchronously asks fraud-service to score this payment before we settle it.
     * If fraud-service is unreachable or errors, we fail closed (treat as DECLINE)
     * rather than silently letting a payment through unchecked.
     */
    public FraudCheckResult checkPayment(Long orderId, Long paymentId, BigDecimal amount) {
        try {
            FraudCheckResponse response = fraudServiceClient.post()
                    .uri("/api/fraud-checks")
                    .body(new FraudCheckRequest(orderId, paymentId, amount))
                    .retrieve()
                    .body(FraudCheckResponse.class);
            if (response == null) {
                return new FraudCheckResult(false, "FRAUD_SERVICE_EMPTY_RESPONSE");
            }
            boolean approved = "APPROVE".equals(response.decision());
            return new FraudCheckResult(approved, response.decision());
        } catch (RestClientResponseException ex) {
            log.error("fraud-service returned {} for paymentId={}", ex.getStatusCode(), paymentId);
            return new FraudCheckResult(false, "FRAUD_SERVICE_ERROR");
        } catch (Exception ex) {
            log.error("fraud-service unreachable for paymentId={}", paymentId, ex);
            return new FraudCheckResult(false, "FRAUD_SERVICE_UNAVAILABLE");
        }
    }

    public record FraudCheckRequest(Long orderId, Long paymentId, BigDecimal amount) {}
    public record FraudCheckResponse(Long id, String decision, double riskScore) {}
    public record FraudCheckResult(boolean approved, String decision) {}
}
