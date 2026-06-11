package com.example.scmplatform.e2e.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Test-side {@link KafkaProducer} that emits cross-project wms-platform
 * events into the shared Kafka cluster, simulating the wms inventory service
 * publishing without actually booting a wms container.
 *
 * <p>The envelope shape mirrors {@code projects/wms-platform/specs/contracts/
 * events/inventory-events.md} § Global Envelope and feeds the inventory-
 * visibility consumer's {@link com.example.scmplatform.inventoryvisibility
 * .adapter.inbound.messaging.EventEnvelope} deserialization. This avoids
 * the Failure Scenario B in the task spec ("Cross-project consumption is
 * inherently wms-dependent — overhead increases").
 */
public final class KafkaTestProducer implements AutoCloseable {

    public static final String TOPIC_WMS_INVENTORY_ADJUSTED = "wms.inventory.adjusted.v1";
    public static final String TOPIC_WMS_INVENTORY_RECEIVED = "wms.inventory.received.v1";
    public static final String TOPIC_WMS_INVENTORY_ALERT = "wms.inventory.alert.v1";

    private static final String DEFAULT_PRODUCER = "inventory-service";

    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper;

    public KafkaTestProducer(String bootstrapServers) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // Keep the producer transactional-quiet — tests are linear and do not
        // need exactly-once semantics on the test side.
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.LINGER_MS_CONFIG, 0);
        this.producer = new KafkaProducer<>(props);

        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    /**
     * Emit a {@code wms.inventory.adjusted.v1} event whose payload mirrors the
     * authoritative wms envelope shape consumed by
     * {@link com.example.scmplatform.inventoryvisibility.adapter.inbound.messaging.WmsInventoryAdjustedConsumer}.
     *
     * <p>Returns the {@code eventId} so tests can assert idempotent dedupe
     * (T8) when re-publishing the same event.
     */
    public UUID publishInventoryAdjusted(UUID eventId, String locationId, String skuId, long delta)
            throws ExecutionException, InterruptedException, TimeoutException {
        UUID id = eventId != null ? eventId : UUID.randomUUID();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", id.toString());
        envelope.put("eventType", "inventory.adjusted");
        envelope.put("eventVersion", 1);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("producer", DEFAULT_PRODUCER);
        envelope.put("aggregateType", "stock_adjustment");
        envelope.put("aggregateId", UUID.randomUUID().toString());
        envelope.put("traceId", UUID.randomUUID().toString());
        envelope.put("actorId", "system:e2e-producer");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("adjustmentId", UUID.randomUUID().toString());
        payload.put("locationId", locationId);
        payload.put("skuId", skuId);
        payload.put("delta", delta);
        payload.put("reasonCode", delta >= 0 ? "ADJUSTMENT_GAIN" : "ADJUSTMENT_LOSS");
        envelope.put("payload", payload);

        sendBlocking(TOPIC_WMS_INVENTORY_ADJUSTED, locationId, envelope);
        return id;
    }

    /** Emit a {@code wms.inventory.received.v1} event with full envelope shape. */
    public UUID publishInventoryReceived(UUID eventId, String locationId, String skuId, long qtyReceived)
            throws ExecutionException, InterruptedException, TimeoutException {
        UUID id = eventId != null ? eventId : UUID.randomUUID();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", id.toString());
        envelope.put("eventType", "inventory.received");
        envelope.put("eventVersion", 1);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("producer", DEFAULT_PRODUCER);
        envelope.put("aggregateType", "inventory");
        envelope.put("aggregateId", UUID.randomUUID().toString());
        envelope.put("traceId", UUID.randomUUID().toString());
        envelope.put("actorId", "system:e2e-producer");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("warehouseId", locationId);
        payload.put("locationId", locationId);
        payload.put("skuId", skuId);
        payload.put("qtyReceived", qtyReceived);
        envelope.put("payload", payload);

        sendBlocking(TOPIC_WMS_INVENTORY_RECEIVED, locationId, envelope);
        return id;
    }

    /**
     * Emit a {@code wms.inventory.alert.v1} low-stock alert (ADR-MONO-027 D1)
     * whose envelope mirrors the wms {@code inventory.low-stock-detected} shape
     * that {@code demand-planning-service}'s {@code WmsLowStockAlertConsumer}
     * deserializes. {@code warehouseId} (the location UUID) becomes the
     * suggestion's warehouse dimension; {@code skuCode} the join key to the
     * reorder policy + supplier mapping.
     *
     * @return the {@code eventId} so tests can re-publish for T8 dedup assertions.
     */
    public UUID publishLowStockAlert(UUID eventId, String skuCode, String warehouseId,
                                     int availableQty, int threshold)
            throws ExecutionException, InterruptedException, TimeoutException {
        UUID id = eventId != null ? eventId : UUID.randomUUID();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", id.toString());
        envelope.put("eventType", "inventory.low-stock-detected");
        envelope.put("eventVersion", 1);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("producer", DEFAULT_PRODUCER);
        envelope.put("aggregateType", "inventory");
        envelope.put("aggregateId", warehouseId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("skuId", UUID.randomUUID().toString());
        payload.put("skuCode", skuCode);
        payload.put("locationId", warehouseId);
        payload.put("locationCode", "WH-E2E");
        payload.put("availableQty", availableQty);
        payload.put("threshold", threshold);
        payload.put("triggeringEventType", "inventory.adjusted");
        envelope.put("payload", payload);

        sendBlocking(TOPIC_WMS_INVENTORY_ALERT, warehouseId, envelope);
        return id;
    }

    private void sendBlocking(String topic, String key, Map<String, Object> envelope)
            throws ExecutionException, InterruptedException, TimeoutException {
        String value;
        try {
            value = objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize wms envelope", e);
        }
        producer.send(new ProducerRecord<>(topic, key, value)).get(15, TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        producer.flush();
        producer.close();
    }
}
