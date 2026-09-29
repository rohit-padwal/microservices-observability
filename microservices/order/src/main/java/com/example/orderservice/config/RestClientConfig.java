package com.example.orderservice.config;

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

/** Builds the observed Payment client and relays the authenticated bearer token across the service boundary. */
@Configuration
public class RestClientConfig {

    @Value("${services.payment-service.base-url}")
    private String paymentServiceBaseUrl;

    /**
     * Builder is auto-configured by Spring Boot with ObservationRestClientCustomizer,
     * which propagates trace context (traceparent headers) and records client-side
     * timing metrics (http.client.requests) automatically — no manual wiring needed.
     */
    @Bean
    public RestClient paymentServiceRestClient(RestClient.Builder builder) {
        return builder
                .baseUrl(paymentServiceBaseUrl)
                .requestFactory(requestFactory())
                .requestInterceptor(bearerTokenRelay())
                .build();
    }

    /** Relay the authenticated caller to Payment so downstream role checks see the same principal. */
    private ClientHttpRequestInterceptor bearerTokenRelay() {
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

    private ClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(5).toMillis());
        return factory;
    }
}
