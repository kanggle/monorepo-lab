package com.wms.inventory.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inventory.adapter.out.alert.InMemoryLowStockThresholdAdapter;
import com.wms.inventory.application.command.AdjustStockCommand;
import com.wms.inventory.application.command.AdjustStockCommand.AdjustOperation;
import com.wms.inventory.application.command.TransferStockCommand;
import com.wms.inventory.application.port.in.AdjustStockUseCase;
import com.wms.inventory.application.port.in.TransferStockUseCase;
import com.wms.inventory.application.port.out.LowStockThresholdPort;
import com.wms.inventory.domain.model.Bucket;
import com.wms.inventory.domain.model.ReasonCode;
import com.wms.inventory.domain.model.TransferReasonCode;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Full-path integration coverage for TASK-BE-024:
 * <ul>
 *   <li>POST {@code /adjustments} → {@code inventory.adjusted} on Kafka</li>
 *   <li>POST {@code /transfers} → {@code inventory.transferred} on Kafka</li>
 *   <li>Crossing the low-stock threshold → {@code inventory.low-stock-detected}</li>
 *   <li>Repeat mutation → no second alert (debounce active)</li>
 * </ul>
 */
class AdjustmentTransferIntegrationTest extends InventoryServiceIntegrationBase {

    private static final String TOPIC_ADJUSTED = "wms.inventory.adjusted.v1";
    private static final String TOPIC_TRANSFERRED = "wms.inventory.transferred.v1";
    private static final String TOPIC_ALERT = "wms.inventory.alert.v1";

    @Autowired private AdjustStockUseCase adjustStock;
    @Autowired private TransferStockUseCase transferStock;
    @Autowired private LowStockThresholdPort thresholdPort;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM inventory_outbox");
        jdbc.update("DELETE FROM stock_adjustment");
        jdbc.update("DELETE FROM stock_transfer");
        // TRUNCATE, not DELETE: inventory_movement has an append-only BEFORE DELETE
        // trigger (V5 W2) that rejects row DELETE; TRUNCATE does not fire row triggers.
        jdbc.update("TRUNCATE TABLE inventory_movement");
        jdbc.update("DELETE FROM inventory");
        jdbc.update("DELETE FROM location_snapshot");
        jdbc.update("DELETE FROM warehouse_snapshot");
        if (thresholdPort instanceof InMemoryLowStockThresholdAdapter adapter) {
            adapter.clearAll();
        }
    }

    @Test
    @DisplayName("POST /adjustments → inventory.adjusted on Kafka")
    void adjustmentEmitsInventoryAdjusted() throws Exception {
        UUID warehouse = UUID.randomUUID();
        UUID inventoryId = seedInventoryRow(warehouse, 100);

        adjustStock.adjust(new AdjustStockCommand(
                AdjustOperation.REGULAR, inventoryId, Bucket.AVAILABLE, -10,
                ReasonCode.ADJUSTMENT_LOSS, "lost in cycle count",
                "actor-1", UUID.randomUUID().toString()));

        try (KafkaConsumer<String, String> consumer = newConsumer(TOPIC_ADJUSTED)) {
            JsonNode envelope = pollOne(consumer, TOPIC_ADJUSTED, 30);
            assertThat(envelope).isNotNull();
            assertThat(envelope.get("eventType").asText()).isEqualTo("inventory.adjusted");
            JsonNode payload = envelope.get("payload");
            assertThat(payload.get("delta").asInt()).isEqualTo(-10);
            assertThat(payload.get("inventory").get("availableQty").asInt()).isEqualTo(90);
        }
    }

    @Test
    @DisplayName("POST /transfers → inventory.transferred on Kafka")
    void transferEmitsInventoryTransferred() throws Exception {
        UUID warehouse = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        UUID sku = UUID.randomUUID();
        // Realistic precondition: both locations are registered in the master
        // read-model (same warehouse) so TransferStockService.resolveSameWarehouse
        // can resolve the warehouse; inventory exists only for the source.
        seedLocationSnapshot(source, warehouse);
        seedLocationSnapshot(target, warehouse);
        seedInventoryRowAt(warehouse, source, sku, 100);

        transferStock.transfer(new TransferStockCommand(
                source, target, sku, null, 30,
                TransferReasonCode.TRANSFER_INTERNAL, "rebalance",
                "actor-1", UUID.randomUUID().toString()));

        try (KafkaConsumer<String, String> consumer = newConsumer(TOPIC_TRANSFERRED)) {
            JsonNode envelope = pollOne(consumer, TOPIC_TRANSFERRED, 30);
            assertThat(envelope).isNotNull();
            assertThat(envelope.get("eventType").asText()).isEqualTo("inventory.transferred");
            JsonNode payload = envelope.get("payload");
            assertThat(payload.get("quantity").asInt()).isEqualTo(30);
            assertThat(payload.get("source").get("availableQtyAfter").asInt()).isEqualTo(70);
            assertThat(payload.get("target").get("availableQtyAfter").asInt()).isEqualTo(30);
            assertThat(payload.get("target").get("wasCreated").asBoolean()).isTrue();
        }
    }

    @Test
    @DisplayName("threshold crossed → inventory.low-stock-detected fires once (debounced)")
    void lowStockAlertFiresOnceUnderDebounce() throws Exception {
        ((InMemoryLowStockThresholdAdapter) thresholdPort).setDefaultThreshold(20);

        UUID warehouse = UUID.randomUUID();
        UUID inventoryId = seedInventoryRow(warehouse, 25);

        adjustStock.adjust(new AdjustStockCommand(
                AdjustOperation.REGULAR, inventoryId, Bucket.AVAILABLE, -10,
                ReasonCode.ADJUSTMENT_LOSS, "first dip below threshold",
                "actor-1", UUID.randomUUID().toString()));
        adjustStock.adjust(new AdjustStockCommand(
                AdjustOperation.REGULAR, inventoryId, Bucket.AVAILABLE, -1,
                ReasonCode.ADJUSTMENT_LOSS, "second dip — debounced",
                "actor-1", UUID.randomUUID().toString()));

        await().atMost(30, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    Long alerts = jdbc.queryForObject(
                            "SELECT COUNT(*) FROM inventory_outbox WHERE event_type = ?",
                            Long.class, "inventory.low-stock-detected");
                    assertThat(alerts).isEqualTo(1L);
                });

        try (KafkaConsumer<String, String> consumer = newConsumer(TOPIC_ALERT)) {
            JsonNode envelope = pollOne(consumer, TOPIC_ALERT, 30);
            assertThat(envelope).isNotNull();
            assertThat(envelope.get("eventType").asText()).isEqualTo("inventory.low-stock-detected");
            JsonNode payload = envelope.get("payload");
            assertThat(payload.get("threshold").asInt()).isEqualTo(20);
            assertThat(payload.get("availableQty").asInt()).isEqualTo(15);
            assertThat(payload.get("triggeringEventType").asText()).isEqualTo("inventory.adjusted");
        }
    }

    /**
     * ADR-MONO-050 D9 / TASK-SCM-BE-037: the mutation events carry the warehouse CODE so
     * the cross-project scm batch replenishment leg can address a PO by code. Resolved from
     * the warehouse master read-model, exactly like the §7 alert path.
     */
    @Test
    @DisplayName("adjusted/transferred payloads carry warehouseCode when the master snapshot exists")
    void mutationEventsCarryWarehouseCode() throws Exception {
        UUID warehouse = UUID.randomUUID();
        seedWarehouseSnapshot(warehouse, "WH01");
        UUID inventoryId = seedInventoryRow(warehouse, 100);

        adjustStock.adjust(new AdjustStockCommand(
                AdjustOperation.REGULAR, inventoryId, Bucket.AVAILABLE, -10,
                ReasonCode.ADJUSTMENT_LOSS, "lost in cycle count",
                "actor-1", UUID.randomUUID().toString()));

        try (KafkaConsumer<String, String> consumer = newConsumer(TOPIC_ADJUSTED)) {
            JsonNode payload = pollOne(consumer, TOPIC_ADJUSTED, 30).get("payload");
            assertThat(payload.get("warehouseCode").asText()).isEqualTo("WH01");
        }

        UUID source = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        UUID sku = UUID.randomUUID();
        seedLocationSnapshot(source, warehouse);
        seedLocationSnapshot(target, warehouse);
        seedInventoryRowAt(warehouse, source, sku, 100);

        transferStock.transfer(new TransferStockCommand(
                source, target, sku, null, 30,
                TransferReasonCode.TRANSFER_INTERNAL, "rebalance",
                "actor-1", UUID.randomUUID().toString()));

        try (KafkaConsumer<String, String> consumer = newConsumer(TOPIC_TRANSFERRED)) {
            JsonNode payload = pollOne(consumer, TOPIC_TRANSFERRED, 30).get("payload");
            assertThat(payload.get("warehouseCode").asText()).isEqualTo("WH01");
        }
    }

    /**
     * The code is best-effort: with no warehouse snapshot (startup race) the payload must
     * still be emitted with a null code — a missing code never blocks the mutation.
     */
    @Test
    @DisplayName("adjusted payload warehouseCode is null (not absent, not fatal) without a snapshot")
    void adjustedEventWarehouseCodeNullWithoutSnapshot() throws Exception {
        UUID warehouse = UUID.randomUUID();  // deliberately NOT seeded into warehouse_snapshot
        UUID inventoryId = seedInventoryRow(warehouse, 100);

        adjustStock.adjust(new AdjustStockCommand(
                AdjustOperation.REGULAR, inventoryId, Bucket.AVAILABLE, -10,
                ReasonCode.ADJUSTMENT_LOSS, "lost in cycle count",
                "actor-1", UUID.randomUUID().toString()));

        try (KafkaConsumer<String, String> consumer = newConsumer(TOPIC_ADJUSTED)) {
            JsonNode payload = pollOne(consumer, TOPIC_ADJUSTED, 30).get("payload");
            assertThat(payload.has("warehouseCode")).isTrue();
            assertThat(payload.get("warehouseCode").isNull()).isTrue();
            // The mutation itself still applied.
            assertThat(payload.get("inventory").get("availableQty").asInt()).isEqualTo(90);
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

    private void seedLocationSnapshot(UUID locationId, UUID warehouseId) {
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO location_snapshot
                (id, location_code, warehouse_id, zone_id, location_type, status, cached_at, master_version)
                VALUES (?, ?, ?, ?, 'STORAGE', 'ACTIVE', ?, 1)
                """, locationId, "LOC-" + locationId.toString().substring(0, 8),
                warehouseId, UUID.randomUUID(), now);
    }

    private UUID seedInventoryRow(UUID warehouseId, int qty) {
        return seedInventoryRowAt(warehouseId, UUID.randomUUID(), UUID.randomUUID(), qty);
    }

    private UUID seedInventoryRowAt(UUID warehouseId, UUID locationId, UUID skuId, int qty) {
        UUID id = UUID.randomUUID();
        // OffsetDateTime (timestamptz) — pgjdbc cannot infer the SQL type of a raw Instant.
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO inventory
                (id, warehouse_id, location_id, sku_id, lot_id,
                 available_qty, reserved_qty, damaged_qty,
                 last_movement_at, version, created_at, created_by, updated_at, updated_by)
                VALUES (?, ?, ?, ?, NULL, ?, 0, 0, ?, 0, ?, 'test', ?, 'test')
                """, id, warehouseId, locationId, skuId, qty, now, now, now);
        return id;
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

    private JsonNode pollOne(KafkaConsumer<String, String> consumer, String topic,
                             long maxSeconds) throws Exception {
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
}
