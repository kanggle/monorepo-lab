package com.example.fanplatform.notification;

import com.example.messaging.outbox.OutboxAutoConfiguration;
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
 * therefore runs <b>no transactional outbox</b>:
 * {@link OutboxAutoConfiguration} (and its metrics companion) from
 * {@code libs/java-messaging} are excluded so no {@code outbox} schema is
 * required and no {@code OutboxWriter}/{@code OutboxPublisher} bean is created
 * (those require an {@code outbox} table). Consumer idempotency lives entirely in
 * this service's own {@code processed_events} dedupe table (libs:java-messaging
 * {@code processed_events} shape; feedback §13). The only Kafka <i>producer</i>
 * use is the DLQ recoverer ({@code KafkaConsumerConfig}) writing to
 * {@code <topic>.dlq} — infrastructure error-handling, not a domain event.
 */
@SpringBootApplication(exclude = {
        OutboxAutoConfiguration.class,
        OutboxMetricsAutoConfiguration.class
})
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
