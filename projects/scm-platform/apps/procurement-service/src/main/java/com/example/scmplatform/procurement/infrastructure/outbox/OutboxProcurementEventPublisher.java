package com.example.scmplatform.procurement.infrastructure.outbox;

import com.example.common.id.UuidV7;
import com.example.scmplatform.procurement.application.event.ProcurementEventPublisher;
import com.example.scmplatform.procurement.domain.po.PurchaseOrder;
import com.example.scmplatform.procurement.infrastructure.persistence.jpa.ProcurementOutboxJpaEntity;
import com.example.scmplatform.procurement.infrastructure.persistence.jpa.ProcurementOutboxJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * procurement-service outbox write path (TASK-SCM-BE-032, outbox v2).
 *
 * <p>Persists one {@link ProcurementOutboxJpaEntity} ({@code procurement_outbox}
 * table) per domain event inside the caller's transaction, so the business
 * mutation and the outbox row commit atomically. {@code ProcurementOutboxPublisher}
 * drains the table to Kafka.
 *
 * <p>Replaces the v1 path (this class's predecessor {@code ProcurementEventPublisher
 * extends BaseEventPublisher} + lib {@code OutboxWriter} → {@code OutboxJpaEntity},
 * server-assigned {@code BIGSERIAL}, {@code status} string). <b>Wire is preserved
 * exactly</b>:
 * <ul>
 *   <li>The Kafka record <b>value</b> is the canonical 7-field envelope JSON
 *       ({@code eventId, eventType, source, occurredAt, schemaVersion=1,
 *       partitionKey, payload}) built here in the same field order the lib
 *       {@code BaseEventPublisher.writeEvent} used — byte-identical.</li>
 *   <li>Per-event {@code payload} maps are copied verbatim from the v1 publisher
 *       (same keys, same order, same value formatting).</li>
 *   <li>{@code aggregate_type}/{@code aggregate_id}/{@code event_type} match the
 *       v1 {@code writeEvent(...)} arguments. {@code aggregate_id} becomes the
 *       Kafka record key (partition_key is left null → the publisher falls back to
 *       aggregateId), preserving the v1 {@code kafkaTemplate.send(topic,
 *       aggregateId, payload)} key.</li>
 *   <li>{@code eventId} is a fresh UUIDv7 (as v1's {@code UuidV7.randomString()}),
 *       reused as both the envelope {@code eventId} and the row PK, so the Kafka
 *       header {@code eventId} matches the payload {@code eventId}.</li>
 * </ul>
 */
@Component
public class OutboxProcurementEventPublisher implements ProcurementEventPublisher {

    private static final String AGGREGATE_PO = "purchase_order";
    private static final String AGGREGATE_ASN = "asn";
    private static final String SOURCE = "scm-platform-procurement-service";
    private static final int SCHEMA_VERSION = 1;

    private final ProcurementOutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxProcurementEventPublisher(ProcurementOutboxJpaRepository outboxRepository,
                                           ObjectMapper objectMapper,
                                           Clock clock) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void publishPoSubmitted(PurchaseOrder po) {
        Map<String, Object> payload = base(po);
        payload.put("submittedAt", po.getSubmittedAt() != null ? po.getSubmittedAt().toString() : Instant.now().toString());
        writeEvent(AGGREGATE_PO, po.getId(), EVENT_PO_SUBMITTED, payload);
    }

    @Override
    public void publishPoAcknowledged(PurchaseOrder po, String supplierAckRef) {
        Map<String, Object> payload = base(po);
        payload.put("supplierAckRef", supplierAckRef);
        payload.put("acknowledgedAt", po.getAcknowledgedAt() != null ? po.getAcknowledgedAt().toString() : Instant.now().toString());
        writeEvent(AGGREGATE_PO, po.getId(), EVENT_PO_ACKNOWLEDGED, payload);
    }

    @Override
    public void publishPoConfirmed(PurchaseOrder po, String actorAccountId) {
        Map<String, Object> payload = base(po);
        payload.put("confirmedAt", po.getConfirmedAt() != null ? po.getConfirmedAt().toString() : Instant.now().toString());
        payload.put("actorAccountId", actorAccountId);
        writeEvent(AGGREGATE_PO, po.getId(), EVENT_PO_CONFIRMED, payload);
    }

    @Override
    public void publishPoCanceled(PurchaseOrder po, String reason, String actorAccountId) {
        Map<String, Object> payload = base(po);
        payload.put("reason", reason);
        payload.put("canceledAt", po.getCanceledAt() != null ? po.getCanceledAt().toString() : Instant.now().toString());
        payload.put("actorAccountId", actorAccountId);
        writeEvent(AGGREGATE_PO, po.getId(), EVENT_PO_CANCELED, payload);
    }

    @Override
    public void publishPoReceived(PurchaseOrder po) {
        Map<String, Object> payload = base(po);
        payload.put("receivedAt", Instant.now().toString());
        writeEvent(AGGREGATE_PO, po.getId(), EVENT_PO_RECEIVED, payload);
    }

    @Override
    public void publishAsnReceived(String asnId,
                                   String poId,
                                   String tenantId,
                                   String supplierAsnRef,
                                   Instant expectedArrivalAt,
                                   Instant receivedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("asnId", asnId);
        payload.put("poId", poId);
        payload.put("tenantId", tenantId);
        payload.put("supplierAsnRef", supplierAsnRef);
        payload.put("expectedArrivalAt", expectedArrivalAt.toString());
        payload.put("receivedAt", receivedAt.toString());
        writeEvent(AGGREGATE_ASN, asnId, EVENT_ASN_RECEIVED, payload);
    }

    private static Map<String, Object> base(PurchaseOrder po) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("poId", po.getId());
        p.put("poNumber", po.getPoNumber());
        p.put("tenantId", po.getTenantId());
        p.put("supplierId", po.getSupplierId());
        p.put("buyerAccountId", po.getBuyerAccountId());
        p.put("totalAmount", po.getTotalAmount().getAmount().toPlainString());
        p.put("currency", po.getTotalAmount().getCurrency());
        return p;
    }

    /**
     * Wrap the payload in the canonical 7-field envelope (v1 shape, same field
     * order as the lib {@code BaseEventPublisher.writeEvent}), serialise to JSON,
     * and persist a pending {@code procurement_outbox} row in the caller's
     * transaction. The fresh UUIDv7 doubles as the envelope {@code eventId} and
     * the row PK.
     */
    private void writeEvent(String aggregateType, String aggregateId,
                            String eventType, Map<String, Object> payload) {
        UUID eventId = UuidV7.randomUuid();
        Instant occurredAt = Instant.now(clock);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", eventType);
        envelope.put("source", SOURCE);
        envelope.put("occurredAt", occurredAt.toString());
        envelope.put("schemaVersion", SCHEMA_VERSION);
        envelope.put("partitionKey", aggregateId);
        envelope.put("payload", payload);

        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise " + eventType + " outbox envelope", e);
        }

        outboxRepository.save(new ProcurementOutboxJpaEntity(
                eventId, eventType, aggregateType, aggregateId,
                null, // partition_key: publisher falls back to aggregateId (the v1 Kafka key)
                json, occurredAt));
    }
}
