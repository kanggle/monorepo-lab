package com.example.finance.ledger.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox-relay infrastructure (3rd increment, TASK-FIN-BE-009). Supplies the
 * {@link TransactionTemplate} the shared {@code AbstractOutboxPublisher} uses to
 * poll pending rows and mark them published in a fresh transaction after the Kafka
 * ACK ({@code TransactionTemplate} is NOT auto-configured by Spring Boot).
 *
 * <p>The {@code java.time.Clock} bean the relay also needs is provided by
 * {@link ClockConfig}; the {@code ledger.outbox.*} polling properties are declared
 * in {@code application.yml} (and gated off in the test profile so non-Kafka runs
 * skip the scheduler — see {@code LedgerOutboxPublisher}).
 */
@Configuration
public class OutboxConfig {

    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
