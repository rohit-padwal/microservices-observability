package com.example.paymentservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/** Configures observed, timeout-bounded clients for synchronous Fraud calls and Notification dispatch. */
@Configuration
public class RestClientConfig {

    @Value("${services.fraud-service.base-url}")
    private String fraudServiceBaseUrl;

    @Value("${services.notification-service.base-url}")
    private String notificationServiceBaseUrl;

    @Bean
    public RestClient fraudServiceRestClient(RestClient.Builder builder) {
        return builder
                .baseUrl(fraudServiceBaseUrl)
                .requestFactory(requestFactory(2))
                .requestInterceptor(bearerTokenRelay())
                .build();
    }

    @Bean
    public RestClient notificationServiceRestClient(RestClient.Builder builder) {
        return builder
                .baseUrl(notificationServiceBaseUrl)
                .requestFactory(requestFactory(2))
                .build();
    }

    private ClientHttpRequestInterceptor bearerTokenRelay() {
        // Preserve user identity across synchronous Payment -> Fraud calls; Boot still adds tracing/metrics.
        return (request, body, execution) -> {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                String authorization = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
                if (authorization != null && !authorization.isBlank()) {
                    request.getHeaders().set(HttpHeaders.AUTHORIZATION, authorization);
                }
            }
            return execution.execute(request, body);
        };
    }

    private ClientHttpRequestFactory requestFactory(int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(timeoutSeconds).toMillis());
        return factory;
    }
}
