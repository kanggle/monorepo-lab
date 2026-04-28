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
        jdbc.update("DELETE FROM inventory_movement");
        jdbc.update("DELETE FROM inventory");
        jdbc.update("DELETE FROM inventory_event_dedupe");
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
        publish(INBOUND_TOPIC, payload);

        // Wait for the second message to be processed before asserting.
        await().atMost(45, TimeUnit.SECONDS).until(() -> {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM inventory_event_dedupe WHERE event_id = ?",
                    Integer.class, eventId);
            return count != null && count == 1;
        });

        Optional<Inventory> row = inventoryRepository.findByKey(locationId, skuId, null);
        assertThat(row).isPresent();
        // Single application — second attempt was skipped by EventDedupe.
        assertThat(row.get().availableQty()).isEqualTo(50);
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
