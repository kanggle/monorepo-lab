package com.example.settlement.infrastructure.config;

import com.example.settlement.application.port.SettlementEventPublisher;
import com.example.settlement.infrastructure.event.NoopSettlementEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Standalone-profile wiring (H2, no Kafka). Supplies a no-op
 * {@link SettlementEventPublisher} so the period-close use case runs locally without
 * the outbox/Kafka relay (the production publisher is {@code @Profile("!standalone")}).
 */
@Configuration
@Profile("standalone")
public class StandaloneConfig {

    @Bean
    SettlementEventPublisher standaloneSettlementEventPublisher() {
        return new NoopSettlementEventPublisher();
    }
}
