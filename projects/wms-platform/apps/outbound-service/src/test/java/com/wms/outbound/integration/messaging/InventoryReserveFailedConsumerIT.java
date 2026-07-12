package com.wms.outbound.integration.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.wms.outbound.integration.OutboundServiceIntegrationBase;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.utils.ContainerTestUtils;

/**
 * Testcontainers test for the BACKORDER return path (TASK-MONO-196,
 * ADR-MONO-022 §D4) — the negative-leg counterpart of
 * {@link FulfillmentRequestedConsumerIT}.
 *
 * <p>Sets up a real REQUESTED saga via the fulfillment path (so the
 * {@code pickingRequestId → saga} correlation is exercised end to end), then
 * publishes a synthetic {@code wms.inventory.reserve.failed.v1} and asserts
 * {@link com.wms.outbound.adapter.in.messaging.consumer.InventoryReserveFailedConsumer}
 * drives the order to {@code BACKORDERED} and emits {@code outbound.order.cancelled}
 * (reason=INSUFFICIENT_STOCK, carrying {@code orderNo}) — the cross-project
 * backorder signal the ecommerce side consumes.
 */
class InventoryReserveFailedConsumerIT extends OutboundServiceIntegrationBase {

    private static final String FULFILLMENT_TOPIC = "ecommerce.fulfillment.requested.v1";
    private static final String RESERVE_FAILED_TOPIC = "wms.inventory.reserve.failed.v1";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    private KafkaProducer<String, String> producer;

    @BeforeEach
    void seedMaster() {
        createTopic(FULFILLMENT_TOPIC);
        createTopic(RESERVE_FAILED_TOPIC);
        waitForAssignment(FULFILLMENT_TOPIC);
        waitForAssignment(RESERVE_FAILED_TOPIC);

        jdbc.execute("TRUNCATE TABLE outbound_outbox");
        jdbc.execute("TRUNCATE TABLE outbound_event_dedupe");

        jdbc.update("""
                INSERT INTO partner_snapshot
                    (id, partner_code, partner_type, status, cached_at, master_version)
                VALUES (?, 'ECOMMERCE-STORE', 'CUSTOMER', 'ACTIVE', now(), 1)
                """, UUID.randomUUID());
        jdbc.update("""
                INSERT INTO warehouse_snapshot
                    (id, warehouse_code, status, cached_at, master_version)
                VALUES (?, 'WH-MAIN', 'ACTIVE', now(), 1)
                """, UUID.randomUUID());
        jdbc.update("""
                INSERT INTO sku_snapshot
                    (id, sku_code, tracking_type, status, cached_at, master_version)
                VALUES (?, 'SKU-APPLE-001', 'NONE', 'ACTIVE', now(), 1)
                """, UUID.randomUUID());
    }

    @AfterEach
    void cleanup() {
        if (producer != null) {
            producer.close();
        }
        jdbc.execute("TRUNCATE TABLE outbound_outbox");
        jdbc.execute("TRUNCATE TABLE outbound_event_dedupe");
        jdbc.update("DELETE FROM outbound_order_line");
        jdbc.update("DELETE FROM outbound_saga");
        jdbc.update("DELETE FROM outbound_order");
        jdbc.update("DELETE FROM partner_snapshot");
        jdbc.update("DELETE FROM warehouse_snapshot");
        jdbc.update("DELETE FROM sku_snapshot");
    }

    @Test
    @DisplayName("inventory.reserve.failed → order BACKORDERED + outbound.order.cancelled(reason, orderNo) emitted")
    void reserveFailedBackordersOrderAndEmitsCancel() throws Exception {
        // ----- arrange: create a real order + REQUESTED saga via fulfillment ---
        String orderNo = "ECO-BO-" + System.currentTimeMillis();
        publish(FULFILLMENT_TOPIC, buildFulfillmentEvent(UUID.randomUUID(), orderNo));

        UUID[] ids = new UUID[2]; // [orderId, pickingRequestId]
        await().atMost(30, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT id FROM outbound_order WHERE order_no = ?", orderNo);
            assertThat(rows).hasSize(1);
            UUID orderId = (UUID) rows.get(0).get("id");
            ids[0] = orderId;
            ids[1] = jdbc.queryForObject(
                    "SELECT picking_request_id FROM outbound_saga WHERE order_id = ?",
                    UUID.class, orderId);
            assertThat(ids[1]).isNotNull();
        });
        UUID orderId = ids[0];
        UUID pickingRequestId = ids[1];

        // clear the fulfillment-path outbox rows so the cancel assertion is clean
        jdbc.execute("TRUNCATE TABLE outbound_outbox");

        // ----- act: publish the inventory reservation-shortfall signal --------
        publish(RESERVE_FAILED_TOPIC, buildReserveFailedEvent(UUID.randomUUID(), pickingRequestId));

        // ----- assert: order BACKORDERED + cross-project cancel emitted -------
        await().atMost(30, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            String status = jdbc.queryForObject(
                    "SELECT status FROM outbound_order WHERE id = ?", String.class, orderId);
            assertThat(status).isEqualTo("BACKORDERED");

            // The cross-project cancel carries the backorder reason + orderNo.
            Long cancelled = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM outbound_outbox
                    WHERE event_type = 'outbound.order.cancelled'
                      AND aggregate_id = ?
                    """, Long.class, orderId);
            assertThat(cancelled).as("outbound.order.cancelled emitted for the backordered order").isEqualTo(1L);

            String payload = jdbc.queryForObject("""
                    SELECT payload::text FROM outbound_outbox
                    WHERE event_type = 'outbound.order.cancelled' AND aggregate_id = ?
                    """, String.class, orderId);
            assertThat(payload).contains("INSUFFICIENT_STOCK").contains(orderNo);
        });
    }

    // ------------------------------------------------------------------
    // helpers (mirror FulfillmentRequestedConsumerIT)
    // ------------------------------------------------------------------

    private void createTopic(String topic) {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        try (AdminClient admin = AdminClient.create(props)) {
            admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1))).all().get(20, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (!(e.getCause() instanceof TopicExistsException)) {
                throw new IllegalStateException("Failed to create topic " + topic, e);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted creating topic " + topic, e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create topic " + topic, e);
        }
    }

    /**
     * Starts the one listener subscribed to {@code topic} and blocks until it holds its
     * partition. Only the containers this test actually needs are started (TASK-MONO-376) —
     * the other listeners share the same consumer group, and every one of them that joins
     * revokes the assignment of the container under test.
     */
    private void waitForAssignment(String topic) {
        startAndAwaitListener(listenerRegistry, topic);
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

    private static String buildFulfillmentEvent(UUID eventId, String orderNo) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "ecommerce.fulfillment.requested",
                  "eventVersion": 1,
                  "occurredAt": "2026-06-08T10:00:00.000Z",
                  "producer": "ecommerce-platform",
                  "aggregateType": "fulfillment",
                  "aggregateId": "%s",
                  "traceId": null,
                  "actorId": "system:ecommerce",
                  "payload": {
                    "orderNo": "%s",
                    "customerPartnerCode": "ECOMMERCE-STORE",
                    "warehouseCode": "WH-MAIN",
                    "requiredShipDate": null,
                    "shipTo": {
                      "recipientName": "홍길동",
                      "address": "서울시 강남구 1",
                      "phone": "010-1234-5678"
                    },
                    "lines": [
                      { "lineNo": 1, "skuCode": "SKU-APPLE-001", "lotNo": null, "qtyOrdered": 2 }
                    ]
                  }
                }
                """.formatted(eventId, UUID.randomUUID(), orderNo);
    }

    private static String buildReserveFailedEvent(UUID eventId, UUID pickingRequestId) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "inventory.reserve.failed",
                  "eventVersion": 1,
                  "occurredAt": "2026-06-08T10:05:00.000Z",
                  "producer": "inventory-service",
                  "aggregateType": "reservation",
                  "aggregateId": "%s",
                  "traceId": null,
                  "actorId": "system:picking-requested-consumer",
                  "payload": {
                    "pickingRequestId": "%s",
                    "reason": "INSUFFICIENT_STOCK",
                    "insufficientLines": [
                      {
                        "inventoryId": "%s",
                        "skuId": "%s",
                        "lotId": null,
                        "locationId": "%s",
                        "qtyRequested": 2,
                        "qtyAvailable": 0
                      }
                    ]
                  }
                }
                """.formatted(eventId, pickingRequestId, pickingRequestId,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }
}
