package com.example.product.infrastructure.config;

import com.example.product.domain.event.ProductEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Slf4j
@Configuration
@Profile("standalone")
public class StandaloneConfig {

    @Bean
    ProductEventPublisher noOpProductEventPublisher() {
        return event -> log.info("[standalone] product event: {}", event.eventType());
    }
}
