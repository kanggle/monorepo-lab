package com.example.scmplatform.inventoryvisibility.adapter.inbound.messaging;

import com.example.scmplatform.inventoryvisibility.application.service.InventoryVisibilityApplicationService;
import com.example.scmplatform.inventoryvisibility.application.service.InventoryVisibilityApplicationService.ExpectedLine;
import com.example.scmplatform.inventoryvisibility.domain.error.NodeNotFoundException;
import com.example.scmplatform.inventoryvisibility.domain.error.NodeTypeConflictException;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Kafka consumer for {@code scm.procurement.inbound-expected.third-party.v1}
 * (ADR-MONO-055 §D4 / TASK-SCM-BE-049) — the scm-internal 3PL inbound-expectation
 * honour sink.
 *
 * <p>Intra-scm (not cross-project): {@code procurement-service} publishes this when a
 * {@code THIRD_PARTY_LOGISTICS}-addressed replenishment PO is confirmed, and this
 * service is its <b>only</b> consumer (wms is deliberately never involved —
 * ADR-MONO-054 §D3). Records one {@code inbound_expectations} row per PO line against
 * the addressed 3PL node.
 *
 * <p>Retry: 3 attempts with exponential backoff → DLT on exhaustion. A fail-closed
 * rejection (node absent / wrong type / cross-tenant → {@link NodeNotFoundException} /
 * {@link NodeTypeConflictException}) and an invalid envelope both route to the DLT
 * with a clear error and never create an orphan expectation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScmThirdPartyInboundExpectedConsumer {

    static final String TOPIC = "scm.procurement.inbound-expected.third-party.v1";
    static final String TENANT_ID = "scm"; // this service only serves tenant_id=scm

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
                log.error("Invalid 3PL inbound-expected envelope on topic={} partition={} offset={}; sending to DLT",
                        record.topic(), record.partition(), record.offset());
                ack.acknowledge();
                throw new WmsEnvelopeParser.InvalidEnvelopeException("Invalid envelope: missing required fields");
            }

            Map<String, Object> payload = envelope.payload();
            String poId = WmsEnvelopeParser.getStringField(payload, "poId");
            String poNumber = WmsEnvelopeParser.getStringField(payload, "poNumber");
            String nodeId = WmsEnvelopeParser.getStringField(payload, "destinationNodeId");
            String expectedArrivalDate = WmsEnvelopeParser.getNullableStringField(payload, "expectedArrivalDate");
            LocalDate expectedAt = expectedArrivalDate != null ? LocalDate.parse(expectedArrivalDate) : null;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> lines = (List<Map<String, Object>>) payload.get("lines");
            if (lines == null || lines.isEmpty()) {
                log.warn("3PL inbound-expected event with no lines; skipping. eventId={}", envelope.eventId());
                ack.acknowledge();
                return;
            }

            List<ExpectedLine> expectedLines = new ArrayList<>(lines.size());
            for (Map<String, Object> line : lines) {
                String skuCode = WmsEnvelopeParser.getStringField(line, "skuCode");
                BigDecimal expectedQty = new BigDecimal(WmsEnvelopeParser.getStringField(line, "expectedQty"));
                expectedLines.add(new ExpectedLine(skuCode, expectedQty));
            }

            applicationService.recordThirdPartyInboundExpectation(
                    nodeId, TENANT_ID, poId, poNumber, expectedAt, expectedLines);

            ack.acknowledge();
        } catch (WmsEnvelopeParser.InvalidEnvelopeException e) {
            throw e; // already logged; route to DLT
        } catch (NodeNotFoundException | NodeTypeConflictException e) {
            // Fail-closed (Edge Case: 3PL node deregistered/absent) — a clear error, no orphan
            // expectation. Routed to the DLT after retry exhaustion (the node will not appear
            // on retry); an operator inspects the DLT.
            log.error("3PL inbound-expected sink rejected (fail-closed): topic={} offset={} error={}",
                    record.topic(), record.offset(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to process 3PL inbound-expected: topic={} partition={} offset={} error={}",
                    record.topic(), record.partition(), record.offset(), e.getMessage(), e);
            throw new RuntimeException("Failed to process scm.procurement.inbound-expected.third-party event", e);
        }
    }
}
