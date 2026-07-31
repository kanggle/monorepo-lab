package com.example.order.infrastructure.config;

import com.example.common.resilience.ResilienceClientFactory;
import com.example.order.application.port.OrderEventPublisher;
import com.example.order.infrastructure.event.StandaloneOrderEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;

@Configuration
@Profile("standalone")
public class StandaloneConfig {

    /**
     * ADR-MONO-058 D7 (TASK-BE-570) — previously {@code RestClient.builder().baseUrl(...)
     * .build()} with no request factory (zero timeout). Lower urgency than search/review
     * (standalone profile only, not the default deployment topology), but still a real gap
     * if this profile is ever used in anger. Now built via {@link ResilienceClientFactory}
     * with explicit, non-zero connect/read timeouts.
     */
    @Bean
    OrderEventPublisher standaloneOrderEventPublisher(
            @Value("${services.payment-service.url:http://localhost:8087}") String paymentServiceUrl,
            @Value("${services.payment-service.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${services.payment-service.read-timeout-ms:5000}") int readTimeoutMs
    ) {
        RestClient restClient = ResilienceClientFactory.buildRestClient(paymentServiceUrl, connectTimeoutMs, readTimeoutMs);
        return new StandaloneOrderEventPublisher(restClient);
    }
}
