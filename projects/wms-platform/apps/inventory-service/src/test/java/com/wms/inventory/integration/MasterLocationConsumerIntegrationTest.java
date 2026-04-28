package com.wms.inventory.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.wms.inventory.application.port.out.MasterReadModelPort;
import com.wms.inventory.domain.model.masterref.LocationSnapshot;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end test: publish {@code wms.master.location.v1} events through a
 * real Kafka container, verify the consumer upserts the snapshot, dedupes
 * re-deliveries, and applies the version guard.
 */
class MasterLocationConsumerIntegrationTest extends InventoryServiceIntegrationBase {

    private static final String TOPIC = "wms.master.location.v1";

    @Autowired
    private MasterReadModelPort readModel;

    @Autowired
    private JdbcTemplate jdbc;

    private KafkaProducer<String, String> producer;

    @AfterEach
    void cleanup() {
        if (producer != null) {
            producer.close();
        }
        // Reset state between tests so version-guard scenarios start fresh.
        jdbc.update("DELETE FROM inventory_event_dedupe");
        jdbc.update("DELETE FROM location_snapshot");
    }

    @Test
    @DisplayName("created event upserts a new LocationSnapshot")
    void createdEventUpsertsSnapshot() throws Exception {
        UUID locationId = UUID.randomUUID();
        publish(buildLocationEvent("master.location.created", locationId, "ACTIVE", 0L));

        await().atMost(20, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(250)).untilAsserted(() -> {
            Optional<LocationSnapshot> snapshot = readModel.findLocation(locationId);
            assertThat(snapshot).isPresent();
            assertThat(snapshot.get().status()).isEqualTo(LocationSnapshot.Status.ACTIVE);
            assertThat(snapshot.get().masterVersion()).isEqualTo(0L);
        });
    }

    @Test
    @DisplayName("deactivated event flips status to INACTIVE")
    void deactivatedEventFlipsStatus() throws Exception {
        UUID locationId = UUID.randomUUID();
        publish(buildLocationEvent("master.location.created", locationId, "ACTIVE", 0L));
        await().atMost(20, TimeUnit.SECONDS).until(() -> readModel.findLocation(locationId).isPresent());

        publish(buildLocationEvent("master.location.deactivated", locationId, "INACTIVE", 1L));

        await().atMost(20, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(250)).untilAsserted(() -> {
            Optional<LocationSnapshot> snapshot = readModel.findLocation(locationId);
            assertThat(snapshot).isPresent();
            assertThat(snapshot.get().status()).isEqualTo(LocationSnapshot.Status.INACTIVE);
            assertThat(snapshot.get().masterVersion()).isEqualTo(1L);
        });
    }

    @Test
    @DisplayName("older master_version is dropped by version guard")
    void olderVersionIsDropped() throws Exception {
        UUID locationId = UUID.randomUUID();
        // First event: version=5
        publish(buildLocationEvent("master.location.updated", locationId, "ACTIVE", 5L));
        await().atMost(20, TimeUnit.SECONDS).until(() ->
                readModel.findLocation(locationId).map(s -> s.masterVersion() == 5L).orElse(false));

        // Out-of-order older event: version=3 — must be ignored (different eventId so dedupe doesn't apply)
        publish(buildLocationEvent("master.location.updated", locationId, "INACTIVE", 3L));

        // Wait long enough that the message has been processed, then assert state stayed.
        Thread.sleep(2_000);
        Optional<LocationSnapshot> snapshot = readModel.findLocation(locationId);
        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().masterVersion()).isEqualTo(5L);
        assertThat(snapshot.get().status()).isEqualTo(LocationSnapshot.Status.ACTIVE);
    }

    @Test
    @DisplayName("duplicate eventId is deduped at the database layer")
    void duplicateEventIdIsDeduped() throws Exception {
        UUID locationId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        // Publish the SAME event twice with same eventId.
        String payload = buildLocationEventWithId(eventId,
                "master.location.created", locationId, "ACTIVE", 0L);
        publish(payload);
        publish(payload);

        await().atMost(20, TimeUnit.SECONDS).until(() -> readModel.findLocation(locationId).isPresent());
        // Verify exactly one dedupe row exists for this eventId.
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_event_dedupe WHERE event_id = ?",
                Integer.class, eventId);
        assertThat(count).isEqualTo(1);
    }

    private void publish(String json) throws Exception {
        if (producer == null) {
            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            producer = new KafkaProducer<>(props);
        }
        producer.send(new ProducerRecord<>(TOPIC, "key", json)).get(10, TimeUnit.SECONDS);
        producer.flush();
    }

    private static String buildLocationEvent(String eventType, UUID locationId,
                                             String status, long version) {
        return buildLocationEventWithId(UUID.randomUUID(), eventType, locationId, status, version);
    }

    private static String buildLocationEventWithId(UUID eventId, String eventType,
                                                   UUID locationId, String status, long version) {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", eventType);
        envelope.put("eventVersion", 1);
        envelope.put("occurredAt", "2026-04-25T10:00:00Z");
        envelope.put("producer", "master-service");
        envelope.put("aggregateType", "location");
        envelope.put("aggregateId", locationId.toString());
        envelope.put("traceId", null);
        envelope.put("actorId", null);
        return """
                {
                  "eventId": "%s",
                  "eventType": "%s",
                  "eventVersion": 1,
                  "occurredAt": "2026-04-25T10:00:00Z",
                  "producer": "master-service",
                  "aggregateType": "location",
                  "aggregateId": "%s",
                  "traceId": null,
                  "actorId": null,
                  "payload": {
                    "location": {
                      "id": "%s",
                      "warehouseId": "%s",
                      "zoneId": "%s",
                      "locationCode": "WH01-A-01-01-01",
                      "aisle": "01",
                      "rack": "01",
                      "level": "01",
                      "bin": null,
                      "locationType": "STORAGE",
                      "capacityUnits": 500,
                      "status": "%s",
                      "version": %d,
                      "createdAt": "2026-04-18T00:00:00Z",
                      "createdBy": "seed-dev",
                      "updatedAt": "2026-04-25T10:00:00Z",
                      "updatedBy": "seed-dev"
                    }
                  }
                }
                """.formatted(eventId, eventType, locationId,
                locationId, UUID.randomUUID(), UUID.randomUUID(), status, version);
    }
}
