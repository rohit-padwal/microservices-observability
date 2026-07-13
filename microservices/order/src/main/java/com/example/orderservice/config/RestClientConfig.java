package com.example.orderservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

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
                .build();
    }

    private ClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(5).toMillis());
        return factory;
    }
}
