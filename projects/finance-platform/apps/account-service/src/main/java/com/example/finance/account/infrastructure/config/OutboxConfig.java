package com.example.finance.account.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox-relay infrastructure (TASK-FIN-BE-045 — outbox v1 → v2). Supplies the
 * {@link TransactionTemplate} the shared {@code AbstractOutboxPublisher} uses to
 * poll pending {@code account_outbox} rows and mark them published in a fresh
 * transaction after the Kafka ACK ({@code TransactionTemplate} is NOT
 * auto-configured by Spring Boot). Mirrors ledger-service's {@code OutboxConfig}.
 *
 * <p>The {@code java.time.Clock} bean the relay + write path also need is
 * provided by {@code ClockConfig}; the {@code account.outbox.*} polling
 * properties are declared in {@code application.yml} (gated off in the test
 * profile so non-Kafka runs skip the scheduler — see {@code AccountOutboxPublisher}).
 */
@Configuration
public class OutboxConfig {

    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
