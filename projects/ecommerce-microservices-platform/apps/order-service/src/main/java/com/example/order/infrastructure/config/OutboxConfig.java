package com.example.order.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox wiring for order-service (TASK-BE-448, outbox v2).
 *
 * <p>The publisher is the {@code @Component}
 * {@link com.example.order.infrastructure.event.OrderOutboxPublisher} (a thin
 * {@code AbstractOutboxPublisher} subclass, {@code @Profile("!standalone")}). This
 * config supplies the one extra bean it needs: a {@link TransactionTemplate} (the
 * {@code Clock} bean is already provided by {@code ClockConfig}).
 *
 * <p>The v1 relay ({@code OutboxPollingScheduler}) is gone and the write path no
 * longer uses the lib {@code OutboxWriter}. The lib {@code OutboxAutoConfiguration}
 * is intentionally retained (not excluded): its EntityScan (and the application's
 * explicit {@code @EntityScan("com.example.messaging")}) keep the v1 {@code outbox}
 * (V5) and {@code processed_events} (V6) tables required under {@code ddl-auto=validate};
 * see {@code V10__order_outbox_v2.sql}.
 */
@Configuration
public class OutboxConfig {

    @Bean
    TransactionTemplate outboxTransactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
