package com.example.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox wiring for payment-service (TASK-BE-449, outbox v2).
 *
 * <p>The publisher is the {@code @Component}
 * {@link com.example.payment.adapter.out.event.PaymentOutboxPublisher} (a thin
 * {@code AbstractOutboxPublisher} subclass). This config supplies the one extra
 * bean it needs: a {@link TransactionTemplate} (the {@code Clock} bean is already
 * provided by {@code ClockConfig}).
 *
 * <p>The v1 relay ({@code PaymentEventOutboxRelay}) is gone and the write path no
 * longer uses the lib {@code OutboxWriter}. TASK-MONO-406 then deleted the lib
 * {@code OutboxAutoConfiguration} and the {@code OutboxJpaConfig} it imported, so there is
 * nothing left to import or exclude: {@code libs/java-messaging} now ships no
 * {@code @Entity} at all ({@code OutboxRowEntity} is a {@code @MappedSuperclass}, resolved
 * through the entity hierarchy rather than by scanning).
 *
 * <p>Consequently the v1 {@code outbox} (V3) and {@code processed_events} (V4) tables are
 * mapped by no entity in payment-service any more — the lib's entities were the only
 * mappings — and are inert. {@code ddl-auto=validate} only validates mapped entities, so
 * their presence is no longer required; they stay in the schema because applied Flyway
 * migrations are immutable. See {@code V8__payment_outbox_v2.sql}, whose EntityScan
 * rationale is superseded by MONO-406.
 */
@Configuration
public class OutboxConfig {

    @Bean
    TransactionTemplate outboxTransactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
