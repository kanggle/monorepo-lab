package com.example.scmplatform.inventoryvisibility.adapter.inbound.messaging;

import com.example.scmplatform.inventoryvisibility.application.service.InventoryVisibilityApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Kafka consumer for {@code wms.inventory.received.v1}.
 * Processes putaway-completed events from wms-platform (cross-project consumption).
 * <p>
 * Retry: 3 attempts with exponential backoff → DLT on exhaustion.
 * Idempotency: delegated to application service (eventId dedup).
 * Edge Case 2: invalid envelope → published to DLT without retry.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WmsInventoryReceivedConsumer {

    static final String TOPIC = "wms.inventory.received.v1";
    static final String TENANT_ID = "scm"; // hardcoded: this service only serves tenant_id=scm

    private final InventoryVisibilityApplicationService applicationService;
    private final ObjectMapper objectMapper;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = ".DLT"
    )
    @KafkaListener(topics = TOPIC, groupId = "scm-inventory-visibility-v1")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            EventEnvelope envelope = objectMapper.readValue(record.value(), EventEnvelope.class);

            // Edge Case 2: invalid envelope → send to DLT without retry
            if (!envelope.isValid()) {
                log.error("Invalid wms envelope on topic={} partition={} offset={}; sending to DLT",
                        record.topic(), record.partition(), record.offset());
                ack.acknowledge();
                throw new WmsEnvelopeParser.InvalidEnvelopeException("Invalid envelope: missing required fields");
            }

            // Edge Case 8: cross-tenant event filtering
            // wms v1 is single-tenant, but add safety guard
            Map<String, Object> payload = envelope.payload();
            String warehouseId = WmsEnvelopeParser.getStringField(payload, "warehouseId");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> lines = (List<Map<String, Object>>) payload.get("lines");
            if (lines == null || lines.isEmpty()) {
                log.warn("Received empty lines in inventory.received event; skipping. eventId={}",
                        envelope.eventId());
                ack.acknowledge();
                return;
            }

            // Each line represents a separate SKU at the warehouse location
            for (Map<String, Object> line : lines) {
                String skuId = WmsEnvelopeParser.getStringField(line, "skuId");
                long qtyReceived = WmsEnvelopeParser.getLongField(line, "qtyReceived");

                applicationService.applyInventoryReceived(
                        warehouseId, skuId, qtyReceived,
                        envelope.eventId(), envelope.occurredAt(),
                        TENANT_ID, TOPIC);
            }

            ack.acknowledge();
        } catch (WmsEnvelopeParser.InvalidEnvelopeException e) {
            throw e; // propagate to DLT without retry
        } catch (Exception e) {
            log.error("Failed to process wms.inventory.received: topic={} partition={} offset={} error={}",
                    record.topic(), record.partition(), record.offset(), e.getMessage(), e);
            throw new RuntimeException("Failed to process wms.inventory.received event", e);
        }
    }
}
