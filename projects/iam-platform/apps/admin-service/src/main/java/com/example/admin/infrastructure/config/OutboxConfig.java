package com.example.admin.infrastructure.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox wiring for admin-service (TASK-BE-452 — outbox v1 → v2).
 *
 * <p>The relay itself is the {@code @Component}
 * {@link com.example.admin.infrastructure.outbox.AdminOutboxPublisher}
 * (a thin {@code AbstractOutboxPublisher} subclass). This config supplies the two
 * infrastructure beans the subclass + the two write adapters need by constructor
 * injection that admin-service did not previously declare:
 * <ul>
 *   <li>a {@link TransactionTemplate} — the relay reads pending rows and marks them
 *       published in separate transactions on the background scheduler thread.</li>
 *   <li>a {@link Clock} — used by the relay's publish-lag metric and the write
 *       adapters' {@code created_at} stamp (admin-service had no Clock bean).</li>
 * </ul>
 *
 * <p><b>Legacy v1 tables (TASK-MONO-406).</b> The v1 relay
 * ({@code AdminOutboxPollingScheduler extends OutboxPollingScheduler}) is gone and the
 * write path ({@code OutboxAdminEventPublisher} + {@code OutboxTenantEventPublisher})
 * no longer uses the lib {@code OutboxWriter}. TASK-MONO-312 deleted the lib's v1
 * {@code OutboxJpaEntity} / {@code OutboxWriter} / {@code OutboxPublisher} beans and
 * TASK-MONO-406 deleted the remaining {@code OutboxAutoConfiguration} /
 * {@code OutboxJpaConfig} / {@code ProcessedEventJpaEntity}, so no library entity maps
 * the v1 {@code outbox} / {@code processed_events} tables ({@code V0002}/{@code V0016})
 * any more. They survive only because applied migrations are immutable, and
 * {@code ddl-auto=validate} only validates mapped entities. The live outbox table is
 * {@code admin_outbox} ({@code V0038}), mapped by this service's own
 * {@code AdminOutboxJpaEntity}.
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
