package com.example.fanplatform.membership.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox wiring for membership-service (TASK-FAN-BE-020, outbox v2).
 *
 * <p>The publisher itself is the {@code @Component}
 * {@link com.example.fanplatform.membership.infrastructure.outbox.MembershipOutboxPublisher}
 * (a thin {@code AbstractOutboxPublisher} subclass). This config supplies the
 * {@link TransactionTemplate} the subclass needs by constructor injection — the
 * publisher reads pending rows and marks them published in separate transactions
 * on the background scheduler thread. A {@code Clock} bean already exists
 * ({@code ClockConfig}) and is reused by the write adapter + publisher.
 *
 * <p>The v1 relay ({@code MembershipOutboxPollingScheduler extends
 * com.example.messaging.outbox.OutboxPollingScheduler}) is gone and the write path
 * ({@code OutboxMembershipEventPublisher}) no longer uses the lib
 * {@code OutboxWriter}. The lib {@code OutboxAutoConfiguration} is intentionally
 * retained (not excluded): its {@code OutboxJpaConfig} EntityScan is what keeps the
 * v1 {@code outbox} / {@code processed_events} tables required under
 * {@code ddl-auto=validate} (see {@code V1__init.sql} + {@code V3__membership_outbox_v2.sql}).
 * The v1 {@code OutboxWriter} / {@code OutboxPublisher} beans it still registers are
 * no longer referenced.
 */
@Configuration
public class OutboxConfig {

    @Bean
    TransactionTemplate outboxTransactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
