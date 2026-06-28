package com.example.messaging.outbox;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration entrypoint for the shared messaging persistence layer.
 *
 * <p>Imports {@link OutboxJpaConfig} (entity scan + repository activation for the
 * retained {@code ProcessedEventJpaEntity} inbox-dedup machinery). The historical
 * v1 outbox beans ({@code OutboxWriter}, {@code OutboxPublisher}) were removed once
 * every service migrated to the v2 {@code AbstractOutboxPublisher} + per-service
 * {@code OutboxRow} relay (TASK-MONO-312). The class itself is retained because
 * numerous services exclude it by name via
 * {@code @SpringBootApplication(exclude = OutboxAutoConfiguration.class)}.
 */
@AutoConfiguration
@Import(OutboxJpaConfig.class)
public class OutboxAutoConfiguration {
}
