package com.example.shipping.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox wiring for shipping-service (TASK-BE-446, outbox v2).
 *
 * <p>The publisher is the {@code @Component}
 * {@link com.example.shipping.infrastructure.event.ShippingOutboxPublisher} (a
 * thin {@code AbstractOutboxPublisher} subclass). This config supplies the one
 * extra bean it needs: a {@link TransactionTemplate} (the {@code Clock} bean is
 * already provided by {@code ClockConfig}).
 *
 * <p>The v1 relay ({@code OutboxPollingScheduler}) is gone and the write path no
 * longer uses the lib {@code OutboxWriter}. TASK-MONO-406 then deleted the lib
 * {@code OutboxAutoConfiguration} and the {@code OutboxJpaConfig} it imported, so there is
 * nothing left to import or exclude: {@code libs/java-messaging} now ships no
 * {@code @Entity} at all ({@code OutboxRowEntity} is a {@code @MappedSuperclass}, resolved
 * through the entity hierarchy rather than by scanning), and the application's
 * {@code @EntityScan} no longer lists {@code "com.example.messaging"}.
 *
 * <p>Consequently the v1 {@code outbox} table (V3) is mapped by no entity any more and is
 * inert; it stays in the schema because applied Flyway migrations are immutable, not because
 * {@code ddl-auto=validate} demands it (validate only checks mapped entities).
 * {@code processed_events} (V4) is still mapped — by shipping-service's own
 * {@link com.example.shipping.infrastructure.persistence.ProcessedEventJpaEntity}, which
 * MONO-406 moved out of the library into this service. See {@code V9__shipping_outbox_v2.sql},
 * whose EntityScan rationale is superseded by MONO-406.
 */
@Configuration
public class OutboxConfig {

    @Bean
    TransactionTemplate outboxTransactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
