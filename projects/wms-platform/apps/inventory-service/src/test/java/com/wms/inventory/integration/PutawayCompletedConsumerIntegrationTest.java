package com.wms.inventory.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inventory.application.port.out.InventoryRepository;
import com.wms.inventory.domain.model.Inventory;
import java.time.Duration;
import java.util.HashMap;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Full path: publish {@code inbound.putaway.completed} to Kafka →
 * {@code PutawayCompletedConsumer} reads it → {@code ReceiveStockService}
 * upserts the Inventory row + writes Movement + outbox row → outbox publisher
 * forwards the {@code inventory.received} event to
 * {@code wms.inventory.received.v1}.
 */
class PutawayCompletedConsumerIntegrationTest extends InventoryServiceIntegrationBase {

    private static final String INBOUND_TOPIC = "wms.inbound.putaway.completed.v1";
    private static final String OUTBOUND_TOPIC = "wms.inventory.received.v1";

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private KafkaProducer<String, String> producer;

    @AfterEach
    void cleanup() {
        if (producer != null) {
            producer.close();
        }
        jdbc.update("DELETE FROM inventory_outbox");
        // TRUNCATE, not DELETE: inventory_movement has an append-only BEFORE DELETE
        // trigger (V5 W2) that rejects row DELETE; TRUNCATE does not fire row triggers.
        jdbc.update("TRUNCATE TABLE inventory_movement");
        jdbc.update("DELETE FROM inventory");
        jdbc.update("DELETE FROM inventory_event_dedupe");
        jdbc.update("DELETE FROM warehouse_snapshot");
    }

    /**
     * ADR-MONO-050 D9 / TASK-SCM-BE-037: {@code inventory.received} carries the warehouse
     * CODE resolved from the warehouse master read-model, so the cross-project scm batch
     * replenishment leg can address a PO by code rather than uuid.
     */
    @Test
    @DisplayName("putaway → inventory.received payload carries warehouseCode")
    void receivedEventCarriesWarehouseCode() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        seedWarehouseSnapshot(warehouseId, "WH01");

        publish(INBOUND_TOPIC, buildPutawayEvent(UUID.randomUUID(), warehouseId,
                UUID.randomUUID(), locationId, skuId, null, 50));

        await().atMost(45, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> assertThat(
                        inventoryRepository.findByKey(locationId, skuId, null)).isPresent());

        try (KafkaConsumer<String, String> consumer = newConsumer(OUTBOUND_TOPIC)) {
            // Match THIS test's warehouse — the topic also replays earlier tests' events.
            JsonNode envelope = pollMatching(consumer, OUTBOUND_TOPIC,
                    p -> warehouseId.toString().equals(p.path("warehouseId").asText()), 30);
            assertThat(envelope).as("inventory.received for warehouse %s", warehouseId).isNotNull();
            assertThat(envelope.get("payload").get("warehouseCode").asText()).isEqualTo("WH01");
        }
    }

    /**
     * Best-effort resolution: with no warehouse snapshot the event must still be emitted
     * with a null code — a missing code never blocks the receive.
     */
    @Test
    @DisplayName("putaway without a warehouse snapshot → warehouseCode null, event still emitted")
    void receivedEventWarehouseCodeNullWithoutSnapshot() throws Exception {
        UUID warehouseId = UUID.randomUUID();  // deliberately NOT seeded
        UUID locationId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();

        publish(INBOUND_TOPIC, buildPutawayEvent(UUID.randomUUID(), warehouseId,
                UUID.randomUUID(), locationId, skuId, null, 50));

        await().atMost(45, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> assertThat(
                        inventoryRepository.findByKey(locationId, skuId, null)).isPresent());

        try (KafkaConsumer<String, String> consumer = newConsumer(OUTBOUND_TOPIC)) {
            // Match THIS test's warehouse — otherwise the first replayed event (from another
            // test) could satisfy "code is null" and green this assertion for the wrong reason.
            JsonNode envelope = pollMatching(consumer, OUTBOUND_TOPIC,
                    p -> warehouseId.toString().equals(p.path("warehouseId").asText()), 30);
            assertThat(envelope).as("inventory.received for warehouse %s", warehouseId).isNotNull();
            JsonNode payload = envelope.get("payload");
            assertThat(payload.has("warehouseCode")).isTrue();
            assertThat(payload.get("warehouseCode").isNull()).isTrue();
            assertThat(payload.get("lines").get(0).get("availableQtyAfter").asInt()).isEqualTo(50);
        }
    }

    private void seedWarehouseSnapshot(UUID warehouseId, String warehouseCode) {
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO warehouse_snapshot
                (id, warehouse_code, status, cached_at, master_version)
                VALUES (?, ?, 'ACTIVE', ?, 1)
                """, warehouseId, warehouseCode, now);
    }

    @Test
    @DisplayName("end-to-end: putaway → inventory.received on Kafka")
    void putawayProducesInventoryReceivedEvent() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        UUID asnId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        publish(INBOUND_TOPIC, buildPutawayEvent(eventId, warehouseId, asnId,
                locationId, skuId, null, 50));

        await().atMost(45, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    Optional<Inventory> row = inventoryRepository.findByKey(locationId, skuId, null);
                    assertThat(row).isPresent();
                    assertThat(row.get().availableQty()).isEqualTo(50);
                });

        try (KafkaConsumer<String, String> consumer = newConsumer(OUTBOUND_TOPIC)) {
            JsonNode envelope = pollOne(consumer, OUTBOUND_TOPIC, 30);
            assertThat(envelope).isNotNull();
            assertThat(envelope.get("eventType").asText()).isEqualTo("inventory.received");
            assertThat(envelope.get("aggregateType").asText()).isEqualTo("inventory");
            JsonNode payload = envelope.get("payload");
            assertThat(payload.get("warehouseId").asText()).isEqualTo(warehouseId.toString());
            assertThat(payload.get("sourceEventId").asText()).isEqualTo(eventId.toString());
            JsonNode lines = payload.get("lines");
            assertThat(lines.size()).isEqualTo(1);
            assertThat(lines.get(0).get("availableQtyAfter").asInt()).isEqualTo(50);
        }
    }

    @Test
    @DisplayName("re-delivery with same eventId is deduped")
    void redeliveryIsDeduped() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        UUID asnId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        String payload = buildPutawayEvent(eventId, warehouseId, asnId, locationId, skuId, null, 50);
        publish(INBOUND_TOPIC, payload);

        // Wait until the first delivery is fully applied before redelivering, so the
        // redelivery cannot race the initial application (both would otherwise be in
        // flight and the dedupe insert-then-flush guard would not yet be committed).
        await().atMost(45, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    Optional<Inventory> applied = inventoryRepository.findByKey(locationId, skuId, null);
                    assertThat(applied).isPresent();
                    assertThat(applied.get().availableQty()).isEqualTo(50);
                });

        // Redeliver the identical event, then publish a sentinel with a fresh eventId.
        // All records share the producer key "key" → same partition → strict ordering,
        // so once the sentinel is applied the redelivery is guaranteed to have been
        // consumed (and, if dedupe works, skipped).
        publish(INBOUND_TOPIC, payload);
        UUID sentinelEventId = UUID.randomUUID();
        UUID sentinelLocationId = UUID.randomUUID();
        UUID sentinelSkuId = UUID.randomUUID();
        publish(INBOUND_TOPIC, buildPutawayEvent(sentinelEventId, warehouseId, asnId,
                sentinelLocationId, sentinelSkuId, null, 10));

        await().atMost(45, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    Optional<Inventory> sentinel =
                            inventoryRepository.findByKey(sentinelLocationId, sentinelSkuId, null);
                    assertThat(sentinel).isPresent();
                    assertThat(sentinel.get().availableQty()).isEqualTo(10);
                });

        // The redelivery was skipped by EventDedupe: qty unchanged, single dedupe row.
        Optional<Inventory> row = inventoryRepository.findByKey(locationId, skuId, null);
        assertThat(row).isPresent();
        assertThat(row.get().availableQty()).isEqualTo(50);
        Integer dedupeCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_event_dedupe WHERE event_id = ?",
                Integer.class, eventId);
        assertThat(dedupeCount).isEqualTo(1);
    }

    private void publish(String topic, String json) throws Exception {
        if (producer == null) {
            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            producer = new KafkaProducer<>(props);
        }
        producer.send(new ProducerRecord<>(topic, "key", json)).get(10, TimeUnit.SECONDS);
        producer.flush();
    }

    private KafkaConsumer<String, String> newConsumer(String topic) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(java.util.List.of(topic));
        return consumer;
    }

    private JsonNode pollOne(KafkaConsumer<String, String> consumer, String topic, long maxSeconds) throws Exception {
        long deadline = System.currentTimeMillis() + maxSeconds * 1_000L;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> r : records) {
                if (topic.equals(r.topic())) {
                    return objectMapper.readTree(r.value());
                }
            }
        }
        return null;
    }

    /**
     * Like {@link #pollOne} but returns the first envelope whose payload satisfies
     * {@code match}, instead of the first envelope on the topic.
     *
     * <p>The consumers here use a fresh group with {@code auto.offset.reset=earliest}, so a
     * poll replays the topic from the beginning — including events published by EARLIER tests
     * in this class. "First record on the topic" is therefore not "the event this test just
     * caused"; asserting on it makes a test pass or fail based on execution order. Match on
     * an id this test generated instead.
     */
    private JsonNode pollMatching(KafkaConsumer<String, String> consumer, String topic,
                                  java.util.function.Predicate<JsonNode> match,
                                  long maxSeconds) throws Exception {
        long deadline = System.currentTimeMillis() + maxSeconds * 1_000L;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> r : records) {
                if (!topic.equals(r.topic())) {
                    continue;
                }
                JsonNode envelope = objectMapper.readTree(r.value());
                JsonNode payload = envelope.get("payload");
                if (payload != null && match.test(payload)) {
                    return envelope;
                }
            }
        }
        return null;
    }

    private static String buildPutawayEvent(UUID eventId, UUID warehouseId, UUID asnId,
                                            UUID locationId, UUID skuId, UUID lotId,
                                            int qty) {
        HashMap<String, Object> root = new HashMap<>();
        root.put("eventId", eventId.toString());
        root.put("eventType", "inbound.putaway.completed");
        root.put("eventVersion", 1);
        root.put("occurredAt", "2026-04-25T10:00:00Z");
        root.put("producer", "inbound-service");
        root.put("aggregateType", "putaway");
        root.put("aggregateId", asnId.toString());
        root.put("traceId", null);
        root.put("actorId", "test-operator");

        HashMap<String, Object> payload = new HashMap<>();
        payload.put("asnId", asnId.toString());
        payload.put("warehouseId", warehouseId.toString());
        HashMap<String, Object> line = new HashMap<>();
        line.put("skuId", skuId.toString());
        line.put("lotId", lotId == null ? null : lotId.toString());
        line.put("locationId", locationId.toString());
        line.put("qtyReceived", qty);
        payload.put("lines", java.util.List.of(line));
        root.put("payload", payload);

        try {
            return new ObjectMapper().writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
