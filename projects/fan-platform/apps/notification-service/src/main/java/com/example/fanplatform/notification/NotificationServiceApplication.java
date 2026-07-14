package com.example.fanplatform.notification;

import com.example.messaging.outbox.OutboxMetricsAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * fan-platform notification-service entry point (TASK-FAN-BE-013).
 *
 * <p>Closes the membership lifecycle loop: membership-service emits
 * {@code fan.membership.activated.v1} / {@code fan.membership.canceled.v1}
 * (outbox, FAN-BE-009) with no consumer; this service subscribes to both,
 * records a per-fan {@code Notification} idempotently, fans out via deterministic
 * mock channels (logging email/push), and serves a recipient-scoped read-only
 * inbox via REST.
 *
 * <p>Service Type: {@code event-consumer} (primary) + {@code rest-api} (in-app
 * inbox read) — the documented dual-type exception (event-consumer.md § Allowed
 * Patterns; same as erp notification-service / read-model-service). Architecture:
 * Layered (presentation / application / domain / infrastructure), matching the
 * sibling membership / community / artist services.
 *
 * <p><b>Terminal consumer</b>: this service holds notification logic only — it
 * owns no domain state machine and never re-emits or publishes any event. It
 * therefore runs <b>no transactional outbox</b>: {@link OutboxMetricsAutoConfiguration}
 * from {@code libs/java-messaging} stays excluded (nothing here publishes, so a
 * publish-failure counter would be dead weight). Consumer idempotency lives entirely in
 * this service's own {@code processed_events} dedupe table. The only Kafka
 * <i>producer</i> use is the DLQ recoverer ({@code KafkaConsumerConfig}) writing to
 * {@code <topic>.dlq} — infrastructure error-handling, not a domain event.
 *
 * <p>The companion {@code exclude = OutboxAutoConfiguration.class} was dropped by
 * TASK-MONO-406: that auto-config's only remaining payload was an {@code @EntityScan} +
 * {@code @EnableJpaRepositories} registering the library's own {@code ProcessedEvent}
 * entity/repository fleet-wide, whose bean name collided with this service's. ADR-MONO-004
 * already placed per-service dedupe entities outside the shared library, so MONO-406 deleted
 * them there — leaving nothing to exclude.
 */
@SpringBootApplication(exclude = OutboxMetricsAutoConfiguration.class)
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
