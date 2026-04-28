package com.wms.inventory.domain.model;

import com.wms.inventory.domain.exception.InventoryValidationException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Atomic move between two locations in one warehouse (W1).
 *
 * <p>Authoritative reference:
 * {@code specs/services/inventory-service/domain-model.md} §5.
 *
 * <p>Immutable post-create. The use-case loads / upserts both Inventory rows
 * in deterministic id-ascending order, calls {@code transferOut} on the
 * source and {@code transferIn} on the target, persists the StockTransfer +
 * 2 Movement rows, then writes one outbox event — all in one
 * {@code @Transactional} boundary.
 */
public final class StockTransfer {

    private final UUID id;
    private final UUID warehouseId;
    private final UUID sourceLocationId;
    private final UUID targetLocationId;
    private final UUID skuId;
    private final UUID lotId;
    private final int quantity;
    private final TransferReasonCode reasonCode;
    private final String reasonNote;
    private final String actorId;
    private final String idempotencyKey;
    private final long version;
    private final Instant createdAt;
    private final String createdBy;
    private final Instant updatedAt;
    private final String updatedBy;

    private StockTransfer(UUID id, UUID warehouseId,
                          UUID sourceLocationId, UUID targetLocationId,
                          UUID skuId, UUID lotId, int quantity,
                          TransferReasonCode reasonCode, String reasonNote,
                          String actorId, String idempotencyKey,
                          long version, Instant createdAt, String createdBy,
                          Instant updatedAt, String updatedBy) {
        this.id = Objects.requireNonNull(id, "id");
        this.warehouseId = Objects.requireNonNull(warehouseId, "warehouseId");
        this.sourceLocationId = Objects.requireNonNull(sourceLocationId, "sourceLocationId");
        this.targetLocationId = Objects.requireNonNull(targetLocationId, "targetLocationId");
        if (sourceLocationId.equals(targetLocationId)) {
            throw new InventoryValidationException(
                    "StockTransfer sourceLocationId and targetLocationId must differ");
        }
        this.skuId = Objects.requireNonNull(skuId, "skuId");
        this.lotId = lotId;
        if (quantity <= 0) {
            throw new InventoryValidationException(
                    "StockTransfer quantity must be > 0, got: " + quantity);
        }
        this.quantity = quantity;
        this.reasonCode = Objects.requireNonNull(reasonCode, "reasonCode");
        if (reasonNote != null && !reasonNote.isBlank() && reasonNote.length() > 500) {
            throw new InventoryValidationException(
                    "StockTransfer reasonNote must be ≤ 500 chars");
        }
        this.reasonNote = reasonNote;
        this.actorId = Objects.requireNonNull(actorId, "actorId");
        this.idempotencyKey = idempotencyKey;
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.updatedBy = Objects.requireNonNull(updatedBy, "updatedBy");
    }

    public static StockTransfer create(UUID warehouseId,
                                       UUID sourceLocationId, UUID targetLocationId,
                                       UUID skuId, UUID lotId, int quantity,
                                       TransferReasonCode reasonCode, String reasonNote,
                                       String actorId, String idempotencyKey,
                                       Instant now) {
        return new StockTransfer(UUID.randomUUID(), warehouseId,
                sourceLocationId, targetLocationId, skuId, lotId, quantity,
                reasonCode, reasonNote, actorId, idempotencyKey,
                0, now, actorId, now, actorId);
    }

    public static StockTransfer restore(UUID id, UUID warehouseId,
                                        UUID sourceLocationId, UUID targetLocationId,
                                        UUID skuId, UUID lotId, int quantity,
                                        TransferReasonCode reasonCode, String reasonNote,
                                        String actorId, String idempotencyKey,
                                        long version, Instant createdAt, String createdBy,
                                        Instant updatedAt, String updatedBy) {
        return new StockTransfer(id, warehouseId, sourceLocationId, targetLocationId,
                skuId, lotId, quantity, reasonCode, reasonNote, actorId, idempotencyKey,
                version, createdAt, createdBy, updatedAt, updatedBy);
    }

    public UUID id() { return id; }
    public UUID warehouseId() { return warehouseId; }
    public UUID sourceLocationId() { return sourceLocationId; }
    public UUID targetLocationId() { return targetLocationId; }
    public UUID skuId() { return skuId; }
    public UUID lotId() { return lotId; }
    public int quantity() { return quantity; }
    public TransferReasonCode reasonCode() { return reasonCode; }
    public String reasonNote() { return reasonNote; }
    public String actorId() { return actorId; }
    public String idempotencyKey() { return idempotencyKey; }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }
    public String createdBy() { return createdBy; }
    public Instant updatedAt() { return updatedAt; }
    public String updatedBy() { return updatedBy; }
}
