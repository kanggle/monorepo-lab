package com.example.scmplatform.procurement.application.event;

import com.example.messaging.event.BaseEventPublisher;
import com.example.messaging.outbox.OutboxWriter;
import com.example.scmplatform.procurement.domain.po.PurchaseOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Appends {@code scm.procurement.*} events to the transactional outbox
 * (rules/traits/transactional.md T3).
 *
 * <p>Topic naming convention: every Kafka topic is the envelope's
 * {@code eventType} field plus a {@code .v1} suffix (TASK-SCM-BE-002 §
 * Acceptance Criteria #6, fan-platform alignment).
 */
@Component
public class ProcurementEventPublisher extends BaseEventPublisher {

    private static final String AGGREGATE_PO = "purchase_order";
    private static final String AGGREGATE_ASN = "asn";
    private static final String SOURCE = "scm-platform-procurement-service";

    public static final String EVENT_PO_SUBMITTED = "scm.procurement.po.submitted";
    public static final String EVENT_PO_ACKNOWLEDGED = "scm.procurement.po.acknowledged";
    public static final String EVENT_PO_CONFIRMED = "scm.procurement.po.confirmed";
    public static final String EVENT_PO_CANCELED = "scm.procurement.po.canceled";
    public static final String EVENT_PO_RECEIVED = "scm.procurement.po.received";
    public static final String EVENT_PO_CLOSED = "scm.procurement.po.closed";
    public static final String EVENT_ASN_RECEIVED = "scm.procurement.asn.received";

    public ProcurementEventPublisher(OutboxWriter outboxWriter, ObjectMapper objectMapper) {
        super(outboxWriter, objectMapper);
    }

    public void publishPoSubmitted(PurchaseOrder po) {
        Map<String, Object> payload = base(po);
        payload.put("submittedAt", po.getSubmittedAt() != null ? po.getSubmittedAt().toString() : Instant.now().toString());
        writeEvent(AGGREGATE_PO, po.getId(), EVENT_PO_SUBMITTED, SOURCE, payload);
    }

    public void publishPoAcknowledged(PurchaseOrder po, String supplierAckRef) {
        Map<String, Object> payload = base(po);
        payload.put("supplierAckRef", supplierAckRef);
        payload.put("acknowledgedAt", po.getAcknowledgedAt() != null ? po.getAcknowledgedAt().toString() : Instant.now().toString());
        writeEvent(AGGREGATE_PO, po.getId(), EVENT_PO_ACKNOWLEDGED, SOURCE, payload);
    }

    public void publishPoConfirmed(PurchaseOrder po, String actorAccountId) {
        Map<String, Object> payload = base(po);
        payload.put("confirmedAt", po.getConfirmedAt() != null ? po.getConfirmedAt().toString() : Instant.now().toString());
        payload.put("actorAccountId", actorAccountId);
        writeEvent(AGGREGATE_PO, po.getId(), EVENT_PO_CONFIRMED, SOURCE, payload);
    }

    public void publishPoCanceled(PurchaseOrder po, String reason, String actorAccountId) {
        Map<String, Object> payload = base(po);
        payload.put("reason", reason);
        payload.put("canceledAt", po.getCanceledAt() != null ? po.getCanceledAt().toString() : Instant.now().toString());
        payload.put("actorAccountId", actorAccountId);
        writeEvent(AGGREGATE_PO, po.getId(), EVENT_PO_CANCELED, SOURCE, payload);
    }

    public void publishPoReceived(PurchaseOrder po) {
        Map<String, Object> payload = base(po);
        payload.put("receivedAt", Instant.now().toString());
        writeEvent(AGGREGATE_PO, po.getId(), EVENT_PO_RECEIVED, SOURCE, payload);
    }

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
        writeEvent(AGGREGATE_ASN, asnId, EVENT_ASN_RECEIVED, SOURCE, payload);
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
}
