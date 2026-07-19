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
    String EVENT_INBOUND_EXPECTED = "scm.procurement.inbound-expected";
    String EVENT_INBOUND_EXPECTED_CANCELLED = "scm.procurement.inbound-expected.cancelled";

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

    /**
     * ADR-MONO-050 D1: publish {@code scm.procurement.inbound-expected.v1} for a
     * warehouse-addressed replenishment PO on CONFIRMED. The caller
     * ({@code PurchaseOrderApplicationService}) has already applied the
     * producer-side filter (D4) — this method assumes the PO is a
     * {@code WMS_WAREHOUSE}-addressed PO with a resolvable arrival date.
     */
    void publishInboundExpected(PurchaseOrder po);

    /**
     * ADR-MONO-050 D6.3: publish {@code scm.procurement.inbound-expected.cancelled.v1}
     * so wms can drop a not-yet-received expectation for a cancelled PO.
     */
    void publishInboundExpectedCancelled(PurchaseOrder po);
}
