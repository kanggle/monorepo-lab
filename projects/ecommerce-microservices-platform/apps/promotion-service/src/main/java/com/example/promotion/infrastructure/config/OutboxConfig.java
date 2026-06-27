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
 * path no longer uses the lib {@code OutboxWriter}. The lib
 * {@code OutboxAutoConfiguration} is intentionally retained (not excluded): its
 * {@code OutboxJpaConfig} EntityScan is what keeps the v1 {@code outbox} /
 * {@code processed_events} tables required under {@code ddl-auto=validate}; see
 * {@code V7__promotion_outbox_v2.sql}. The v1 {@code OutboxWriter} /
 * {@code OutboxPublisher} beans it still registers are no longer referenced.
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
