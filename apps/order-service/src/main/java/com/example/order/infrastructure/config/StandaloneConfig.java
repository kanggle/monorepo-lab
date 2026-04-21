package com.example.order.infrastructure.config;

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

    @Bean
    OrderEventPublisher standaloneOrderEventPublisher(
            @Value("${services.payment-service.url:http://localhost:8087}") String paymentServiceUrl
    ) {
        RestClient restClient = RestClient.builder()
                .baseUrl(paymentServiceUrl)
                .build();
        return new StandaloneOrderEventPublisher(restClient);
    }
}
