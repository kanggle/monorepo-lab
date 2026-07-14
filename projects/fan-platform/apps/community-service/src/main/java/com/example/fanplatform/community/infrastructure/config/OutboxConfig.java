package com.example.fanplatform.community.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox wiring for community-service (TASK-FAN-BE-021, outbox v2).
 *
 * <p>The publisher itself is the {@code @Component}
 * {@link com.example.fanplatform.community.infrastructure.outbox.CommunityOutboxPublisher}
 * (a thin {@code AbstractOutboxPublisher} subclass). This config supplies the
 * {@link TransactionTemplate} the subclass needs by constructor injection — the
 * publisher reads pending rows and marks them published in separate transactions
 * on the background scheduler thread. A {@code Clock} bean already exists
 * ({@code ClockConfig}) and is reused by the write adapter + publisher.
 *
 * <p><b>Legacy v1 tables (TASK-MONO-406).</b> The v1 relay
 * ({@code CommunityOutboxPollingScheduler extends
 * com.example.messaging.outbox.OutboxPollingScheduler}) is gone and the write path
 * ({@code OutboxCommunityEventPublisher}) no longer uses the lib
 * {@code OutboxWriter}. TASK-MONO-312 deleted the lib's v1 {@code OutboxJpaEntity} /
 * {@code OutboxWriter} / {@code OutboxPublisher} beans and TASK-MONO-406 deleted the
 * remaining {@code OutboxAutoConfiguration} / {@code OutboxJpaConfig} /
 * {@code ProcessedEventJpaEntity}, so no library entity maps the v1 {@code outbox} /
 * {@code processed_events} tables any more. Those tables still exist in the schema
 * ({@code V1__init.sql}; applied migrations are immutable) but are now unmapped, and
 * {@code ddl-auto=validate} only validates mapped entities. The live outbox table is
 * {@code community_outbox} ({@code V2__community_outbox_v2.sql}), mapped by this
 * service's own {@code CommunityOutboxJpaEntity}.
 */
@Configuration
public class OutboxConfig {

    @Bean
    TransactionTemplate outboxTransactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
