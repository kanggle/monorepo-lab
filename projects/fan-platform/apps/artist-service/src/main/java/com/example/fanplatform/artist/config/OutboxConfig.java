package com.example.fanplatform.artist.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox wiring for artist-service (TASK-FAN-BE-022, outbox v2).
 *
 * <p>The publisher itself is the {@code @Component}
 * {@link com.example.fanplatform.artist.adapter.out.messaging.ArtistOutboxPublisher}
 * (a thin {@code AbstractOutboxPublisher} subclass). This config supplies the
 * {@link TransactionTemplate} the subclass needs by constructor injection — the
 * publisher reads pending rows and marks them published in separate transactions
 * on the background scheduler thread. A {@code Clock} bean already exists
 * ({@code ClockConfig}) and is reused by the write adapter + publisher.
 *
 * <p><b>Legacy v1 tables (TASK-MONO-406).</b> The v1 relay
 * ({@code ArtistOutboxPollingScheduler extends
 * com.example.messaging.outbox.OutboxPollingScheduler}) is gone and the write path
 * ({@code ArtistEventPublisherAdapter}) no longer extends the lib
 * {@code BaseEventPublisher} / uses {@code OutboxWriter}. TASK-MONO-312 deleted the
 * lib's v1 {@code OutboxJpaEntity} / {@code OutboxWriter} / {@code OutboxPublisher}
 * beans and TASK-MONO-406 deleted the remaining {@code OutboxAutoConfiguration} /
 * {@code OutboxJpaConfig} / {@code ProcessedEventJpaEntity}, so no library entity maps
 * the v1 {@code outbox} / {@code processed_events} tables any more. Those tables still
 * exist in the schema ({@code V1__init.sql}; applied migrations are immutable) but are
 * now unmapped, and {@code ddl-auto=validate} only validates mapped entities. The live
 * outbox table is {@code artist_outbox} ({@code V2__artist_outbox_v2.sql}), mapped by
 * this service's own {@code ArtistOutboxJpaEntity}.
 */
@Configuration
public class OutboxConfig {

    @Bean
    TransactionTemplate outboxTransactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
