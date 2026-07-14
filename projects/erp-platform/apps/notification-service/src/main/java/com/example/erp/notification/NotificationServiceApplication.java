package com.example.erp.notification;

import com.example.messaging.outbox.OutboxMetricsAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * erp-platform notification-service entry point (TASK-ERP-BE-011).
 *
 * <p>The erp approval-notification fan-out, first increment. Consumes
 * {@code approval-service}'s four transition topics
 * ({@code erp.approval.{submitted,approved,rejected,withdrawn}.v1}), resolves
 * the recipient + renders a message, persists an in-app {@code Notification}
 * plus a {@code NotificationDelivery} (Category C structure — IN_APP delivery
 * commits synchronously {@code DELIVERED} in v1), and serves a recipient-scoped
 * read-only inbox via REST.
 *
 * <p>Service Type: {@code event-consumer} (primary) + {@code rest-api} (in-app
 * inbox read) — the same documented dual-type exception as read-model-service /
 * scm inventory-visibility-service. Architecture: Hexagonal (domain /
 * application / adapter / config).
 *
 * <p><b>Terminal consumer (E5-adjacent boundary)</b>: this service holds
 * notification logic (recipient resolution + message rendering) but no domain
 * business logic, owns no approval/master state machine, and never re-emits or
 * publishes any event. {@code rules/domains/erp.md} § Internal Event Catalog has
 * <b>no</b> {@code erp.notification.*} topic. It therefore runs <b>no
 * transactional outbox</b>: {@link OutboxMetricsAutoConfiguration} from
 * {@code libs/java-messaging} stays excluded (nothing here publishes, so a publish-failure
 * counter would be dead weight). Consumer idempotency (T8) lives entirely in this service's
 * own {@code processed_events} dedupe table.
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
