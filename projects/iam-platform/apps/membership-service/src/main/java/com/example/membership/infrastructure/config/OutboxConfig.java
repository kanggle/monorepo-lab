package com.example.membership.infrastructure.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox wiring for membership-service (TASK-BE-454 — outbox v1 → v2).
 *
 * <p>The relay itself is the {@code @Component}
 * {@link com.example.membership.infrastructure.outbox.MembershipOutboxPublisher}
 * (a thin {@code AbstractOutboxPublisher} subclass). This config supplies the two
 * infrastructure beans the subclass + write adapter need by constructor injection
 * that membership-service did not previously declare:
 * <ul>
 *   <li>a {@link TransactionTemplate} — the relay reads pending rows and marks them
 *       published in separate transactions on the background scheduler thread.</li>
 *   <li>a {@link Clock} — used by the relay's publish-lag metric and the write
 *       adapter's {@code occurredAt} (membership-service did not previously declare one).</li>
 * </ul>
 *
 * <p><b>KEEP lib auto-config.</b> The v1 relay
 * ({@code MembershipOutboxPollingScheduler extends OutboxPollingScheduler}) is gone
 * and the write path ({@code OutboxMembershipEventPublisher}) no longer uses the lib
 * {@code OutboxWriter}. The lib {@code OutboxAutoConfiguration} is intentionally
 * RETAINED (not excluded): its {@code OutboxJpaConfig} EntityScan is what keeps the
 * v1 {@code outbox} / {@code processed_events} tables required under
 * {@code ddl-auto=validate} (see {@code V0005}/{@code V0006} + {@code V0007}). The
 * v1 {@code OutboxWriter} / {@code OutboxPublisher} beans it still registers are no
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
