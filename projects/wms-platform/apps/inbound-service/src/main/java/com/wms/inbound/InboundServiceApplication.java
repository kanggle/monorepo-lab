package com.wms.inbound;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * inbound-service runs its OWN outbox stack ({@code InboundOutboxJpaEntity} + the
 * {@code AbstractOutboxPublisher}-based {@code @Component OutboxPublisher}) and its own
 * {@code inbound_event_dedupe} table, using no bean from a libs auto-config.
 *
 * <p><b>Formerly excluded {@code OutboxAutoConfiguration} (TASK-BE-489) — removed by
 * TASK-MONO-406.</b> The auto-config imported {@code OutboxJpaConfig}, which entity-scanned
 * the lib's {@code ProcessedEventJpaEntity} (table {@code processed_events}) into every
 * consumer's context. inbound-service has no such table, so under {@code ddl-auto=validate}
 * that phantom entity alone failed context load. MONO-406 deleted the entity and the
 * auto-config, so there is nothing left to exclude.
 *
 * <p>This was the fourth boot failure caused by that one auto-config: TASK-BE-333
 * (outbound-service) and TASK-BE-432 (inventory-service) hit the v1 {@code outboxPublisher}
 * bean-name collision (those beans were later deleted by TASK-MONO-312), while TASK-BE-489
 * (here) and TASK-BE-461 (ecommerce settlement-service) hit the {@code ProcessedEvent}
 * registration. Each was fixed service-locally with the same exclude; MONO-406 fixed the
 * source.
 *
 * <p>None of the four was caught by the compiler or by unit tests — the slice/unit tests
 * never load auto-configs, and no inbound full-context (Testcontainers) job ran in CI at the
 * time (TASK-MONO-335 → -489). Only a booting context can observe this defect class.
 */
@SpringBootApplication
@EnableScheduling
public class InboundServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InboundServiceApplication.class, args);
    }
}
