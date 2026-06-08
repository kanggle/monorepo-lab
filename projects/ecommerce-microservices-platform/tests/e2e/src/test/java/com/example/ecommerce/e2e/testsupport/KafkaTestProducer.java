package com.example.ecommerce.e2e.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Host-side producer of the two SYNTHETIC events the e2e injects onto the shared
 * broker (TASK-MONO-195):
 *
 * <ol>
 *   <li>{@code product.product.stock-changed} — stands in for product-service,
 *       drives order-service PENDING → CONFIRMED (the loop trigger). Snake-case
 *       envelope per the ecommerce-internal convention.</li>
 *   <li>{@code wms.outbound.shipping.confirmed.v1} — stands in for the wms
 *       outbound-service boundary (the wms internal saga is not booted; gated by
 *       FulfillmentRequestedConsumerIT). camelCase wms envelope per
 *       {@code wms-shipment-subscriptions.md}.</li>
 * </ol>
 */
public class KafkaTestProducer implements AutoCloseable {

    private final KafkaProducer<String, String> producer;
    private final ObjectMapper mapper = new ObjectMapper();

    public KafkaTestProducer(String bootstrapServers) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        this.producer = new KafkaProducer<>(props);
    }

    /**
     * Publishes a {@code StockChanged} event with {@code reason=ORDER_RESERVED}
     * for the given order — the trigger order-service consumes to confirm.
     */
    public void publishStockChangedOrderReserved(String orderId, String productId, String variantId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("productId", productId);
        payload.put("variantId", variantId);
        payload.put("previousStock", 10);
        payload.put("currentStock", 9);
        payload.put("delta", -1);
        payload.put("reason", "ORDER_RESERVED");
        payload.put("orderId", orderId);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("event_id", UUID.randomUUID().toString());
        envelope.put("event_type", "StockChanged");
        envelope.put("occurred_at", Instant.now().toString());
        envelope.put("source", "e2e-product-stub");
        envelope.put("payload", payload);

        send("product.product.stock-changed", orderId, envelope);
    }

    /**
     * Publishes a wms {@code outbound.shipping.confirmed} event (camelCase
     * envelope) carrying {@code orderNo == orderId} — the wms boundary event the
     * ecommerce return leg correlates on.
     */
    public void publishWmsShippingConfirmed(String orderId, String shipmentNo, String carrierCode) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", UUID.randomUUID().toString());
        payload.put("orderNo", orderId);
        payload.put("shipmentNo", shipmentNo);
        payload.put("carrierCode", carrierCode);
        payload.put("shippedAt", Instant.now().toString());

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("eventType", "outbound.shipping.confirmed");
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("aggregateType", "outbound-order");
        envelope.put("aggregateId", orderId);
        envelope.put("payload", payload);

        send("wms.outbound.shipping.confirmed.v1", orderId, envelope);
    }

    private void send(String topic, String key, Map<String, Object> envelope) {
        try {
            String json = mapper.writeValueAsString(envelope);
            producer.send(new ProducerRecord<>(topic, key, json)).get(10, TimeUnit.SECONDS);
            producer.flush();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish to " + topic, e);
        }
    }

    @Override
    public void close() {
        producer.close();
    }
}
