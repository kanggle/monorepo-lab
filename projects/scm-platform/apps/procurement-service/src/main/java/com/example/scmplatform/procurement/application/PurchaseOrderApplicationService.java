package com.example.scmplatform.procurement.application;

import com.example.common.id.UuidV7;
import com.example.scmplatform.procurement.application.command.AcknowledgePurchaseOrderCommand;
import com.example.scmplatform.procurement.application.command.CancelPurchaseOrderCommand;
import com.example.scmplatform.procurement.application.command.ConfirmPurchaseOrderCommand;
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
import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

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
        historyRepository.save(PoStatusHistory.record(
                saved.getId(), saved.getTenantId(),
                previous, PoStatus.SUBMITTED,
                actor.actorType(), actor.accountId(),
                "supplier ref=" + result.supplierReceiptRef()));
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
        // Idempotency: already-ACKNOWLEDGED PO with same supplier_ack_ref → no-op
        if (po.getStatus() == PoStatus.ACKNOWLEDGED || po.getStatus() == PoStatus.CONFIRMED
                || po.getStatus() == PoStatus.PARTIALLY_RECEIVED || po.getStatus() == PoStatus.RECEIVED
                || po.getStatus() == PoStatus.SETTLED || po.getStatus() == PoStatus.CLOSED) {
            log.info("Supplier ack received for PO {} already in status {} — treating as idempotent no-op",
                    po.getId(), po.getStatus());
            return PurchaseOrderView.from(po);
        }

        PoStatus previous = po.acknowledge(ActorType.SUPPLIER);
        PurchaseOrder saved = poRepository.save(po);
        historyRepository.save(PoStatusHistory.record(
                saved.getId(), saved.getTenantId(),
                previous, PoStatus.ACKNOWLEDGED,
                ActorType.SUPPLIER, null, "ack ref=" + cmd.supplierAckRef()));
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
        historyRepository.save(PoStatusHistory.record(
                saved.getId(), saved.getTenantId(),
                previous, PoStatus.CONFIRMED,
                actor.actorType(), actor.accountId(), null));
        auditLogRepository.save(AuditLog.of(
                saved.getTenantId(), AGGREGATE_PO, saved.getId(),
                "CONFIRM", actor.accountId(), actor.actorType(),
                "{\"status\":\"" + previous + "\"}",
                "{\"status\":\"CONFIRMED\"}"));
        eventPublisher.publishPoConfirmed(saved, actor.accountId());
        return PurchaseOrderView.from(saved);
    }

    // ---------------- CANCEL PO ----------------

    @Transactional
    public PurchaseOrderView cancel(CancelPurchaseOrderCommand cmd) {
        ActorContext actor = cmd.actor();
        PurchaseOrder po = loadPo(cmd.poId(), actor.tenantId());
        PoStatus previous = po.cancel(actor.actorType(), cmd.reason());
        PurchaseOrder saved = poRepository.save(po);
        historyRepository.save(PoStatusHistory.record(
                saved.getId(), saved.getTenantId(),
                previous, PoStatus.CANCELED,
                actor.actorType(), actor.accountId(), cmd.reason()));
        auditLogRepository.save(AuditLog.of(
                saved.getTenantId(), AGGREGATE_PO, saved.getId(),
                "CANCEL", actor.accountId(), actor.actorType(),
                "{\"status\":\"" + previous + "\"}",
                "{\"status\":\"CANCELED\",\"reason\":\""
                        + (cmd.reason() == null ? "" : cmd.reason()) + "\"}"));
        eventPublisher.publishPoCanceled(saved, cmd.reason(), actor.accountId());
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
                historyRepository.save(PoStatusHistory.record(
                        po.getId(), po.getTenantId(),
                        previous, po.getStatus(),
                        ActorType.SYSTEM, null,
                        "ASN " + cmd.supplierAsnRef()));
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
        PageRequest pageable = PageRequest.of(pageQuery.page(), pageQuery.size());
        Page<PurchaseOrder> page = poRepository.search(actor.tenantId(), status, supplierId, pageable);
        return new PageResult<>(
                page.getContent().stream().map(PurchaseOrderView::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    // ---------------- helpers ----------------

    private PurchaseOrder loadPo(String poId, String tenantId) {
        return poRepository.findById(poId, tenantId)
                .orElseThrow(() -> new PoNotFoundException("PO not found: " + poId));
    }
}
