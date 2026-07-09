package com.wms.inventory.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inventory.application.port.out.LowStockThresholdPort;
import java.time.Duration;
import java.util.HashMap;
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
 * TASK-BE-459 — end-to-end: publish {@code admin.settings.changed} (low-stock
 * threshold key, GLOBAL) to {@code wms.admin.settings.v1} →
 * {@code AdminSettingsConsumer} reads it → the in-memory {@code LowStockThresholdPort}
 * default reflects the new value live (no redeploy). Proves the read/write ports
 * share one holder instance.
 */
class AdminSettingsConsumerIntegrationTest extends InventoryServiceIntegrationBase {

    private static final String SETTINGS_TOPIC = "wms.admin.settings.v1";
    private static final String LOW_STOCK_KEY = "inventory.low_stock.default_threshold_qty";

    @Autowired
    private LowStockThresholdPort thresholdPort;

    @Autowired
    private JdbcTemplate jdbc;

    private KafkaProducer<String, String> producer;

    @AfterEach
    void cleanup() {
        if (producer != null) {
            producer.close();
        }
        jdbc.update("DELETE FROM inventory_event_dedupe");
    }

    @Test
    @DisplayName("admin.settings.changed(low-stock, GLOBAL) → default threshold reflects new value live")
    void lowStockSettingChange_updatesThresholdLive() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        int newThreshold = 42;

        publish(SETTINGS_TOPIC, settingsChangedEvent(UUID.randomUUID(), LOW_STOCK_KEY, "GLOBAL", newThreshold));

        // The global default now resolves for any (warehouse, sku) with no specific override.
        await().atMost(45, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(500))
                .untilAsserted(() ->
                        assertThat(thresholdPort.findThreshold(warehouseId, skuId)).contains(newThreshold));
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

    private static String settingsChangedEvent(UUID eventId, String key, String scope, int value) {
        HashMap<String, Object> root = new HashMap<>();
        root.put("eventId", eventId.toString());
        root.put("eventType", "admin.settings.changed");
        root.put("eventVersion", 1);
        root.put("occurredAt", "2026-07-09T10:00:00.000Z");
        root.put("producer", "admin-service");
        root.put("aggregateType", "setting");
        root.put("aggregateId", key);
        root.put("traceId", null);
        root.put("actorId", "test-admin");

        HashMap<String, Object> payload = new HashMap<>();
        payload.put("key", key);
        payload.put("scope", scope);
        payload.put("warehouseId", null);
        payload.put("valueJson", value);
        payload.put("previousValueJson", 10);
        payload.put("version", 5);
        root.put("payload", payload);

        try {
            return new ObjectMapper().writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
