package com.example.erp.approval.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
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
 *
 * <p><b>{@code @EnableScheduling} (TASK-ERP-BE-042).</b> Without it Spring never
 * registers the publisher's {@code @Scheduled} method — <em>silently</em>: no
 * exception, no warning, the bean exists and its method is simply never called. The
 * relay had therefore never run once. Measured on the demo stack before the fix:
 * {@code approval_outbox} holding an unpublished row, every {@code erp.approval.*}
 * topic at end-offset {@code 0}, and the read-model's delegation/approval facts
 * empty. That judgement came from the <em>broker</em>, not from the consumer side —
 * "the projection is empty" is equally consistent with a lagging consumer, and only
 * an end-offset of 0 separates the two.
 *
 * <p>It lives here rather than in a separate {@code SchedulingConfig} (the shape
 * notification-service uses) because the outbox relay is the <em>only</em> scheduled
 * work in this service: co-locating the switch with the thing it switches on means a
 * reader of the relay wiring cannot miss it. notification-service's own
 * {@code SchedulingConfig} follows the same principle — it sits next to the delivery
 * retry scheduler it enables.
 *
 * <p>Enabling scheduling is inert on its own. Whether the relay actually polls is
 * still governed by {@code outbox.polling.enabled} on
 * {@code ApprovalOutboxPublisher}; with that gate off (slice/unit tests) no
 * {@code @Scheduled} bean exists and nothing is registered.
 */
@Configuration
@EnableScheduling
public class OutboxConfig {

    @Bean
    TransactionTemplate outboxTransactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
