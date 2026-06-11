package com.example.scmplatform.procurement.domain.po;

import com.example.scmplatform.procurement.domain.error.AsnOverreceiptException;
import com.example.scmplatform.procurement.domain.error.PoAlreadyConfirmedException;
import com.example.scmplatform.procurement.domain.error.PoQuantityExceededException;
import com.example.scmplatform.procurement.domain.po.status.ActorType;
import com.example.scmplatform.procurement.domain.po.status.PoStatus;
import com.example.scmplatform.procurement.domain.po.status.PoStatusMachine;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Purchase Order aggregate root.
 *
 * <p>Multi-tenant: every PO carries non-nullable {@code tenantId}; cross-tenant
 * reads are blocked at the repository layer by always passing tenant_id in
 * {@code WHERE}. State transitions flow through {@link PoStatusMachine} and
 * never through direct setter mutation — there are no setters.
 *
 * <p>Lines are an aggregate-internal collection. The repository adapter loads
 * them explicitly (see {@link com.example.scmplatform.procurement.domain.po.repository.PurchaseOrderRepository#findById})
 * and saves them through the same adapter, keeping aggregate consistency in
 * the application service's transaction.
 */
@Entity
@Table(name = "purchase_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseOrder {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "po_number", length = 40, nullable = false)
    private String poNumber;

    @Column(name = "supplier_id", length = 36, nullable = false)
    private String supplierId;

    @Column(name = "buyer_account_id", length = 36, nullable = false)
    private String buyerAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private PoStatus status;

    /**
     * Provenance (ADR-MONO-027 D5). Defaults to OPERATOR for the operator-authored
     * flow; DEMAND_PLANNING for POs materialized from a reorder suggestion.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "origin", length = 30, nullable = false)
    private PoOrigin origin;

    /**
     * Cross-service idempotency key (S2): the demand-planning reorder_suggestion
     * this PO was materialized from. NULL for operator-authored POs. A partial
     * UNIQUE (tenant_id, source_suggestion_id) enforces at-most-one PO per
     * suggestion at the DB layer.
     */
    @Column(name = "source_suggestion_id", length = 36)
    private String sourceSuggestionId;

    @Embedded
    private Money totalAmount;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Column(name = "cancellation_reason", length = 200)
    private String cancellationReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * Aggregate-internal line collection. Marked {@code @Transient} because
     * the persistence adapter loads / saves lines through a dedicated JPA
     * repository (avoids Hibernate's lazy-init footguns in hexagonal layout).
     */
    @Transient
    private final List<PurchaseOrderLine> lines = new ArrayList<>();

    public static PurchaseOrder createDraft(String id,
                                            String tenantId,
                                            String poNumber,
                                            String supplierId,
                                            String buyerAccountId,
                                            String currency) {
        return newDraft(id, tenantId, poNumber, supplierId, buyerAccountId, currency,
                PoOrigin.OPERATOR, null);
    }

    /**
     * Create a DRAFT PO materialized from an approved reorder suggestion
     * (ADR-MONO-027 D5). Identical lifecycle to {@link #createDraft}; differs only
     * in provenance ({@code origin=DEMAND_PLANNING}) and the cross-service
     * idempotency key {@code sourceSuggestionId}. The supplier is NOT FK-validated
     * here (FK-free cross-service convention) — the operator validates the
     * supplier reference at DRAFT review.
     */
    public static PurchaseOrder createDraftFromSuggestion(String id,
                                                          String tenantId,
                                                          String poNumber,
                                                          String supplierId,
                                                          String buyerAccountId,
                                                          String currency,
                                                          String sourceSuggestionId) {
        Objects.requireNonNull(sourceSuggestionId, "sourceSuggestionId");
        return newDraft(id, tenantId, poNumber, supplierId, buyerAccountId, currency,
                PoOrigin.DEMAND_PLANNING, sourceSuggestionId);
    }

    private static PurchaseOrder newDraft(String id,
                                          String tenantId,
                                          String poNumber,
                                          String supplierId,
                                          String buyerAccountId,
                                          String currency,
                                          PoOrigin origin,
                                          String sourceSuggestionId) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(poNumber, "poNumber");
        Objects.requireNonNull(supplierId, "supplierId");
        Objects.requireNonNull(buyerAccountId, "buyerAccountId");

        PurchaseOrder po = new PurchaseOrder();
        po.id = id;
        po.tenantId = tenantId;
        po.poNumber = poNumber;
        po.supplierId = supplierId;
        po.buyerAccountId = buyerAccountId;
        po.status = PoStatus.DRAFT;
        po.origin = origin;
        po.sourceSuggestionId = sourceSuggestionId;
        po.totalAmount = Money.zero(currency);
        Instant now = Instant.now();
        po.createdAt = now;
        po.updatedAt = now;
        // Leave version null so Spring Data JPA save() detects this as a new
        // entity and calls persist() instead of merge().
        return po;
    }

    /**
     * Add a line. Only valid while in DRAFT — once SUBMITTED the lines are
     * immutable from the buyer's perspective.
     */
    public void addLine(PurchaseOrderLine line) {
        if (status != PoStatus.DRAFT) {
            throw new PoAlreadyConfirmedException(
                    "Cannot add lines to PO " + id + " in status " + status);
        }
        // Reject duplicate line_no — the DB UNIQUE will catch it eventually,
        // but failing in-memory gives a cleaner error envelope.
        for (PurchaseOrderLine existing : this.lines) {
            if (existing.getLineNo() == line.getLineNo()) {
                throw new IllegalArgumentException(
                        "Duplicate line_no " + line.getLineNo() + " on PO " + id);
            }
        }
        this.lines.add(line);
        recomputeTotal();
        this.updatedAt = Instant.now();
    }

    /** Replace the in-memory line collection (used by repository on load). */
    public void hydrateLines(List<PurchaseOrderLine> hydrated) {
        this.lines.clear();
        this.lines.addAll(hydrated);
    }

    public List<PurchaseOrderLine> linesView() {
        return List.copyOf(this.lines);
    }

    public PoStatus submit(ActorType actor) {
        if (this.lines.isEmpty()) {
            throw new IllegalStateException("Cannot submit PO " + id + " with no lines");
        }
        return transition(PoStatus.SUBMITTED, actor, now -> this.submittedAt = now);
    }

    public PoStatus acknowledge(ActorType actor) {
        return transition(PoStatus.ACKNOWLEDGED, actor, now -> this.acknowledgedAt = now);
    }

    public PoStatus confirm(ActorType actor) {
        return transition(PoStatus.CONFIRMED, actor, now -> this.confirmedAt = now);
    }

    public PoStatus cancel(ActorType actor, String reason) {
        return transition(PoStatus.CANCELED, actor, now -> {
            this.canceledAt = now;
            this.cancellationReason = reason;
        });
    }

    /**
     * Apply ASN line: increase received quantity for the matched PO line and
     * advance PO status (CONFIRMED → PARTIALLY_RECEIVED → RECEIVED) when
     * appropriate. Returns the previous PO status for audit, or {@code null}
     * if status did not change.
     */
    public PoStatus applyAsnLine(String poLineId, BigDecimal deltaQuantity) {
        PurchaseOrderLine target = this.lines.stream()
                .filter(l -> l.getId().equals(poLineId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "PO line not found: " + poLineId + " in PO " + id));
        try {
            target.addReceived(deltaQuantity);
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("ASN_OVERRECEIPT")) {
                throw new AsnOverreceiptException(e.getMessage());
            }
            throw e;
        }

        boolean allReceived = this.lines.stream().allMatch(PurchaseOrderLine::isFullyReceived);
        boolean anyReceived = this.lines.stream()
                .anyMatch(l -> l.getReceivedQuantity().signum() > 0);

        PoStatus previous = this.status;
        if (allReceived && status == PoStatus.RECEIVED) {
            return null;
        }
        if (allReceived) {
            this.status = PoStatus.RECEIVED;
            this.updatedAt = Instant.now();
            return previous;
        }
        if (anyReceived && status == PoStatus.CONFIRMED) {
            this.status = PoStatus.PARTIALLY_RECEIVED;
            this.updatedAt = Instant.now();
            return previous;
        }
        return null;
    }

    /**
     * Validate that the supplier ack quantity does not exceed the PO line
     * quantity. Pure invariant check — used by AcknowledgePurchaseOrderUseCase.
     */
    public void validateAckQuantity(String poLineId, BigDecimal ackQuantity) {
        PurchaseOrderLine line = this.lines.stream()
                .filter(l -> l.getId().equals(poLineId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "PO line not found: " + poLineId));
        if (ackQuantity == null || ackQuantity.signum() <= 0) {
            throw new IllegalArgumentException("ackQuantity must be positive");
        }
        if (ackQuantity.compareTo(line.getQuantity()) > 0) {
            throw new PoQuantityExceededException(
                    "Ack quantity " + ackQuantity + " exceeds ordered " + line.getQuantity()
                            + " on line " + poLineId);
        }
    }

    private PoStatus transition(PoStatus target, ActorType actor, java.util.function.Consumer<Instant> sideEffect) {
        PoStatus previous = this.status;
        PoStatusMachine.ensureTransitionAllowed(previous, target, actor);
        this.status = target;
        Instant now = Instant.now();
        sideEffect.accept(now);
        this.updatedAt = now;
        return previous;
    }

    private void recomputeTotal() {
        BigDecimal sum = BigDecimal.ZERO;
        String currency = totalAmount.getCurrency();
        for (PurchaseOrderLine l : this.lines) {
            sum = sum.add(l.lineTotal());
        }
        this.totalAmount = Money.of(sum, currency);
    }
}
