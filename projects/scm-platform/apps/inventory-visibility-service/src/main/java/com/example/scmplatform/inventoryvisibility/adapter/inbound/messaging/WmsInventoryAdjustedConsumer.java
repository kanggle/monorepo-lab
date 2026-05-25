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

import java.util.Map;

/**
 * Kafka consumer for {@code wms.inventory.adjusted.v1}.
 * Processes manual adjustment events (positive or negative delta).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WmsInventoryAdjustedConsumer {

    static final String TOPIC = "wms.inventory.adjusted.v1";
    static final String TENANT_ID = "scm";

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

            if (!envelope.isValid()) {
                log.error("Invalid wms envelope on topic={} offset={}; sending to DLT",
                        record.topic(), record.offset());
                ack.acknowledge();
                throw new WmsEnvelopeParser.InvalidEnvelopeException(
                        "Invalid envelope: missing required fields");
            }

            Map<String, Object> payload = envelope.payload();
            String locationId = WmsEnvelopeParser.getStringField(payload, "locationId");
            String skuId = WmsEnvelopeParser.getStringField(payload, "skuId");
            long delta = WmsEnvelopeParser.getLongField(payload, "delta");

            applicationService.applyInventoryAdjusted(
                    locationId, skuId, delta,
                    envelope.eventId(), envelope.occurredAt(),
                    TENANT_ID, TOPIC);

            ack.acknowledge();
        } catch (WmsEnvelopeParser.InvalidEnvelopeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to process wms.inventory.adjusted: partition={} offset={} error={}",
                    record.partition(), record.offset(), e.getMessage(), e);
            throw new RuntimeException("Failed to process wms.inventory.adjusted event", e);
        }
    }
}
