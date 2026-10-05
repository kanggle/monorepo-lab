package com.example.scmplatform.inventoryvisibility.adapter.inbound.messaging;

import com.example.scmplatform.inventoryvisibility.application.service.InventoryVisibilityApplicationService;
import com.example.scmplatform.inventoryvisibility.application.service.InventoryVisibilityApplicationService.ConfirmedLine;
import com.example.scmplatform.inventoryvisibility.config.ProjectionTenant;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Kafka consumer for {@code wms.inventory.confirmed.v1} (TASK-MONO-762).
 *
 * <p>AC-0 ⓐ: the snapshot quantity means on-hand (available + reserved). Only
 * {@code confirmed} moves it — this service does NOT subscribe to
 * {@code wms.inventory.reserved.v1} / {@code wms.inventory.released.v1} (those move stock
 * between available and reserved, which on-hand is indifferent to).
 *
 * <p>Retry: 3 attempts + DLT, same as the other three wms consumers. Unlike those three,
 * this one never auto-registers a node and never auto-creates a snapshot row on a missing
 * node/SKU — {@link InventoryVisibilityApplicationService#applyInventoryConfirmed} throws,
 * which this consumer lets propagate through the generic catch block below so
 * {@code @RetryableTopic} retries then DLTs (AC-0 Edge Case: confirmed arriving before
 * received).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WmsInventoryConfirmedConsumer {

    static final String TOPIC = "wms.inventory.confirmed.v1";

    private final InventoryVisibilityApplicationService applicationService;
    private final ObjectMapper objectMapper;
    // wms events carry no tenant — project into the configured one (TASK-MONO-760).
    private final ProjectionTenant projectionTenant;

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

            Map<String, Object> payload = envelope.payload();
            String warehouseId = WmsEnvelopeParser.getStringField(payload, "warehouseId");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> lines = (List<Map<String, Object>>) payload.get("lines");
            if (lines == null || lines.isEmpty()) {
                log.warn("Received empty lines in inventory.confirmed event; skipping. eventId={}",
                        envelope.eventId());
                ack.acknowledge();
                return;
            }

            List<ConfirmedLine> confirmedLines = new ArrayList<>(lines.size());
            for (Map<String, Object> line : lines) {
                String skuId = WmsEnvelopeParser.getStringField(line, "skuId");
                long quantity = WmsEnvelopeParser.getLongField(line, "quantity");
                confirmedLines.add(new ConfirmedLine(skuId, quantity));
            }

            applicationService.applyInventoryConfirmed(
                    warehouseId, confirmedLines,
                    envelope.eventId(), envelope.occurredAt(),
                    projectionTenant.id(), TOPIC);

            ack.acknowledge();
        } catch (WmsEnvelopeParser.InvalidEnvelopeException e) {
            throw e; // propagate to DLT without retry
        } catch (Exception e) {
            log.error("Failed to process wms.inventory.confirmed: topic={} partition={} offset={} error={}",
                    record.topic(), record.partition(), record.offset(), e.getMessage(), e);
            throw new RuntimeException("Failed to process wms.inventory.confirmed event", e);
        }
    }
}
