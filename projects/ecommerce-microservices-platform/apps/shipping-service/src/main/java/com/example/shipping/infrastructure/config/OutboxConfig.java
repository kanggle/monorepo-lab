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
 * longer uses the lib {@code OutboxWriter}. The lib {@code OutboxAutoConfiguration}
 * is intentionally retained (not excluded): its {@code OutboxJpaConfig} EntityScan
 * (and the application's explicit {@code @EntityScan("com.example.messaging")})
 * keep the v1 {@code outbox} / {@code processed_events} tables required under
 * {@code ddl-auto=validate}; see {@code V9__shipping_outbox_v2.sql}.
 */
@Configuration
public class OutboxConfig {

    @Bean
    TransactionTemplate outboxTransactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
