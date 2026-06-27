package com.example.account.infrastructure.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox wiring for account-service (TASK-BE-451 — outbox v1 → v2).
 *
 * <p>The relay itself is the {@code @Component}
 * {@link com.example.account.infrastructure.outbox.AccountOutboxPublisher}
 * (a thin {@code AbstractOutboxPublisher} subclass). This config supplies the two
 * infrastructure beans the subclass + the two write adapters need by constructor
 * injection that account-service did not previously declare:
 * <ul>
 *   <li>a {@link TransactionTemplate} — the relay reads pending rows and marks them
 *       published in separate transactions on the background scheduler thread.</li>
 *   <li>a {@link Clock} — used by the relay's publish-lag metric and the write
 *       adapters' {@code created_at} stamp (account-service had no Clock bean).</li>
 * </ul>
 *
 * <p><b>KEEP lib auto-config.</b> The v1 relay
 * ({@code AccountOutboxPollingScheduler extends OutboxPollingScheduler}) is gone and
 * the write path ({@code OutboxAccountEventPublisher} +
 * {@code OutboxTenantDomainSubscriptionEventPublisher}) no longer uses the lib
 * {@code OutboxWriter}. The lib {@code OutboxAutoConfiguration} is intentionally
 * RETAINED (not excluded): its {@code OutboxJpaConfig} EntityScan keeps the v1
 * {@code outbox} / {@code processed_events} tables required under
 * {@code ddl-auto=validate} (see {@code V0003}/{@code V0005} + {@code V0026}). The v1
 * {@code OutboxWriter} / {@code OutboxPublisher} beans it still registers are no
 * longer referenced.
 */
@Configuration
public class OutboxConfig {

    @Bean
    TransactionTemplate outboxTransactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    Clock outboxClock() {
        return Clock.systemUTC();
    }
}
