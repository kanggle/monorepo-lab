package com.wms.inbound;

import com.example.messaging.outbox.OutboxAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * TASK-MONO-335 (same fix as inventory TASK-BE-432 / outbound TASK-BE-333):
 * exclude the shared {@link OutboxAutoConfiguration}. inbound-service supplies
 * its OWN outbox stack (the {@code AbstractOutboxPublisher}-based
 * {@code @Component OutboxPublisher} over {@code InboundOutboxJpaEntity}) and its
 * own dedupe ({@code inbound_event_dedupe}); it does not use the lib's shared
 * inbox. Left unexcluded, the lib's {@code OutboxJpaConfig} entity-scans
 * {@code ProcessedEventJpaEntity} (table {@code processed_events}), which inbound
 * has no migration for → {@code ddl-auto=validate} fails the context load; and
 * the lib's {@code @Bean outboxPublisher} clashes with inbound's own by bean
 * name. Never caught because no inbound full-context (Testcontainers) job ran in
 * CI — surfaced when MONO-335 restored + wired the inbound integration suite.
 */
@SpringBootApplication(exclude = OutboxAutoConfiguration.class)
@EnableScheduling
public class InboundServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InboundServiceApplication.class, args);
    }
}
