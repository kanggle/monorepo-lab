package com.wms.inbound;

import com.example.messaging.outbox.OutboxAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * TASK-BE-489: exclude the shared {@link OutboxAutoConfiguration} — the same fix
 * inventory-service (TASK-BE-432) and outbound-service (TASK-BE-333) applied.
 * The auto-config imports {@code OutboxJpaConfig}, which entity-scans the shared
 * {@code ProcessedEventJpaEntity} (table {@code processed_events}). inbound-service
 * has no such table — it runs its OWN outbox stack ({@code InboundOutboxJpaEntity}
 * + the {@code AbstractOutboxPublisher}-based {@code @Component OutboxPublisher})
 * and its own {@code inbound_event_dedupe}, using no libs auto-config bean. With
 * the entity scanned but no matching table, {@code ddl-auto=validate} (the IT
 * profile) fails context load. Excluding it removes the phantom entity; no
 * companion beans are needed (the v1 outbox beans were removed in TASK-MONO-312,
 * so the auto-config now contributes only the entity scan, and inbound already
 * supplies the {@code Clock} + {@code TransactionTemplate} its publisher needs).
 *
 * <p>Never caught before because no inbound full-context (Testcontainers) job ran
 * in CI and the slice/unit tests don't load the auto-config (TASK-MONO-335 → -489).
 */
@SpringBootApplication(exclude = OutboxAutoConfiguration.class)
@EnableScheduling
public class InboundServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InboundServiceApplication.class, args);
    }
}
