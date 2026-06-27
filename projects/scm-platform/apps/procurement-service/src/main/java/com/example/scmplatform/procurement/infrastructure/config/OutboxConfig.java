package com.example.scmplatform.procurement.infrastructure.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox wiring for procurement-service (TASK-SCM-BE-032, outbox v2).
 *
 * <p>The publisher itself is the {@code @Component}
 * {@link com.example.scmplatform.procurement.infrastructure.outbox.ProcurementOutboxPublisher}
 * (a thin {@code AbstractOutboxPublisher} subclass). This config supplies the two
 * infrastructure beans that subclass + the write adapter need by constructor
 * injection and that procurement-service did not previously declare:
 * <ul>
 *   <li>a {@link TransactionTemplate} — the publisher reads pending rows and marks
 *       them published in separate transactions on the background scheduler thread;</li>
 *   <li>a {@link Clock} (system UTC) — used by the publisher for the publish-lag
 *       metric and by the write adapter for {@code occurred_at} / the envelope
 *       {@code occurredAt}.</li>
 * </ul>
 *
 * <p>The v1 relay ({@code ProcurementOutboxPollingScheduler extends
 * com.example.messaging.outbox.OutboxPollingScheduler}) is gone and the write path
 * ({@code OutboxProcurementEventPublisher}) no longer uses the lib
 * {@code OutboxWriter}. The lib {@code OutboxAutoConfiguration} is intentionally
 * retained (not excluded): its {@code OutboxJpaConfig} EntityScan is what keeps the
 * v1 {@code outbox} / {@code processed_events} tables required under
 * {@code ddl-auto=validate} (see {@code V1__init.sql} + {@code V3__procurement_outbox_v2.sql}).
 * The v1 {@code OutboxWriter} / {@code OutboxPublisher} beans it still registers are
 * no longer referenced.
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
