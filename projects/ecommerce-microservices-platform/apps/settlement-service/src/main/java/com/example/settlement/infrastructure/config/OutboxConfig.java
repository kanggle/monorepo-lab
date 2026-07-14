package com.example.settlement.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox wiring for settlement-service (TASK-BE-447, outbox v2).
 *
 * <p>The publisher is the {@code @Component}
 * {@link com.example.settlement.infrastructure.event.SettlementOutboxPublisher} (a
 * thin {@code AbstractOutboxPublisher} subclass, {@code @Profile("!standalone")}).
 * This config supplies the one extra bean it needs: a {@link TransactionTemplate}
 * (the {@code Clock} bean is already provided by {@code ClockConfig}).
 *
 * <p>The v1 relay ({@code SettlementOutboxPollingScheduler}) is gone and the write
 * path no longer uses the lib {@code OutboxWriter}. TASK-MONO-406 then deleted the lib
 * {@code OutboxAutoConfiguration} and the {@code OutboxJpaConfig} it imported, so there is
 * nothing left to import or exclude (settlement-service used to {@code exclude} that
 * auto-config outright — see {@code SettlementServiceApplication}). {@code libs/java-messaging}
 * now ships no {@code @Entity} at all ({@code OutboxRowEntity} is a {@code @MappedSuperclass},
 * resolved through the entity hierarchy rather than by scanning). The application's
 * {@code @EntityScan} lists only {@code "com.example.settlement"} — as it has since
 * TASK-BE-461, and correctly so.
 *
 * <p>Consequently the v1 {@code outbox} table (V2) is mapped by no entity any more and is
 * inert. {@code ddl-auto=validate} only validates mapped entities, so its presence is no
 * longer required; it stays in the schema because applied Flyway migrations are immutable.
 * See {@code V4__settlement_outbox_v2.sql}, whose EntityScan rationale is superseded by
 * MONO-406. The locally-owned {@code processed_event} consumer-dedupe table is a separate
 * concern and is unaffected.
 */
@Configuration
public class OutboxConfig {

    @Bean
    TransactionTemplate outboxTransactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
