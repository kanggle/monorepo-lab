package com.example.auth.infrastructure.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox wiring for auth-service (TASK-BE-450 — outbox v1 → v2).
 *
 * <p>The relay itself is the {@code @Component}
 * {@link com.example.auth.infrastructure.outbox.AuthOutboxPublisher}
 * (a thin {@code AbstractOutboxPublisher} subclass). This config supplies the two
 * infrastructure beans the subclass + write adapter need by constructor injection
 * that auth-service did not previously declare:
 * <ul>
 *   <li>a {@link TransactionTemplate} — the relay reads pending rows and marks them
 *       published in separate transactions on the background scheduler thread.</li>
 *   <li>a {@link Clock} — used by the relay's publish-lag metric and the write
 *       adapter's {@code occurredAt} (auth-service did not previously declare one).</li>
 * </ul>
 *
 * <p><b>Legacy v1 tables (TASK-MONO-406).</b> The v1 relay
 * ({@code AuthOutboxPollingScheduler extends OutboxPollingScheduler}) is gone and
 * the write path ({@code OutboxAuthEventPublisher}) no longer uses the lib
 * {@code OutboxWriter}. TASK-MONO-312 deleted the lib's v1 {@code OutboxJpaEntity} /
 * {@code OutboxWriter} / {@code OutboxPublisher} beans and TASK-MONO-406 deleted the
 * remaining {@code OutboxAutoConfiguration} / {@code OutboxJpaConfig} /
 * {@code ProcessedEventJpaEntity}, so no library entity maps the v1 {@code outbox} /
 * {@code processed_events} tables ({@code V0002}/{@code V0004}) any more. They survive
 * only because applied migrations are immutable, and {@code ddl-auto=validate} only
 * validates mapped entities. The live outbox table is {@code auth_outbox}
 * ({@code V0027}), mapped by this service's own {@code AuthOutboxJpaEntity}.
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
