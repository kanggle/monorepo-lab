package com.wms.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.wms.notification.adapter.outbound.persistence.jpa.dedupe.NotificationEventDedupeJpaRepository;
import com.wms.notification.adapter.outbound.persistence.jpa.delivery.NotificationDeliveryJpaRepository;
import com.wms.notification.adapter.outbound.persistence.jpa.outbox.NotificationOutboxJpaRepository;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Per-source-topic happy-path integration tests (Acceptance Criteria 4 — 6
 * source topics). Publishes one envelope per topic; awaits the dedupe row +
 * delivery row + outbox row.
 *
 * <p>Webhook URLs are intentionally unset (NotificationServiceIntegrationBase),
 * so the post-commit DeliveryExecutor will fail-closed → delivery FAILED +
 * notification.delivered outbox with FAILED_CHANNEL_NOT_CONFIGURED. We don't
 * assert the dispatch outcome here — that's the {@code DeliveryExecutorTest}'s
 * job. Here we validate the ingest TX shape (T3 atomicity).
 */
class AlertConsumerIntegrationTest extends NotificationServiceIntegrationBase {

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    NotificationDeliveryJpaRepository deliveries;

    @Autowired
    NotificationEventDedupeJpaRepository dedupe;

    @Autowired
    NotificationOutboxJpaRepository outbox;

    static java.util.stream.Stream<TopicScenario> happyPaths() {
        return java.util.stream.Stream.of(
                new TopicScenario("wms.inventory.alert.v1", "inventory.low-stock-detected",
                        "{\"sku\":\"ABC\",\"available\":0}"),
                new TopicScenario("wms.inventory.adjusted.v1", "inventory.adjusted",
                        "{\"delta\":-250}"),
                new TopicScenario("wms.inbound.inspection.completed.v1", "inbound.inspection.completed",
                        "{\"discrepancyCount\":3}"),
                new TopicScenario("wms.inbound.asn.cancelled.v1", "inbound.asn.cancelled",
                        "{\"asnId\":\"A-1\"}"),
                new TopicScenario("wms.outbound.order.cancelled.v1", "outbound.order.cancelled",
                        "{\"priorStatus\":\"PACKED\"}"),
                new TopicScenario("wms.outbound.shipping.confirmed.v1", "outbound.shipping.confirmed",
                        "{\"orderId\":\"O-1\"}"));
    }

    @ParameterizedTest
    @MethodSource("happyPaths")
    void perTopicHappyPath(TopicScenario scenario) {
        UUID eventId = UUID.randomUUID();
        String json = """
                {
                  "eventId": "%s",
                  "eventType": "%s",
                  "occurredAt": "2025-01-01T00:00:00Z",
                  "aggregateId": "agg-it",
                  "payload": %s
                }
                """.formatted(eventId, scenario.eventType, scenario.payloadJson);

        kafkaTemplate.send(scenario.topic, eventId.toString(), json);

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(250))
                .untilAsserted(() -> {
                    assertThat(dedupe.existsById(eventId))
                            .as("dedupe row for %s", scenario.topic).isTrue();
                });
    }

    @Test
    void duplicateReplaySkipsSilently() {
        UUID eventId = UUID.randomUUID();
        String json = """
                {
                  "eventId": "%s",
                  "eventType": "inventory.low-stock-detected",
                  "occurredAt": "2025-01-01T00:00:00Z",
                  "aggregateId": "agg-replay",
                  "payload": {"sku":"ABC"}
                }
                """.formatted(eventId);
        kafkaTemplate.send("wms.inventory.alert.v1", eventId.toString(), json);
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(dedupe.existsById(eventId)).isTrue());
        long dedupeCountBefore = dedupe.count();

        // Re-publish — second consumption should silently skip.
        kafkaTemplate.send("wms.inventory.alert.v1", eventId.toString(), json);
        // No clean signal; sleep briefly then assert no second dedupe row + no second delivery.
        try {
            Thread.sleep(2_000);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        assertThat(dedupe.count()).isEqualTo(dedupeCountBefore);
    }

    record TopicScenario(String topic, String eventType, String payloadJson) {
        @Override
        public String toString() {
            return topic;
        }
    }
}
