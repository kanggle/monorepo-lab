package com.example.community.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox wiring for community-service (TASK-BE-455 — outbox v1 → v2).
 *
 * <p>The relay itself is the {@code @Component}
 * {@link com.example.community.infrastructure.outbox.CommunityOutboxPublisher}
 * (a thin {@code AbstractOutboxPublisher} subclass). This config supplies the one
 * infrastructure bean the subclass + write adapter need by constructor injection
 * that community-service did not previously declare:
 * <ul>
 *   <li>a {@link TransactionTemplate} — the relay reads pending rows and marks them
 *       published in separate transactions on the background scheduler thread.</li>
 * </ul>
 *
 * <p><b>No Clock bean here.</b> Unlike auth/security/membership, community-service
 * ALREADY declares a {@code Clock systemUTC()} bean
 * ({@link com.example.community.infrastructure.config.ClockConfig}); the relay and
 * write adapter inject that existing bean. Declaring a second {@code Clock} here
 * would be a duplicate-bean conflict.
 *
 * <p><b>KEEP lib auto-config.</b> The v1 relay
 * ({@code CommunityOutboxPollingScheduler extends OutboxPollingScheduler}) is gone
 * and the write path ({@code OutboxCommunityEventPublisher}) no longer uses the lib
 * {@code OutboxWriter}. The lib {@code OutboxAutoConfiguration} is intentionally
 * RETAINED (not excluded): its {@code OutboxJpaConfig} EntityScan is what keeps the
 * v1 {@code outbox} / {@code processed_events} tables required under
 * {@code ddl-auto=validate} (see {@code V0004}/{@code V0005} + {@code V0006}). The
 * v1 {@code OutboxWriter} / {@code OutboxPublisher} beans it still registers are no
 * longer referenced.
 */
@Configuration
public class OutboxConfig {

    @Bean
    TransactionTemplate outboxTransactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
