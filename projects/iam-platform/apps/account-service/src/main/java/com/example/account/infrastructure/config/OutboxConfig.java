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
 * <p><b>Legacy v1 tables (TASK-MONO-406).</b> The v1 relay
 * ({@code AccountOutboxPollingScheduler extends OutboxPollingScheduler}) is gone and
 * the write path ({@code OutboxAccountEventPublisher} +
 * {@code OutboxTenantDomainSubscriptionEventPublisher}) no longer uses the lib
 * {@code OutboxWriter}. TASK-MONO-312 deleted the lib's v1 {@code OutboxJpaEntity} /
 * {@code OutboxWriter} / {@code OutboxPublisher} beans and TASK-MONO-406 deleted the
 * remaining {@code OutboxAutoConfiguration} / {@code OutboxJpaConfig} /
 * {@code ProcessedEventJpaEntity}, so the library maps no table at all any more.
 * The v1 {@code outbox} table ({@code V0003}/{@code V0005}) is now unmapped legacy — it
 * survives only because applied migrations are immutable, and {@code ddl-auto=validate}
 * only validates mapped entities. {@code processed_events} is still mapped, by this
 * service's OWN {@code com.example.account.infrastructure.persistence.ProcessedEventJpaEntity}
 * (added by TASK-MONO-406) — <b>do not drop it</b>. The live outbox table is
 * {@code account_outbox} ({@code V0026}), mapped by {@code AccountOutboxJpaEntity}.
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
