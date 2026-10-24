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
import java.time.LocalDate;
import java.time.ZoneOffset;
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

    /**
     * IAM {@code sub} claim of the actor who drafted the PO. Width 255 (not 36):
     * a human operator's {@code sub} is a UUID/email (&lt;=36) but scm's
     * client-credentials machine caller carries {@code sub == client_id} (e.g. the
     * 37-char {@code scm-platform-internal-services-client}), which overflows a
     * VARCHAR(36). Kept as the true, verifiable {@code sub} — never truncated
     * (TASK-SCM-BE-050 / iam-integration.md Edge Case E1).
     */
    @Column(name = "buyer_account_id", length = 255, nullable = false)
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

    /**
     * wms inbound-expected addressing (ADR-MONO-050 D3). The warehouse that
     * seeded the reorder suggestion — carried from demand-planning at
     * materialization. NULL for operator-authored POs, which are never turned
     * into a wms inbound expectation (fail-closed: no warehouse → no emit).
     */
    @Column(name = "destination_warehouse_id", length = 36)
    private String destinationWarehouseId;

    /**
     * v1 stores only {@code WMS_WAREHOUSE}. The column exists so a future
     * {@code THIRD_PARTY_LOGISTICS} destination (ADR-MONO-050 D4) is a data fact,
     * not a schema change — the producer-side 3PL filter reads it.
     */
    @Column(name = "destination_node_type", length = 30)
    private String destinationNodeType;

    /**
     * {@code sku_supplier_map.lead_time_days} carried at materialization
     * (ADR-MONO-050 D1). {@code expectedArrivalDate = confirmedAt + leadTimeDays}.
     * NULL for operator-authored POs.
     */
    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

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

    /**
     * v1 destination node type accepted for wms inbound-expected emission
     * (ADR-MONO-050 D4). Only own warehouses are turned into wms expectations.
     */
    public static final String NODE_TYPE_WMS_WAREHOUSE = "WMS_WAREHOUSE";

    /**
     * Destination node type addressed to a third-party-logistics node (ADR-MONO-055
     * §D4). A 3PL-addressed replenishment PO is honoured by recording an expectation
     * against the 3PL node in the scm-internal sink (inventory-visibility), NOT by a
     * wms inbound-expected event (wms does not operate a 3PL's WMS — ADR-MONO-050 §D4).
     */
    public static final String NODE_TYPE_THIRD_PARTY_LOGISTICS = "THIRD_PARTY_LOGISTICS";

    public static PurchaseOrder createDraft(String id,
                                            String tenantId,
                                            String poNumber,
                                            String supplierId,
                                            String buyerAccountId,
                                            String currency) {
        return newDraft(id, tenantId, poNumber, supplierId, buyerAccountId, currency,
                PoOrigin.OPERATOR, null, null, null, null);
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
                                                          String sourceSuggestionId,
                                                          String destinationWarehouseId,
                                                          String destinationNodeType,
                                                          Integer leadTimeDays) {
        Objects.requireNonNull(sourceSuggestionId, "sourceSuggestionId");
        return newDraft(id, tenantId, poNumber, supplierId, buyerAccountId, currency,
                PoOrigin.DEMAND_PLANNING, sourceSuggestionId,
                destinationWarehouseId, destinationNodeType, leadTimeDays);
    }

    private static PurchaseOrder newDraft(String id,
                                          String tenantId,
                                          String poNumber,
                                          String supplierId,
                                          String buyerAccountId,
                                          String currency,
                                          PoOrigin origin,
                                          String sourceSuggestionId,
                                          String destinationWarehouseId,
                                          String destinationNodeType,
                                          Integer leadTimeDays) {
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
        po.destinationWarehouseId = destinationWarehouseId;
        po.destinationNodeType = destinationNodeType;
        po.leadTimeDays = leadTimeDays;
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
     * Apply one whole ASN: add each line's shipped quantity to its PO line and
     * move the PO to {@code PARTIALLY_RECEIVED} (something still outstanding) or
     * {@code RECEIVED} (every line complete) — through {@link PoStatusMachine}'s
     * {@link ActorType#SYSTEM} rules, so only a {@code CONFIRMED} or
     * {@code PARTIALLY_RECEIVED} PO can take a receipt (TASK-SCM-BE-063).
     *
     * <p><strong>All-or-nothing.</strong> Every check runs before anything is
     * mutated, in this order, so a rejected ASN leaves lines and status untouched:
     * <ol>
     *   <li>each line exists on this PO and its quantity is positive
     *       ({@link IllegalArgumentException}, as before);</li>
     *   <li>no line would exceed its ordered quantity, counting repeats of the
     *       same line within the ASN ({@link AsnOverreceiptException} — checked
     *       first, so an ASN on an already {@code RECEIVED} PO still answers
     *       {@code ASN_OVERRECEIPT}, unchanged);</li>
     *   <li>the resulting status change is allowed for {@code SYSTEM}
     *       ({@link com.example.scmplatform.procurement.domain.error.PoStatusTransitionInvalidException}
     *       — e.g. {@code CANCELED → RECEIVED}, {@code SUBMITTED → PARTIALLY_RECEIVED}).</li>
     * </ol>
     *
     * <p>The transition is evaluated once for the whole ASN, so a multi-line ASN
     * that completes a {@code CONFIRMED} PO is one {@code CONFIRMED → RECEIVED}
     * step, not a {@code PARTIALLY_RECEIVED} the PO never committed in between.
     *
     * @return the status before the ASN when it changed, otherwise {@code null}
     *         (a further partial receipt on a {@code PARTIALLY_RECEIVED} PO)
     */
    public PoStatus applyAsn(List<AsnLineReceipt> receipts) {
        Objects.requireNonNull(receipts, "receipts");
        if (receipts.isEmpty()) {
            throw new IllegalArgumentException("ASN for PO " + id + " has no lines");
        }

        // 1-2. Validate every line against its projected cumulative quantity.
        java.util.Map<String, BigDecimal> projected = new java.util.LinkedHashMap<>();
        java.util.Map<String, PurchaseOrderLine> byId = new java.util.HashMap<>();
        for (AsnLineReceipt receipt : receipts) {
            PurchaseOrderLine line = findLine(receipt.poLineId());
            byId.put(line.getId(), line);
            BigDecimal delta = receipt.quantity();
            BigDecimal pendingDelta = projected.containsKey(line.getId())
                    ? projected.get(line.getId()).subtract(line.getReceivedQuantity())
                    : BigDecimal.ZERO;
            if (delta == null || delta.signum() <= 0) {
                throw new IllegalArgumentException("delta must be positive: " + delta);
            }
            try {
                projected.put(line.getId(), line.receivedAfter(pendingDelta.add(delta)));
            } catch (IllegalArgumentException e) {
                if (e.getMessage() != null && e.getMessage().startsWith("ASN_OVERRECEIPT")) {
                    throw new AsnOverreceiptException(e.getMessage());
                }
                throw e;
            }
        }

        // 3. The status this ASN would produce, checked against the state machine.
        boolean allReceived = this.lines.stream().allMatch(l -> {
            BigDecimal received = projected.getOrDefault(l.getId(), l.getReceivedQuantity());
            return received.compareTo(l.getQuantity()) >= 0;
        });
        PoStatus target = allReceived ? PoStatus.RECEIVED : PoStatus.PARTIALLY_RECEIVED;
        PoStatus previous = this.status;
        if (target != previous) {
            PoStatusMachine.ensureTransitionAllowed(previous, target, ActorType.SYSTEM);
        }
        // target == previous is reachable only as PARTIALLY_RECEIVED → PARTIALLY_RECEIVED
        // (a RECEIVED PO has every line full, so any positive quantity failed step 2).

        // Mutate only after every check passed.
        for (java.util.Map.Entry<String, BigDecimal> e : projected.entrySet()) {
            PurchaseOrderLine line = byId.get(e.getKey());
            line.addReceived(e.getValue().subtract(line.getReceivedQuantity()));
        }
        if (target == previous) {
            return null;
        }
        this.status = target;
        this.updatedAt = Instant.now();
        return previous;
    }

    private PurchaseOrderLine findLine(String poLineId) {
        return this.lines.stream()
                .filter(l -> l.getId().equals(poLineId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "PO line not found: " + poLineId + " in PO " + id));
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

    /**
     * True when this PO is a warehouse-addressed replenishment order whose
     * destination is an own ({@code WMS_WAREHOUSE}) warehouse (ADR-MONO-050
     * D3/D4). Only such POs are turned into a wms inbound expectation:
     * <ul>
     *   <li>operator-authored POs carry no destination → excluded (fail-closed,
     *       Failure Scenario B — never guess a warehouse);</li>
     *   <li>a {@code THIRD_PARTY_LOGISTICS} destination → excluded (D4
     *       producer-side filter — wms does not operate 3PL nodes).</li>
     * </ul>
     */
    public boolean isWmsWarehouseDestination() {
        return NODE_TYPE_WMS_WAREHOUSE.equals(destinationNodeType)
                && destinationWarehouseId != null
                && !destinationWarehouseId.isBlank();
    }

    /**
     * True when this PO is a replenishment order addressed to a
     * {@code THIRD_PARTY_LOGISTICS} node (ADR-MONO-055 §D4 / TASK-SCM-BE-049). Such a
     * PO is honoured by recording an expectation against the 3PL node in the
     * scm-internal sink (inventory-visibility), not by a wms inbound-expected event.
     *
     * <p>{@code destinationWarehouseId} carries the addressed inventory-visibility
     * node id for a 3PL destination (the field is the generic "destination node
     * identifier" — a warehouse business code for a {@code WMS_WAREHOUSE}, the
     * inventory-visibility node id for a {@code THIRD_PARTY_LOGISTICS}). A 3PL PO with
     * no resolvable node id is excluded (fail-closed — the sink must never record an
     * un-addressable expectation).
     */
    public boolean isThirdPartyLogisticsDestination() {
        return NODE_TYPE_THIRD_PARTY_LOGISTICS.equals(destinationNodeType)
                && destinationWarehouseId != null
                && !destinationWarehouseId.isBlank();
    }

    /**
     * Expected arrival date for the wms inbound expectation (ADR-MONO-050 D1):
     * the confirmation date (UTC) plus {@code lead_time_days}. Computed at
     * confirm time so the horizon is relative to supplier acknowledgement.
     * Returns {@code null} when the lead time is unknown — the caller treats a
     * null as a fail-closed signal (do not emit a wrong horizon).
     */
    public LocalDate expectedArrivalDate() {
        if (leadTimeDays == null) {
            return null;
        }
        Instant basis = confirmedAt != null ? confirmedAt : Instant.now();
        return basis.atZone(ZoneOffset.UTC).toLocalDate().plusDays(leadTimeDays);
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
