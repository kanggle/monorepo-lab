package com.example.security.infrastructure.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox wiring for security-service (TASK-BE-453 — outbox v1 → v2).
 *
 * <p>The relay itself is the {@code @Component}
 * {@link com.example.security.infrastructure.outbox.SecurityOutboxPublisher}
 * (a thin {@code AbstractOutboxPublisher} subclass). This config supplies the two
 * infrastructure beans the subclass + write adapter need by constructor injection
 * that security-service did not previously declare:
 * <ul>
 *   <li>a {@link TransactionTemplate} — the relay reads pending rows and marks them
 *       published in separate transactions on the background scheduler thread.</li>
 *   <li>a {@link Clock} — used by the relay's publish-lag metric and the write
 *       adapter's {@code occurredAt} (security-service did not previously declare one).</li>
 * </ul>
 *
 * <p><b>Legacy v1 tables (TASK-MONO-406).</b> The v1 relay
 * ({@code SecurityOutboxPollingScheduler extends OutboxPollingScheduler}) is gone
 * and the write path ({@code OutboxSecurityEventPublisher}) no longer uses the lib
 * {@code OutboxWriter}. TASK-MONO-312 deleted the lib's v1 {@code OutboxJpaEntity} /
 * {@code OutboxWriter} / {@code OutboxPublisher} beans and TASK-MONO-406 deleted the
 * remaining {@code OutboxAutoConfiguration} / {@code OutboxJpaConfig} /
 * {@code ProcessedEventJpaEntity}, so the library maps no table at all any more. The v1
 * {@code outbox_events} table ({@code V0003}/{@code V0004}/{@code V0005}) is now unmapped
 * legacy — it survives only because applied migrations are immutable, and
 * {@code ddl-auto=validate} only validates mapped entities. {@code processed_events} is
 * still mapped, by this service's OWN
 * {@code com.example.security.infrastructure.persistence.ProcessedEventJpaEntity} (added
 * by TASK-MONO-406) — <b>do not drop it</b>. The live outbox table is
 * {@code security_outbox} ({@code V0011}), mapped by {@code SecurityOutboxJpaEntity}.
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
