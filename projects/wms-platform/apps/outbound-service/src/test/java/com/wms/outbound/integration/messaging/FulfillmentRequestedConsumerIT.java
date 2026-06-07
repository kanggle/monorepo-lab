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
 * Testcontainers end-to-end test for {@link com.wms.outbound.adapter.in.messaging.consumer.FulfillmentRequestedConsumer}
 * (TASK-BE-340, ADR-MONO-022 D1).
 *
 * <p>Publishes a {@code ecommerce.fulfillment.requested.v1} event through a real
 * Kafka container and asserts the consumer resolves codes→uuids, creates the
 * outbound order with {@code source=FULFILLMENT_ECOMMERCE} + the additive
 * {@code ship_to_*} columns, and writes the {@code outbound.order.received}
 * (with {@code shipTo}) and {@code outbound.picking.requested} outbox rows
 * in the same TX.
 *
 * <p>Master snapshots ({@code ECOMMERCE-STORE} partner, default warehouse,
 * mapped SKU — all ACTIVE) are seeded directly via JDBC, mirroring what the
 * {@code master.*} consumers would have populated.
 */
class FulfillmentRequestedConsumerIT extends OutboundServiceIntegrationBase {

    private static final String TOPIC = "ecommerce.fulfillment.requested.v1";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    private KafkaProducer<String, String> producer;

    private UUID partnerId;
    private UUID warehouseId;
    private UUID skuId;

    @BeforeEach
    void seedMaster() {
        // The topic does not exist at context startup (the @KafkaListener
        // subscribes by name). A consumer only discovers a freshly-created
        // topic on a metadata refresh (default metadata.max.age.ms = 5min),
        // so if the producer auto-creates the topic on first send the listener
        // would not be assigned within the test's 30s await → order never
        // created. Pre-create the topic via AdminClient, then wait until every
        // listener container is actually assigned a partition before producing.
        createTopic();
        waitForListenerAssignment();

        partnerId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        skuId = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO partner_snapshot
                    (id, partner_code, partner_type, status, cached_at, master_version)
                VALUES (?, 'ECOMMERCE-STORE', 'CUSTOMER', 'ACTIVE', now(), 1)
                """, partnerId);
        jdbc.update("""
                INSERT INTO warehouse_snapshot
                    (id, warehouse_code, status, cached_at, master_version)
                VALUES (?, 'WH-MAIN', 'ACTIVE', now(), 1)
                """, warehouseId);
        jdbc.update("""
                INSERT INTO sku_snapshot
                    (id, sku_code, tracking_type, status, cached_at, master_version)
                VALUES (?, 'SKU-APPLE-001', 'NONE', 'ACTIVE', now(), 1)
                """, skuId);
    }

    @AfterEach
    void cleanup() {
        if (producer != null) {
            producer.close();
        }
        // outbound_outbox + outbound_event_dedupe are append-only (V8 BEFORE
        // DELETE triggers) — TRUNCATE bypasses the row-level triggers; DELETE
        // is rejected.
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
    @DisplayName("fulfillment event creates a FULFILLMENT_ECOMMERCE order with shipTo + order.received/picking.requested outbox rows")
    void fulfillmentEventCreatesOrderWithShipTo() throws Exception {
        String orderNo = "ECO-" + System.currentTimeMillis();
        publish(buildFulfillmentEvent(UUID.randomUUID(), orderNo));

        await().atMost(30, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT * FROM outbound_order WHERE order_no = ?", orderNo);
            assertThat(rows).hasSize(1);
            Map<String, Object> row = rows.get(0);
            assertThat(row.get("source")).isEqualTo("FULFILLMENT_ECOMMERCE");
            assertThat(row.get("ship_to_name")).isEqualTo("홍길동");
            assertThat(row.get("ship_to_address")).isEqualTo("서울시 강남구 1");
            assertThat(row.get("ship_to_phone")).isEqualTo("010-1234-5678");
        });

        // order.received outbox row carries the shipTo block.
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Long received = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM outbound_outbox
                    WHERE event_type = 'outbound.order.received'
                      AND payload::text LIKE ?
                    """, Long.class, "%\"orderNo\":\"" + orderNo + "\"%");
            assertThat(received).isEqualTo(1L);

            Long shipTo = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM outbound_outbox
                    WHERE event_type = 'outbound.order.received'
                      AND payload::text LIKE '%"recipientName":"홍길동"%'
                    """, Long.class);
            assertThat(shipTo).isEqualTo(1L);

            Long pickingRequested = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM outbound_outbox
                    WHERE event_type = 'outbound.picking.requested'
                    """, Long.class);
            assertThat(pickingRequested).isEqualTo(1L);
        });
    }

    /**
     * Pre-creates the fulfillment topic so the listener can be assigned its
     * partition deterministically (rather than waiting up to
     * {@code metadata.max.age.ms} for producer auto-creation to surface).
     * Idempotent — a {@link TopicExistsException} from a prior test class is
     * swallowed.
     */
    private void createTopic() {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        try (AdminClient admin = AdminClient.create(props)) {
            admin.createTopics(List.of(new NewTopic(TOPIC, 1, (short) 1)))
                    .all().get(20, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (!(e.getCause() instanceof TopicExistsException)) {
                throw new IllegalStateException("Failed to create topic " + TOPIC, e);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted creating topic " + TOPIC, e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create topic " + TOPIC, e);
        }
    }

    /**
     * Blocks until the fulfillment {@code @KafkaListener} container has been
     * assigned its partition, so a subsequently-produced record is guaranteed
     * to be consumed (no metadata-refresh race). Only the fulfillment container
     * is awaited — other listeners (master.* consumers) subscribe to topics
     * that may not exist in this slice and would never be assigned.
     */
    private void waitForListenerAssignment() {
        MessageListenerContainer fulfillment = findFulfillmentContainer();
        ContainerTestUtils.waitForAssignment(fulfillment, 1);
    }

    private MessageListenerContainer findFulfillmentContainer() {
        for (MessageListenerContainer container : listenerRegistry.getListenerContainers()) {
            String[] topics = container.getContainerProperties().getTopics();
            if (topics != null) {
                for (String t : topics) {
                    if (TOPIC.equals(t)) {
                        return container;
                    }
                }
            }
        }
        throw new IllegalStateException(
                "No @KafkaListener container is subscribed to topic " + TOPIC);
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
}
