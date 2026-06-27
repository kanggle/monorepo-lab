package com.example.scmplatform.procurement.application.event;

import com.example.scmplatform.procurement.domain.po.PurchaseOrder;

import java.time.Instant;

/**
 * Application port for publishing {@code scm.procurement.*} domain events to the
 * transactional outbox (rules/traits/transactional.md T3).
 *
 * <p><b>TASK-SCM-BE-032 (outbox v2).</b> This was a concrete class extending the
 * lib {@code BaseEventPublisher} (v1 write path). It is now a port; the
 * infrastructure adapter {@code OutboxProcurementEventPublisher} persists a v2
 * {@code procurement_outbox} row per event, and {@code ProcurementOutboxPublisher}
 * (extends {@code AbstractOutboxPublisher}) drains the table to Kafka. The
 * application layer depends only on this interface — callers
 * ({@code PurchaseOrderApplicationService}) and their unit tests are unchanged.
 *
 * <p>Topic naming convention: every Kafka topic is the envelope's
 * {@code eventType} field plus a {@code .v1} suffix (fan-platform alignment),
 * resolved by {@code ProcurementOutboxPublisher}.
 */
public interface ProcurementEventPublisher {

    String EVENT_PO_SUBMITTED = "scm.procurement.po.submitted";
    String EVENT_PO_ACKNOWLEDGED = "scm.procurement.po.acknowledged";
    String EVENT_PO_CONFIRMED = "scm.procurement.po.confirmed";
    String EVENT_PO_CANCELED = "scm.procurement.po.canceled";
    String EVENT_PO_RECEIVED = "scm.procurement.po.received";
    String EVENT_PO_CLOSED = "scm.procurement.po.closed";
    String EVENT_ASN_RECEIVED = "scm.procurement.asn.received";

    void publishPoSubmitted(PurchaseOrder po);

    void publishPoAcknowledged(PurchaseOrder po, String supplierAckRef);

    void publishPoConfirmed(PurchaseOrder po, String actorAccountId);

    void publishPoCanceled(PurchaseOrder po, String reason, String actorAccountId);

    void publishPoReceived(PurchaseOrder po);

    void publishAsnReceived(String asnId,
                            String poId,
                            String tenantId,
                            String supplierAsnRef,
                            Instant expectedArrivalAt,
                            Instant receivedAt);
}
