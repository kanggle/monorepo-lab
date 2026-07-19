package com.example.scmplatform.procurement.application;

import com.example.common.id.UuidV7;
import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.scmplatform.procurement.application.command.AcknowledgePurchaseOrderCommand;
import com.example.scmplatform.procurement.application.command.CancelPurchaseOrderCommand;
import com.example.scmplatform.procurement.application.command.ConfirmPurchaseOrderCommand;
import com.example.scmplatform.procurement.application.command.DraftFromSuggestionCommand;
import com.example.scmplatform.procurement.application.command.DraftPurchaseOrderCommand;
import com.example.scmplatform.procurement.application.command.ReceiveAsnCommand;
import com.example.scmplatform.procurement.application.command.SubmitPurchaseOrderCommand;
import com.example.scmplatform.procurement.application.event.ProcurementEventPublisher;
import com.example.scmplatform.procurement.application.port.outbound.SupplierAdapterPort;
import com.example.scmplatform.procurement.domain.asn.AdvanceShipmentNotice;
import com.example.scmplatform.procurement.domain.asn.AsnLine;
import com.example.scmplatform.procurement.domain.asn.repository.AsnRepository;
import com.example.scmplatform.procurement.domain.audit.AuditLog;
import com.example.scmplatform.procurement.domain.audit.AuditLogRepository;
import com.example.scmplatform.procurement.domain.error.PoNotFoundException;
import com.example.scmplatform.procurement.domain.error.SupplierNotFoundException;
import com.example.scmplatform.procurement.domain.po.PurchaseOrder;
import com.example.scmplatform.procurement.domain.po.PurchaseOrderLine;
import com.example.scmplatform.procurement.domain.po.repository.PurchaseOrderRepository;
import com.example.scmplatform.procurement.domain.po.status.ActorType;
import com.example.scmplatform.procurement.domain.po.status.PoStatus;
import com.example.scmplatform.procurement.domain.po.status.PoStatusHistory;
import com.example.scmplatform.procurement.domain.po.status.PoStatusHistoryRepository;
import com.example.scmplatform.procurement.domain.supplier.Supplier;
import com.example.scmplatform.procurement.domain.supplier.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Procurement application service — orchestrates PO lifecycle use cases on
 * top of the domain aggregate {@link PurchaseOrder} + {@link AdvanceShipmentNotice}.
 *
 * <p>Per rules/traits/transactional.md every method is a single
 * {@code @Transactional} command boundary; outbox writes happen inside the
 * same transaction as state changes (T2 + T3). Supplier external calls are
 * intentionally outside the DB transaction (Edge Case #9) — the use case
 * persists DRAFT-or-SUBMITTED state, then issues the supplier call, then
 * commits the post-call state in a follow-up step. v1 simplification:
 * supplier submission failure rolls back the SUBMITTED transition (PO stays
 * DRAFT) so the operator can retry — Edge Case #7.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseOrderApplicationService {

    private static final String AGGREGATE_PO = "purchase_order";

    /**
     * Statuses where a duplicate supplier-ack webhook is a no-op (idempotent).
     * The PO has already moved past SUBMITTED and another ack for the same PO
     * must not re-apply the transition.
     */
    private static final Set<PoStatus> ALREADY_PAST_SUBMITTED = EnumSet.of(
            PoStatus.ACKNOWLEDGED, PoStatus.CONFIRMED,
            PoStatus.PARTIALLY_RECEIVED, PoStatus.RECEIVED,
            PoStatus.SETTLED, PoStatus.CLOSED);

    private final PurchaseOrderRepository poRepository;
    private final PoStatusHistoryRepository historyRepository;
    private final AsnRepository asnRepository;
    private final SupplierRepository supplierRepository;
    private final AuditLogRepository auditLogRepository;
    private final SupplierAdapterPort supplierAdapter;
    private final ProcurementEventPublisher eventPublisher;

    // ---------------- DRAFT PO ----------------

    @Transactional
    public PurchaseOrderView draft(DraftPurchaseOrderCommand cmd) {
        ActorContext actor = cmd.actor();
        Supplier supplier = supplierRepository.findById(cmd.supplierId(), actor.tenantId())
                .orElseThrow(() -> new SupplierNotFoundException(
                        "Supplier not found: " + cmd.supplierId()));
        supplier.ensureUsableForOrdering();

        String poId = UuidV7.randomString();
        // poNumber suffix must be pure random — UUID v7's first 8 hex chars are
        // a millisecond-resolution timestamp, so two drafts in the same ms (e.g.
        // a buyer batching 6 POs in a tight loop) hash to the same prefix and
        // trip the unique (tenant_id, po_number) constraint with a 23505. Use
        // the random tail of UUID v7 instead (last 8 hex chars are pure rand_b
        // per RFC 9562). TASK-SCM-INT-001b root cause; matches the IT-side
        // pattern from TASK-SCM-BE-002d.
        String poNumber = "PO-" + poId.substring(poId.length() - 8).toUpperCase();
        PurchaseOrder po = PurchaseOrder.createDraft(
                poId,
                actor.tenantId(),
                poNumber,
                cmd.supplierId(),
                actor.accountId(),
                cmd.currency()
        );
        for (DraftPurchaseOrderCommand.Line line : cmd.lines()) {
            PurchaseOrderLine lineEntity = PurchaseOrderLine.create(
                    UuidV7.randomString(),
                    poId,
                    actor.tenantId(),
                    line.lineNo(),
                    line.sku(),
                    line.supplierSku(),
                    line.quantity(),
                    line.unitPrice()
            );
            po.addLine(lineEntity);
        }
        PurchaseOrder saved = poRepository.save(po);

        auditLogRepository.save(AuditLog.of(
                saved.getTenantId(), AGGREGATE_PO, saved.getId(), "DRAFT",
                actor.accountId(), actor.actorType(), null, null));

        return PurchaseOrderView.from(saved);
    }

    // ---------------- DRAFT PO FROM SUGGESTION (ADR-MONO-027 D5) ----------------

    /**
     * Materialize an approved reorder suggestion into a DRAFT purchase order
     * (ADR-MONO-027 D5). Reuses the existing DRAFT state + PoStatusMachine +
     * audit — <strong>no new PO state, no auto-SUBMIT</strong>.
     *
     * <p><strong>Idempotent on {@code sourceSuggestionId}</strong> (the
     * cross-service idempotency key, S2): a repeated call for the same suggestion
     * returns the <em>existing</em> PO rather than creating a duplicate. The
     * find-or-create is backed by the partial-unique
     * {@code (tenant_id, source_suggestion_id)} index — a concurrent double-call
     * trips it, and we re-read the winner.
     *
     * <p><strong>No supplier FK validation</strong> (FK-free cross-service
     * convention, Edge Case): an unknown supplier reference is caught by the
     * operator at DRAFT review, not rejected here.
     */
    @Transactional
    public PurchaseOrderView draftFromSuggestion(DraftFromSuggestionCommand cmd) {
        ActorContext actor = cmd.actor();

        // Idempotency: a PO already materialized from this suggestion wins.
        Optional<PurchaseOrder> existing =
                poRepository.findBySourceSuggestionId(cmd.sourceSuggestionId(), actor.tenantId());
        if (existing.isPresent()) {
            log.info("from-suggestion idempotent hit: suggestion={} returns existing PO {}",
                    cmd.sourceSuggestionId(), existing.get().getId());
            return PurchaseOrderView.from(existing.get());
        }

        String poId = UuidV7.randomString();
        String poNumber = "PO-" + poId.substring(poId.length() - 8).toUpperCase();
        PurchaseOrder po = PurchaseOrder.createDraftFromSuggestion(
                poId,
                actor.tenantId(),
                poNumber,
                cmd.supplierId(),
                actor.accountId(),
                cmd.currency(),
                cmd.sourceSuggestionId(),
                cmd.destinationWarehouseId(),
                cmd.destinationNodeType(),
                cmd.leadTimeDays()
        );
        for (DraftFromSuggestionCommand.Line line : cmd.lines()) {
            // unitPriceRef is a placeholder, not a price — persist 0 pending the
            // operator setting the real price at DRAFT review (ADR-027 D5).
            PurchaseOrderLine lineEntity = PurchaseOrderLine.create(
                    UuidV7.randomString(),
                    poId,
                    actor.tenantId(),
                    line.lineNo(),
                    line.sku(),
                    null,
                    BigDecimal.valueOf(line.quantity()),
                    BigDecimal.ZERO
            );
            po.addLine(lineEntity);
        }

        PurchaseOrder saved;
        try {
            saved = poRepository.save(po);
        } catch (DataIntegrityViolationException race) {
            // Concurrent call for the same suggestion won the unique index; the
            // loser re-reads the winner (idempotent contract preserved).
            log.info("from-suggestion race on suggestion={} — re-reading the winning PO",
                    cmd.sourceSuggestionId());
            return poRepository.findBySourceSuggestionId(cmd.sourceSuggestionId(), actor.tenantId())
                    .map(PurchaseOrderView::from)
                    .orElseThrow(() -> race);
        }

        auditLogRepository.save(AuditLog.of(
                saved.getTenantId(), AGGREGATE_PO, saved.getId(), "DRAFT",
                actor.accountId(), actor.actorType(), null,
                "{\"origin\":\"DEMAND_PLANNING\",\"sourceSuggestionId\":\""
                        + cmd.sourceSuggestionId() + "\"}"));

        return PurchaseOrderView.from(saved);
    }

    // ---------------- SUBMIT PO (DRAFT → SUBMITTED) ----------------

    @Transactional
    public PurchaseOrderView submit(SubmitPurchaseOrderCommand cmd) {
        ActorContext actor = cmd.actor();
        PurchaseOrder po = loadPo(cmd.poId(), actor.tenantId());

        // 1) Issue the supplier call FIRST (Edge Case #7): if the supplier
        //    circuit is OPEN we must not transition the PO to SUBMITTED.
        SupplierAdapterPort.SupplierSubmissionResult result = supplierAdapter.submitPurchaseOrder(
                po, cmd.idempotencyKey());

        // 2) Apply state transition + history + outbox in this same transaction
        PoStatus previous = po.submit(actor.actorType());
        PurchaseOrder saved = poRepository.save(po);
        recordTransition(saved, previous, PoStatus.SUBMITTED, actor.actorType(), actor.accountId(),
                "supplier ref=" + result.supplierReceiptRef());
        auditLogRepository.save(AuditLog.of(
                saved.getTenantId(), AGGREGATE_PO, saved.getId(),
                "SUBMIT", actor.accountId(), actor.actorType(),
                "{\"status\":\"" + previous + "\"}",
                "{\"status\":\"SUBMITTED\",\"supplierReceiptRef\":\""
                        + result.supplierReceiptRef() + "\"}"));
        eventPublisher.publishPoSubmitted(saved);
        return PurchaseOrderView.from(saved);
    }

    // ---------------- ACKNOWLEDGE PO (webhook from supplier) ----------------

    @Transactional
    public PurchaseOrderView acknowledge(AcknowledgePurchaseOrderCommand cmd) {
        PurchaseOrder po = loadPo(cmd.poId(), cmd.tenantId());
        // Idempotency: already past SUBMITTED PO with same supplier_ack_ref → no-op
        if (ALREADY_PAST_SUBMITTED.contains(po.getStatus())) {
            log.info("Supplier ack received for PO {} already in status {} — treating as idempotent no-op",
                    po.getId(), po.getStatus());
            return PurchaseOrderView.from(po);
        }

        PoStatus previous = po.acknowledge(ActorType.SUPPLIER);
        PurchaseOrder saved = poRepository.save(po);
        recordTransition(saved, previous, PoStatus.ACKNOWLEDGED, ActorType.SUPPLIER, null,
                "ack ref=" + cmd.supplierAckRef());
        auditLogRepository.save(AuditLog.of(
                saved.getTenantId(), AGGREGATE_PO, saved.getId(),
                "ACKNOWLEDGE", null, ActorType.SUPPLIER,
                "{\"status\":\"" + previous + "\"}",
                "{\"status\":\"ACKNOWLEDGED\"}"));
        eventPublisher.publishPoAcknowledged(saved, cmd.supplierAckRef());
        return PurchaseOrderView.from(saved);
    }

    // ---------------- CONFIRM PO ----------------

    @Transactional
    public PurchaseOrderView confirm(ConfirmPurchaseOrderCommand cmd) {
        ActorContext actor = cmd.actor();
        PurchaseOrder po = loadPo(cmd.poId(), actor.tenantId());
        PoStatus previous = po.confirm(actor.actorType());
        PurchaseOrder saved = poRepository.save(po);
        recordTransition(saved, previous, PoStatus.CONFIRMED, actor.actorType(), actor.accountId(), null);
        auditLogRepository.save(AuditLog.of(
                saved.getTenantId(), AGGREGATE_PO, saved.getId(),
                "CONFIRM", actor.accountId(), actor.actorType(),
                "{\"status\":\"" + previous + "\"}",
                "{\"status\":\"CONFIRMED\"}"));
        eventPublisher.publishPoConfirmed(saved, actor.accountId());
        // ADR-MONO-050 D1/D2/D4: a warehouse-addressed replenishment PO also
        // publishes an inbound-expected event to wms, in the SAME transaction as
        // the CONFIRMED state change (outbox → no lost events, D8).
        maybePublishInboundExpected(saved);
        return PurchaseOrderView.from(saved);
    }

    /**
     * ADR-MONO-050 D3/D4 producer-side gate. Emits {@code inbound-expected.v1}
     * only for own-warehouse ({@code WMS_WAREHOUSE}) replenishment POs. Everything
     * else is fail-closed:
     * <ul>
     *   <li>operator-authored / 3PL-destination PO → not eligible → skip (D4);</li>
     *   <li>eligible warehouse PO with an unknown lead time → skip + warn rather
     *       than emit a wrong arrival horizon (Failure Scenario B — never guess).</li>
     * </ul>
     */
    private void maybePublishInboundExpected(PurchaseOrder po) {
        if (!po.isWmsWarehouseDestination()) {
            log.debug("PO {} is not a WMS_WAREHOUSE-addressed replenishment PO "
                    + "(origin={}, nodeType={}) — no inbound-expected emitted (ADR-050 D4)",
                    po.getId(), po.getOrigin(), po.getDestinationNodeType());
            return;
        }
        if (po.expectedArrivalDate() == null) {
            // fail-closed: warehouse is known but the lead time is not — do not
            // fabricate an arrival horizon. Surfaced to ops via WARN.
            log.warn("inbound-expected NOT emitted for PO {} (warehouse={}): lead_time_days "
                    + "missing — fail-closed per ADR-050 (no silent wrong horizon)",
                    po.getId(), po.getDestinationWarehouseId());
            return;
        }
        eventPublisher.publishInboundExpected(po);
    }

    // ---------------- CANCEL PO ----------------

    @Transactional
    public PurchaseOrderView cancel(CancelPurchaseOrderCommand cmd) {
        ActorContext actor = cmd.actor();
        PurchaseOrder po = loadPo(cmd.poId(), actor.tenantId());
        PoStatus previous = po.cancel(actor.actorType(), cmd.reason());
        PurchaseOrder saved = poRepository.save(po);
        recordTransition(saved, previous, PoStatus.CANCELED, actor.actorType(), actor.accountId(), cmd.reason());
        auditLogRepository.save(AuditLog.of(
                saved.getTenantId(), AGGREGATE_PO, saved.getId(),
                "CANCEL", actor.accountId(), actor.actorType(),
                "{\"status\":\"" + previous + "\"}",
                "{\"status\":\"CANCELED\",\"reason\":\""
                        + (cmd.reason() == null ? "" : cmd.reason()) + "\"}"));
        eventPublisher.publishPoCanceled(saved, cmd.reason(), actor.accountId());
        // ADR-MONO-050 D6.3 (SCM-BE-036): cancel the wms inbound expectation for a
        // warehouse-addressed replenishment PO so it is not stranded as a phantom.
        // Emitted unconditionally for any warehouse-addressed cancellation: when the
        // PO was CONFIRMED (`previous == CONFIRMED`) an inbound-expected exists and wms
        // marks it CANCELLED; when cancelled pre-CONFIRMED none was ever created, so it
        // is a harmless no-op on the wms side (wms is the authority on existence). The
        // now-reachable CONFIRMED→CANCELED transition (PoStatusMachine) is what makes
        // the meaningful post-confirm cancel drive this event.
        if (saved.isWmsWarehouseDestination()) {
            eventPublisher.publishInboundExpectedCancelled(saved);
        }
        return PurchaseOrderView.from(saved);
    }

    // ---------------- RECEIVE ASN ----------------

    @Transactional
    public AsnView receiveAsn(ReceiveAsnCommand cmd) {
        // S2 idempotency: same supplier_asn_ref → return existing.
        Optional<AdvanceShipmentNotice> existing = asnRepository.findBySupplierAsnRef(
                cmd.supplierAsnRef(), cmd.tenantId());
        if (existing.isPresent()) {
            log.info("Duplicate ASN webhook for {} — returning stored ASN", cmd.supplierAsnRef());
            return AsnView.from(existing.get());
        }

        PurchaseOrder po = loadPo(cmd.poId(), cmd.tenantId());

        AdvanceShipmentNotice asn = AdvanceShipmentNotice.create(
                UuidV7.randomString(),
                cmd.tenantId(),
                cmd.poId(),
                cmd.supplierAsnRef(),
                cmd.expectedArrivalAt()
        );
        for (ReceiveAsnCommand.AsnLine line : cmd.lines()) {
            asn.addLine(AsnLine.create(
                    UuidV7.randomString(),
                    asn.getId(),
                    cmd.tenantId(),
                    line.poLineId(),
                    line.quantityShipped()
            ));
            // Apply to the PO aggregate — may transition PARTIALLY_RECEIVED / RECEIVED
            PoStatus previous = po.applyAsnLine(line.poLineId(), line.quantityShipped());
            if (previous != null && previous != po.getStatus()) {
                recordTransition(po, previous, po.getStatus(), ActorType.SYSTEM, null,
                        "ASN " + cmd.supplierAsnRef());
            }
        }
        asn.markReceivedNow();
        AdvanceShipmentNotice savedAsn = asnRepository.save(asn);
        PurchaseOrder savedPo = poRepository.save(po);

        auditLogRepository.save(AuditLog.of(
                cmd.tenantId(), "asn", savedAsn.getId(),
                "RECEIVE", null, ActorType.SUPPLIER,
                null,
                "{\"poStatus\":\"" + savedPo.getStatus() + "\"}"));

        eventPublisher.publishAsnReceived(
                savedAsn.getId(), savedPo.getId(), cmd.tenantId(),
                cmd.supplierAsnRef(), cmd.expectedArrivalAt(),
                savedAsn.getReceivedAt());
        if (savedPo.getStatus() == PoStatus.RECEIVED) {
            eventPublisher.publishPoReceived(savedPo);
        }
        return AsnView.from(savedAsn);
    }

    // ---------------- READS ----------------

    @Transactional(readOnly = true)
    public PurchaseOrderView get(String poId, ActorContext actor) {
        return PurchaseOrderView.from(loadPo(poId, actor.tenantId()));
    }

    @Transactional(readOnly = true)
    public PageResult<PurchaseOrderView> search(ActorContext actor,
                                                PoStatus status,
                                                String supplierId,
                                                PageQuery pageQuery) {
        return poRepository.search(actor.tenantId(), status, supplierId, pageQuery)
                .map(PurchaseOrderView::from);
    }

    // ---------------- helpers ----------------

    private PurchaseOrder loadPo(String poId, String tenantId) {
        return poRepository.findById(poId, tenantId)
                .orElseThrow(() -> new PoNotFoundException("PO not found: " + poId));
    }

    /**
     * Saves a {@code po_status_history} row for a single state transition.
     * Consolidates the duplicated {@code historyRepository.save(PoStatusHistory.record(...))}
     * calls across all 6 use-case methods (S7 audit-trail invariant).
     */
    private void recordTransition(PurchaseOrder po, PoStatus previous, PoStatus next,
                                  ActorType actorType, String actorAccountId, String note) {
        historyRepository.save(PoStatusHistory.record(
                po.getId(), po.getTenantId(),
                previous, next,
                actorType, actorAccountId, note));
    }
}
