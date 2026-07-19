package com.example.scmplatform.demandplanning.adapter.inbound.messaging;

import com.example.scmplatform.demandplanning.application.usecase.EvaluateReorderUseCase;
import com.example.scmplatform.demandplanning.domain.error.SkuSupplierUnmappedException;
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

import java.util.UUID;

/**
 * Kafka consumer for {@code wms.inventory.alert.v1}.
 * Processes {@code inventory.low-stock-detected} events from wms-platform (cross-project).
 *
 * <p>Retry: 3 attempts with exponential backoff → DLT on exhaustion.
 * Non-retryable: null envelope / null payload / unmapped SKU → immediate DLT + ops alert (fail-closed).
 * Idempotency: delegated to {@link EvaluateReorderUseCase} (eventId T8 dedup).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WmsLowStockAlertConsumer {

    public static final String TOPIC = "wms.inventory.alert.v1";
    public static final String GROUP = "scm-demand-planning-v1";

    private final EvaluateReorderUseCase evaluateReorderUseCase;
    private final ObjectMapper objectMapper;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = ".DLT",
            exclude = {NonRetryableConsumerException.class}
    )
    @KafkaListener(topics = TOPIC, groupId = GROUP)
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            if (record.value() == null) {
                log.error("Null message value on topic={} partition={} offset={}; routing to DLT",
                        record.topic(), record.partition(), record.offset());
                ack.acknowledge();
                throw new NonRetryableConsumerException("Null message value on " + TOPIC);
            }

            WmsAlertEnvelope envelope;
            try {
                envelope = objectMapper.readValue(record.value(), WmsAlertEnvelope.class);
            } catch (Exception parseEx) {
                log.error("Failed to parse wms alert envelope topic={} partition={} offset={}: {}",
                        record.topic(), record.partition(), record.offset(), parseEx.getMessage());
                ack.acknowledge();
                throw new NonRetryableConsumerException("Malformed envelope on " + TOPIC, parseEx);
            }

            // Non-retryable: malformed envelope (missing eventId or payload)
            if (!envelope.isValid()) {
                log.error("Invalid wms alert envelope (missing required fields) topic={} partition={} offset={}",
                        record.topic(), record.partition(), record.offset());
                ack.acknowledge();
                throw new NonRetryableConsumerException("Invalid envelope: missing required fields on " + TOPIC);
            }

            // Non-retryable: unmapped SKU (fail-closed — SkuSupplierUnmappedException is non-retryable)
            String skuCode = envelope.skuCode();
            if (skuCode == null || skuCode.isBlank()) {
                log.error("Missing skuCode in alert payload eventId={}", envelope.eventId());
                ack.acknowledge();
                throw new NonRetryableConsumerException("Missing skuCode in alert payload eventId=" + envelope.eventId());
            }

            String locationId = envelope.locationId();
            if (locationId == null || locationId.isBlank()) {
                log.error("Missing locationId in alert payload eventId={}", envelope.eventId());
                ack.acknowledge();
                throw new NonRetryableConsumerException("Missing locationId in alert payload eventId=" + envelope.eventId());
            }

            UUID warehouseId;
            try {
                warehouseId = UUID.fromString(locationId);
            } catch (IllegalArgumentException e) {
                log.error("Non-UUID locationId in alert payload eventId={} locationId={}", envelope.eventId(), locationId);
                ack.acknowledge();
                throw new NonRetryableConsumerException("Non-UUID locationId eventId=" + envelope.eventId());
            }

            // ADR-MONO-050 D9 / TASK-SCM-BE-038: warehouseCode is an ADDITIVE field on the
            // alert — the wms producer emits it best-effort (null during a warehouse-master
            // snapshot race, ADR-050 §7). Treat it as OPTIONAL: an absent code must NOT DLT
            // the alert, because that would silently break the pre-existing ADR-027
            // replenishment loop for the SKU (not just the new inbound-expected leg). Raise
            // the reorder suggestion regardless; the downstream inbound-expected leg
            // fail-closes on a null code (no wms expectation emitted) exactly like the
            // batch-sweep path — so the reorder still happens, only the wms hand-off is
            // skipped until the warehouse master populates.
            String warehouseCode = envelope.warehouseCode();
            if (warehouseCode == null || warehouseCode.isBlank()) {
                log.warn("Absent warehouseCode in alert payload eventId={} locationId={} — "
                        + "raising reorder suggestion without a wms inbound-expected leg "
                        + "(additive-field degrade, ADR-050 D9)", envelope.eventId(), locationId);
                warehouseCode = null;
            }

            evaluateReorderUseCase.evaluateFromAlert(
                    envelope.eventId(),
                    skuCode,
                    warehouseId,
                    warehouseCode,
                    envelope.availableQty(),
                    envelope.threshold(),
                    envelope.occurredAt()
            );

            ack.acknowledge();

        } catch (NonRetryableConsumerException e) {
            // Already ack'd; propagate so @RetryableTopic routes to DLT without retry
            throw e;
        } catch (SkuSupplierUnmappedException e) {
            // Unmapped SKU: non-retryable — ack and route to DLT
            log.error("Unmapped SKU — routing to DLT immediately: {}", e.getMessage());
            try { ack.acknowledge(); } catch (Exception ignored) {}
            throw new NonRetryableConsumerException("Unmapped SKU: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Transient error processing alert topic={} partition={} offset={}: {}",
                    record.topic(), record.partition(), record.offset(), e.getMessage(), e);
            // Retryable — let @RetryableTopic handle backoff + eventual DLT
            throw new RuntimeException("Transient error processing alert event", e);
        }
    }
}
