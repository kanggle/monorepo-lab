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
 * longer uses the lib {@code OutboxWriter}. The lib {@code OutboxAutoConfiguration}
 * is intentionally retained (not excluded): its {@code OutboxJpaConfig} EntityScan
 * keeps the v1 {@code outbox} (V3) and {@code processed_events} (V4) tables required
 * under {@code ddl-auto=validate}; see {@code V8__payment_outbox_v2.sql}.
 */
@Configuration
public class OutboxConfig {

    @Bean
    TransactionTemplate outboxTransactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
