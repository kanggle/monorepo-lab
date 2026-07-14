package com.example.erp.approval.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox wiring for approval-service (TASK-ERP-BE-025 — outbox v1 → v2).
 *
 * <p>The relay itself is the {@code @Component}
 * {@link com.example.erp.approval.infrastructure.outbox.ApprovalOutboxPublisher}
 * (a thin {@code AbstractOutboxPublisher} subclass). This config supplies the one
 * infrastructure bean the subclass needs by constructor injection that
 * approval-service did not previously declare:
 * <ul>
 *   <li>a {@link TransactionTemplate} — the publisher reads pending rows and marks
 *       them published in separate transactions on the background scheduler thread.</li>
 * </ul>
 * The {@code Clock} bean is already provided by {@code ClockConfig} (used by both
 * the relay's publish-lag metric and the write adapter's {@code occurredAt}).
 *
 * <p><b>Legacy v1 tables (TASK-MONO-406).</b> The v1 relay
 * ({@code ApprovalOutboxPollingScheduler extends OutboxPollingScheduler}) is gone
 * and the write path ({@code OutboxApprovalEventPublisher}) no longer uses the lib
 * {@code OutboxWriter}. TASK-MONO-312 deleted the lib's v1 {@code OutboxJpaEntity} /
 * {@code OutboxWriter} / {@code OutboxPublisher} beans and TASK-MONO-406 deleted the
 * remaining {@code OutboxAutoConfiguration} / {@code OutboxJpaConfig} /
 * {@code ProcessedEventJpaEntity}, so no library entity maps the v1 {@code outbox} /
 * {@code processed_events} tables any more. Those tables still exist in the schema
 * ({@code V1__init.sql}; applied migrations are immutable) but are now unmapped, and
 * {@code ddl-auto=validate} only validates mapped entities. The live outbox table is
 * {@code approval_outbox} ({@code V5__approval_outbox_v2.sql}), mapped by this
 * service's own {@code ApprovalOutboxJpaEntity}.
 */
@Configuration
public class OutboxConfig {

    @Bean
    TransactionTemplate outboxTransactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
