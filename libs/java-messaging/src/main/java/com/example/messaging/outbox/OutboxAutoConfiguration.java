package com.example.messaging.outbox;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import(OutboxJpaConfig.class)
public class OutboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OutboxWriter outboxWriter(OutboxJpaRepository outboxJpaRepository) {
        return new OutboxWriter(outboxJpaRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxPublisher outboxPublisher(OutboxJpaRepository outboxJpaRepository) {
        return new OutboxPublisher(outboxJpaRepository);
    }
}
