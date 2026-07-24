package com.example.scmplatform.demandplanning.domain.model;

import com.example.scmplatform.demandplanning.domain.error.InvalidSuggestionStateException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Reorder suggestion aggregate (ADR-027 data-model.md).
 * Status machine: SUGGESTED → APPROVED → MATERIALIZED / DISMISSED.
 * DISMISSED can also be reached from SUGGESTED directly.
 * MATERIALIZED and DISMISSED are terminal.
 *
 * <p><strong>ADR-MONO-050 D9 (Option A — cross-service identifiers are CODES).</strong>
 * Two warehouse dimensions are carried, deliberately split:
 * <ul>
 *   <li>{@code warehouseId} (UUID) — the wms {@code locationId}. Kept solely as the
 *       stable dedup-key dimension {@code (tenantId, skuCode, warehouseId)} (the
 *       open-suggestion guard, D6). Never emitted downstream.</li>
 *   <li>{@code warehouseCode} (String) — the wms warehouse <em>business code</em>
 *       (e.g. {@code "WH-SEOUL-01"}). This is the value that flows to the PO
 *       {@code destinationWarehouseId} so wms's {@code findWarehouseByCode} resolves it.
 *       Both sources now carry it: the ALERT path reads it off the alert payload, and the
 *       BATCH path reads it off the IVS node read-model, which learns it from wms's
 *       inventory mutation events (TASK-SCM-BE-037). Still <b>nullable</b> on either path —
 *       wms resolves the code best-effort — and a null code never blocks the suggestion;
 *       it only means the materialized PO emits no wms inbound-expected (fail-closed).</li>
 * </ul>
 * {@code supplierId} is likewise a supplier <em>code</em> (String) — the value wms
 * resolves via {@code findPartnerByCode}, sourced from {@code sku_supplier_map.supplier_id}.
 */
public class ReorderSuggestion {

    /**
     * Default destination node type (ADR-MONO-055 §D2/§D3). A suggestion with no explicit
     * node type — the ALERT path (wms-only), a pre-055 in-flight persisted row, or a BATCH
     * candidate whose node type IVS could not resolve — is treated as a wms warehouse, the
     * pre-055 contract. Normalised in the constructor so {@link #getDestinationNodeType()}
     * is never null.
     */
    public static final String NODE_TYPE_WMS_WAREHOUSE = "WMS_WAREHOUSE";

    private final UUID id;
    private final String skuCode;
    private final UUID warehouseId;          // dedup-key dimension (wms locationId)
    private final String warehouseCode;      // ADR-050 D9: warehouse CODE → PO destination (nullable on both paths)
    private final String destinationNodeType; // ADR-055 §D2/§D3: node TYPE → PO destinationNodeType (never null; defaults WMS_WAREHOUSE)
    private final String supplierId;         // ADR-050 D9: supplier CODE (was UUID)
    private final int suggestedQty;
    private SuggestionStatus status;
    private final SuggestionSource source;
    private final UUID triggerEventId;        // null for BATCH source
    private final Integer triggerAvailableQty;
    private UUID materializedPoId;           // set on MATERIALIZED
    private final String tenantId;
    private int version;
    private final Instant createdAt;
    private Instant updatedAt;

    private ReorderSuggestion(UUID id, String skuCode, UUID warehouseId, String warehouseCode,
                               String destinationNodeType, String supplierId, int suggestedQty,
                               SuggestionStatus status,
                               SuggestionSource source, UUID triggerEventId, Integer triggerAvailableQty,
                               UUID materializedPoId, String tenantId, int version,
                               Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.skuCode = Objects.requireNonNull(skuCode, "skuCode");
        this.warehouseId = Objects.requireNonNull(warehouseId, "warehouseId");
        this.warehouseCode = warehouseCode; // nullable — wms resolves the code best-effort
        // ADR-055 §D2/§D3: normalise null/blank → WMS_WAREHOUSE (backward compat) so the
        // getter is never null and the ALERT path + pre-055 rows keep their wms behaviour.
        this.destinationNodeType = (destinationNodeType != null && !destinationNodeType.isBlank())
                ? destinationNodeType : NODE_TYPE_WMS_WAREHOUSE;
        this.supplierId = Objects.requireNonNull(supplierId, "supplierId");
        this.suggestedQty = suggestedQty;
        this.status = Objects.requireNonNull(status, "status");
        this.source = Objects.requireNonNull(source, "source");
        this.triggerEventId = triggerEventId;
        this.triggerAvailableQty = triggerAvailableQty;
        this.materializedPoId = materializedPoId;
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /**
     * Factory — raise a new SUGGESTED reorder suggestion from an alert.
     * Carries the additive warehouse CODE (ADR-050 D9) that flows to the PO destination.
     *
     * <p>The alert path is wms-only (ADR-MONO-055 §D2 — {@code wms.inventory.alert.v1}
     * fires only for wms nodes), so the destination node type is always
     * {@code WMS_WAREHOUSE}. The signature is deliberately unchanged: 3PL replenishment
     * rides the batch/snapshot path only ({@link #raiseFromBatch}).
     */
    public static ReorderSuggestion raiseFromAlert(UUID id, String skuCode, UUID warehouseId,
                                                    String warehouseCode, String supplierId, int suggestedQty,
                                                    UUID triggerEventId, int triggerAvailableQty,
                                                    String tenantId, Instant now) {
        return new ReorderSuggestion(id, skuCode, warehouseId, warehouseCode,
                NODE_TYPE_WMS_WAREHOUSE, supplierId, suggestedQty,
                SuggestionStatus.SUGGESTED, SuggestionSource.ALERT,
                triggerEventId, triggerAvailableQty, null, tenantId, 0, now, now);
    }

    /**
     * Factory — raise a new SUGGESTED reorder suggestion from the batch sweep.
     *
     * <p>Since TASK-SCM-BE-037 the IVS read-model carries the warehouse CODE (learned from
     * wms's inventory mutation events), so a BATCH suggestion addresses its PO by code
     * exactly like the ALERT path. {@code warehouseCode} remains <b>nullable</b> — wms
     * resolves it best-effort, and IVS may not have learned one for this node yet; such a
     * PO simply emits no wms inbound-expected (fail-closed, no uuid leak).
     *
     * <p>ADR-MONO-055 §D2/§D3: the batch path now carries the node's {@code nodeType} from
     * the IVS read-model, so a below-reorder {@code THIRD_PARTY_LOGISTICS} node drafts a PO
     * addressed to that 3PL node. A null {@code destinationNodeType} normalises to
     * {@code WMS_WAREHOUSE} in the constructor (backward compat).
     */
    public static ReorderSuggestion raiseFromBatch(UUID id, String skuCode, UUID warehouseId,
                                                    String warehouseCode, String destinationNodeType,
                                                    String supplierId, int suggestedQty,
                                                    int triggerAvailableQty,
                                                    String tenantId, Instant now) {
        return new ReorderSuggestion(id, skuCode, warehouseId, warehouseCode,
                destinationNodeType, supplierId, suggestedQty,
                SuggestionStatus.SUGGESTED, SuggestionSource.BATCH,
                null, triggerAvailableQty, null, tenantId, 0, now, now);
    }

    /**
     * Reconstruct from persistence. A null {@code destinationNodeType} (a pre-ADR-055
     * in-flight row) normalises to {@code WMS_WAREHOUSE} in the constructor (backward compat).
     */
    public static ReorderSuggestion reconstitute(UUID id, String skuCode, UUID warehouseId,
                                                  String warehouseCode, String destinationNodeType,
                                                  String supplierId, int suggestedQty,
                                                  SuggestionStatus status, SuggestionSource source,
                                                  UUID triggerEventId, Integer triggerAvailableQty,
                                                  UUID materializedPoId, String tenantId, int version,
                                                  Instant createdAt, Instant updatedAt) {
        return new ReorderSuggestion(id, skuCode, warehouseId, warehouseCode,
                destinationNodeType, supplierId, suggestedQty,
                status, source, triggerEventId, triggerAvailableQty,
                materializedPoId, tenantId, version, createdAt, updatedAt);
    }

    /**
     * Transition to DISMISSED. Valid from SUGGESTED or APPROVED.
     */
    public void dismiss(Instant now) {
        if (!status.canDismiss()) {
            throw new InvalidSuggestionStateException(
                    "Cannot dismiss suggestion in status " + status);
        }
        this.status = SuggestionStatus.DISMISSED;
        this.updatedAt = now;
        this.version++;
    }

    /**
     * Transition to APPROVED. Valid only from SUGGESTED.
     */
    public void approve(Instant now) {
        if (!status.canApprove()) {
            throw new InvalidSuggestionStateException(
                    "Cannot approve suggestion in status " + status);
        }
        this.status = SuggestionStatus.APPROVED;
        this.updatedAt = now;
        this.version++;
    }

    /**
     * Transition to MATERIALIZED with the procurement DRAFT PO id (D5).
     */
    public void materialize(UUID poId, Instant now) {
        if (status != SuggestionStatus.APPROVED) {
            throw new InvalidSuggestionStateException(
                    "Cannot materialize suggestion in status " + status);
        }
        this.materializedPoId = Objects.requireNonNull(poId, "poId");
        this.status = SuggestionStatus.MATERIALIZED;
        this.updatedAt = now;
        this.version++;
    }

    public UUID getId() { return id; }
    public String getSkuCode() { return skuCode; }
    public UUID getWarehouseId() { return warehouseId; }
    public String getWarehouseCode() { return warehouseCode; }
    /** Destination node TYPE (ADR-MONO-055 §D2/§D3) — never null; defaults {@code WMS_WAREHOUSE}. */
    public String getDestinationNodeType() { return destinationNodeType; }
    public String getSupplierId() { return supplierId; }
    public int getSuggestedQty() { return suggestedQty; }
    public SuggestionStatus getStatus() { return status; }
    public SuggestionSource getSource() { return source; }
    public UUID getTriggerEventId() { return triggerEventId; }
    public Integer getTriggerAvailableQty() { return triggerAvailableQty; }
    public UUID getMaterializedPoId() { return materializedPoId; }
    public String getTenantId() { return tenantId; }
    public int getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
