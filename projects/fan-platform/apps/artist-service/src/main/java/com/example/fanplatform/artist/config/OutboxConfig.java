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
 * <p>The v1 relay ({@code ArtistOutboxPollingScheduler extends
 * com.example.messaging.outbox.OutboxPollingScheduler}) is gone and the write path
 * ({@code ArtistEventPublisherAdapter}) no longer extends the lib
 * {@code BaseEventPublisher} / uses {@code OutboxWriter}. The lib
 * {@code OutboxAutoConfiguration} is intentionally retained (not excluded): its
 * {@code OutboxJpaConfig} EntityScan is what keeps the v1 {@code outbox} /
 * {@code processed_events} tables required under {@code ddl-auto=validate} (see
 * {@code V1__init.sql} + {@code V2__artist_outbox_v2.sql}). The v1
 * {@code OutboxWriter} / {@code OutboxPublisher} beans it still registers are no
 * longer referenced.
 */
@Configuration
public class OutboxConfig {

    @Bean
    TransactionTemplate outboxTransactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
