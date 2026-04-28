package com.wms.inventory.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inventory.application.command.ReserveStockCommand;
import com.wms.inventory.application.port.in.ReserveStockUseCase;
import com.wms.inventory.application.port.out.ReservationRepository;
import com.wms.inventory.application.result.ReservationView;
import com.wms.inventory.domain.model.Inventory;
import com.wms.inventory.domain.model.ReservationStatus;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * Full-path integration: outbound.picking.requested → ReserveStockService →
 * inventory.reserved on Kafka, plus the dedupe pickingRequestId lookup
 * scenario when the same picking request is re-delivered.
 */
class PickingFlowIntegrationTest extends InventoryServiceIntegrationBase {

    private static final String INBOUND_PICKING_REQUESTED = "wms.outbound.picking.requested.v1";
    private static final String OUTBOUND_RESERVED = "wms.inventory.reserved.v1";

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReserveStockUseCase reserveStock;

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
        jdbc.update("DELETE FROM reservation_line");
        jdbc.update("DELETE FROM reservation");
        jdbc.update("DELETE FROM inventory_movement");
        jdbc.update("DELETE FROM inventory");
        jdbc.update("DELETE FROM inventory_event_dedupe");
    }

    @Test
    @DisplayName("end-to-end: outbound.picking.requested → inventory.reserved on Kafka")
    void pickingRequestedProducesInventoryReserved() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        // First seed an inventory row by calling ReserveStockUseCase's prerequisite path:
        // we insert a row directly via SQL since BE-022's RECEIVE flow already covers
        // the put-away path. Using direct insertion keeps this test focused on picking.
        UUID inventoryId = seedInventoryRow(warehouseId, 100);
        UUID pickingRequestId = UUID.randomUUID();

        publish(INBOUND_PICKING_REQUESTED,
                buildPickingRequestedEvent(pickingRequestId, warehouseId, inventoryId, 30));

        await().atMost(45, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    var reservation = reservationRepository.findByPickingRequestId(pickingRequestId);
                    assertThat(reservation).isPresent();
                    assertThat(reservation.get().status()).isEqualTo(ReservationStatus.RESERVED);
                });

        try (KafkaConsumer<String, String> consumer = newConsumer(OUTBOUND_RESERVED)) {
            JsonNode envelope = pollOne(consumer, OUTBOUND_RESERVED, 30);
            assertThat(envelope).isNotNull();
            assertThat(envelope.get("eventType").asText()).isEqualTo("inventory.reserved");
            JsonNode payload = envelope.get("payload");
            assertThat(payload.get("pickingRequestId").asText()).isEqualTo(pickingRequestId.toString());
            JsonNode lines = payload.get("lines");
            assertThat(lines.size()).isEqualTo(1);
            assertThat(lines.get(0).get("availableQtyAfter").asInt()).isEqualTo(70);
            assertThat(lines.get(0).get("reservedQtyAfter").asInt()).isEqualTo(30);
        }
    }

    @Test
    @DisplayName("re-delivery with same pickingRequestId is a no-op")
    void redeliveryIsIdempotent() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        UUID inventoryId = seedInventoryRow(warehouseId, 100);
        UUID pickingRequestId = UUID.randomUUID();

        // Use the use-case directly twice — the second call must short-circuit
        // and return the cached reservation, not double-reserve.
        ReservationView first = reserveStock.reserve(new ReserveStockCommand(
                pickingRequestId, warehouseId,
                List.of(new ReserveStockCommand.Line(inventoryId, 30)),
                86400, null, "u", null));
        ReservationView second = reserveStock.reserve(new ReserveStockCommand(
                pickingRequestId, warehouseId,
                List.of(new ReserveStockCommand.Line(inventoryId, 30)),
                86400, null, "u", null));

        assertThat(second.id()).isEqualTo(first.id());
        Inventory inv = inventoryRepository_findById(inventoryId);
        assertThat(inv.availableQty()).isEqualTo(70);
        assertThat(inv.reservedQty()).isEqualTo(30);
    }

    @Autowired
    private com.wms.inventory.application.port.out.InventoryRepository inventoryRepository;

    private Inventory inventoryRepository_findById(UUID id) {
        return inventoryRepository.findById(id).orElseThrow();
    }

    private UUID seedInventoryRow(UUID warehouseId, int qty) {
        UUID id = UUID.randomUUID();
        java.time.Instant now = java.time.Instant.now();
        jdbc.update("""
                INSERT INTO inventory
                (id, warehouse_id, location_id, sku_id, lot_id,
                 available_qty, reserved_qty, damaged_qty,
                 last_movement_at, version, created_at, created_by, updated_at, updated_by)
                VALUES (?, ?, ?, ?, NULL, ?, 0, 0, ?, 0, ?, 'test', ?, 'test')
                """, id, warehouseId, UUID.randomUUID(), UUID.randomUUID(), qty, now, now, now);
        return id;
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
        consumer.subscribe(List.of(topic));
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

    private static String buildPickingRequestedEvent(UUID pickingRequestId, UUID warehouseId,
                                                     UUID inventoryId, int qty) {
        Map<String, Object> root = new HashMap<>();
        root.put("eventId", UUID.randomUUID().toString());
        root.put("eventType", "outbound.picking.requested");
        root.put("eventVersion", 1);
        root.put("occurredAt", "2026-04-25T10:00:00Z");
        root.put("producer", "outbound-service");
        root.put("aggregateType", "picking_request");
        root.put("aggregateId", pickingRequestId.toString());
        root.put("traceId", null);
        root.put("actorId", "outbound-saga");
        Map<String, Object> payload = new HashMap<>();
        payload.put("pickingRequestId", pickingRequestId.toString());
        payload.put("warehouseId", warehouseId.toString());
        payload.put("ttlSeconds", 86400);
        Map<String, Object> line = new HashMap<>();
        line.put("inventoryId", inventoryId.toString());
        line.put("quantity", qty);
        payload.put("lines", List.of(line));
        root.put("payload", payload);
        try {
            return new ObjectMapper().writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
