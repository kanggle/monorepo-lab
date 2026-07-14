package com.example.review.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox wiring for review-service (TASK-BE-445, outbox v2).
 *
 * <p>The publisher is the {@code @Component}
 * {@link com.example.review.infrastructure.event.ReviewOutboxPublisher} (a thin
 * {@code AbstractOutboxPublisher} subclass). This config supplies the one extra
 * bean it needs: a {@link TransactionTemplate} (the {@code Clock} bean is already
 * provided by {@code ClockConfig}).
 *
 * <p>The v1 relay ({@code ReviewOutboxPollingScheduler}) is gone and the write
 * path no longer uses the lib {@code OutboxWriter}. TASK-MONO-406 then deleted the lib
 * {@code OutboxAutoConfiguration} and the {@code OutboxJpaConfig} it imported, so there is
 * nothing left to import or exclude: {@code libs/java-messaging} now ships no
 * {@code @Entity} at all ({@code OutboxRowEntity} is a {@code @MappedSuperclass}, resolved
 * through the entity hierarchy rather than by scanning).
 *
 * <p>Consequently the v1 {@code outbox} and {@code processed_events} tables (both created
 * in V4) are mapped by no entity in review-service any more — the lib's entities were the
 * only mappings — and are inert. {@code ddl-auto=validate} only validates mapped entities,
 * so their presence is no longer required; they stay in the schema because applied Flyway
 * migrations are immutable. See {@code V6__review_outbox_v2.sql}, whose EntityScan
 * rationale is superseded by MONO-406.
 */
@Configuration
public class OutboxConfig {

    @Bean
    TransactionTemplate outboxTransactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
