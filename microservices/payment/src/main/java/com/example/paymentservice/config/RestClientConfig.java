package com.example.paymentservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

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
                .build();
    }

    @Bean
    public RestClient notificationServiceRestClient(RestClient.Builder builder) {
        return builder
                .baseUrl(notificationServiceBaseUrl)
                .requestFactory(requestFactory(2))
                .build();
    }

    private ClientHttpRequestFactory requestFactory(int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(timeoutSeconds).toMillis());
        return factory;
    }
}
