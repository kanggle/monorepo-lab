package com.example.promotion.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox wiring for promotion-service (TASK-BE-444, outbox v2).
 *
 * <p>The publisher itself is the {@code @Component}
 * {@link com.example.promotion.infrastructure.event.PromotionOutboxPublisher} (a
 * thin {@code AbstractOutboxPublisher} subclass). This config supplies the one
 * infrastructure bean that subclass needs by constructor injection that is not
 * already present: a {@link TransactionTemplate} (the {@code Clock} bean is
 * provided by {@code ClockConfig}).
 *
 * <p>The v1 relay ({@code OutboxPollingScheduler extends
 * com.example.messaging.outbox.OutboxPollingScheduler}) is gone and the write
 * path no longer uses the lib {@code OutboxWriter}. TASK-MONO-406 then deleted the lib
 * {@code OutboxAutoConfiguration} and the {@code OutboxJpaConfig} it imported, so there is
 * nothing left to import or exclude: {@code libs/java-messaging} now ships no
 * {@code @Entity} at all ({@code OutboxRowEntity} is a {@code @MappedSuperclass}, resolved
 * through the entity hierarchy rather than by scanning).
 *
 * <p>Consequently the v1 {@code outbox} (V3) and {@code processed_events} (V5) tables are
 * mapped by no entity in promotion-service any more — the lib's entities were the only
 * mappings — and are inert. {@code ddl-auto=validate} only validates mapped entities, so
 * their presence is no longer required; they stay in the schema because applied Flyway
 * migrations are immutable. See {@code V7__promotion_outbox_v2.sql}, whose EntityScan
 * rationale is superseded by MONO-406.
 */
@Configuration
public class OutboxConfig {

    /**
     * Programmatic transaction boundary for the {@code AbstractOutboxPublisher}
     * poll loop (it reads pending rows and marks them published in separate
     * transactions on a background scheduler thread).
     */
    @Bean
    TransactionTemplate outboxTransactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
