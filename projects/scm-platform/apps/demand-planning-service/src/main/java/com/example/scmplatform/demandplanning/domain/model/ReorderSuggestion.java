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
 *       (e.g. {@code "WH-SEOUL-01"}) carried additively on the alert. This is the
 *       value that flows to the PO {@code destinationWarehouseId} so wms's
 *       {@code findWarehouseByCode} resolves it. NULL for BATCH-sourced suggestions
 *       (the IVS read-model carries no warehouse code — see {@code SweepReorderUseCase};
 *       such POs simply do not emit a wms inbound-expected, fail-closed).</li>
 * </ul>
 * {@code supplierId} is likewise a supplier <em>code</em> (String) — the value wms
 * resolves via {@code findPartnerByCode}, sourced from {@code sku_supplier_map.supplier_id}.
 */
public class ReorderSuggestion {

    private final UUID id;
    private final String skuCode;
    private final UUID warehouseId;          // dedup-key dimension (wms locationId)
    private final String warehouseCode;      // ADR-050 D9: warehouse CODE → PO destination (nullable for BATCH)
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
                               String supplierId, int suggestedQty, SuggestionStatus status,
                               SuggestionSource source, UUID triggerEventId, Integer triggerAvailableQty,
                               UUID materializedPoId, String tenantId, int version,
                               Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.skuCode = Objects.requireNonNull(skuCode, "skuCode");
        this.warehouseId = Objects.requireNonNull(warehouseId, "warehouseId");
        this.warehouseCode = warehouseCode; // nullable — BATCH suggestions carry none
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
     */
    public static ReorderSuggestion raiseFromAlert(UUID id, String skuCode, UUID warehouseId,
                                                    String warehouseCode, String supplierId, int suggestedQty,
                                                    UUID triggerEventId, int triggerAvailableQty,
                                                    String tenantId, Instant now) {
        return new ReorderSuggestion(id, skuCode, warehouseId, warehouseCode, supplierId, suggestedQty,
                SuggestionStatus.SUGGESTED, SuggestionSource.ALERT,
                triggerEventId, triggerAvailableQty, null, tenantId, 0, now, now);
    }

    /**
     * Factory — raise a new SUGGESTED reorder suggestion from the batch sweep.
     * The IVS read-model carries no warehouse code (ADR-050 D9 batch-path limitation),
     * so {@code warehouseCode} is null — a materialized PO from a BATCH suggestion does
     * not address a wms inbound-expected (fail-closed; follow-up: IVS carry warehouseCode).
     */
    public static ReorderSuggestion raiseFromBatch(UUID id, String skuCode, UUID warehouseId,
                                                    String supplierId, int suggestedQty,
                                                    int triggerAvailableQty,
                                                    String tenantId, Instant now) {
        return new ReorderSuggestion(id, skuCode, warehouseId, null, supplierId, suggestedQty,
                SuggestionStatus.SUGGESTED, SuggestionSource.BATCH,
                null, triggerAvailableQty, null, tenantId, 0, now, now);
    }

    /**
     * Reconstruct from persistence.
     */
    public static ReorderSuggestion reconstitute(UUID id, String skuCode, UUID warehouseId,
                                                  String warehouseCode, String supplierId, int suggestedQty,
                                                  SuggestionStatus status, SuggestionSource source,
                                                  UUID triggerEventId, Integer triggerAvailableQty,
                                                  UUID materializedPoId, String tenantId, int version,
                                                  Instant createdAt, Instant updatedAt) {
        return new ReorderSuggestion(id, skuCode, warehouseId, warehouseCode, supplierId, suggestedQty,
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
