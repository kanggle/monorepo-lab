package com.example.erp.readmodel;

import com.example.messaging.outbox.OutboxMetricsAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * erp-platform read-model-service entry point (TASK-ERP-BE-007).
 *
 * <p>The erp integrated read model, first increment. Consumes
 * {@code masterdata-service}'s four master-change topics
 * ({@code erp.masterdata.{department,employee,jobgrade,costcenter}.changed.v1})
 * and maintains denormalized MySQL projection tables, then serves a read-only
 * <em>employee org-view</em> (employee × resolved department-hierarchy path ×
 * cost center × job grade) via REST.
 *
 * <p>Service Type: {@code rest-api} + {@code event-consumer} (CQRS read-model —
 * same documented dual-type exception as scm inventory-visibility-service).
 * Architecture: Hexagonal (domain / application / adapter / config).
 *
 * <p><b>E5 read-only boundary</b>: this service holds no domain business logic,
 * owns no aggregate state machine, and never re-emits or writes back
 * authoritative master facts. It therefore runs <b>no transactional outbox</b>:
 * {@link OutboxMetricsAutoConfiguration} from {@code libs/java-messaging} stays excluded
 * (nothing here publishes, so a publish-failure counter would be dead weight). Consumer
 * idempotency (T8) lives entirely in this service's own {@code processed_events} dedupe
 * table.
 *
 * <p>The companion {@code exclude = OutboxAutoConfiguration.class} was dropped by
 * TASK-MONO-406: that auto-config's only remaining payload was an {@code @EntityScan} +
 * {@code @EnableJpaRepositories} registering the library's own {@code ProcessedEvent}
 * entity/repository fleet-wide, whose bean name collided with this service's. ADR-MONO-004
 * already placed per-service dedupe entities outside the shared library, so MONO-406 deleted
 * them there — leaving nothing to exclude.
 */
@SpringBootApplication(exclude = OutboxMetricsAutoConfiguration.class)
public class ReadModelServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReadModelServiceApplication.class, args);
    }
}
