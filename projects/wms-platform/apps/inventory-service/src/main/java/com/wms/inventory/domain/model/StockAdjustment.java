package com.wms.inventory.domain.model;

import com.wms.inventory.domain.exception.InventoryValidationException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Reason-recorded manual correction of a single Inventory bucket.
 *
 * <p>Authoritative reference:
 * {@code specs/services/inventory-service/domain-model.md} §4.
 *
 * <p>Immutable post-create. Reverse-corrections are modelled as a new
 * {@code ADJUSTMENT_RECLASSIFY} adjustment that references the original via
 * {@code reasonNote}.
 */
public final class StockAdjustment {

    private static final Set<ReasonCode> ALLOWED_REASONS = EnumSet.of(
            ReasonCode.ADJUSTMENT_CYCLE_COUNT,
            ReasonCode.ADJUSTMENT_DAMAGE,
            ReasonCode.ADJUSTMENT_LOSS,
            ReasonCode.ADJUSTMENT_FOUND,
            ReasonCode.ADJUSTMENT_RECLASSIFY,
            ReasonCode.DAMAGE_WRITE_OFF);

    private final UUID id;
    private final UUID inventoryId;
    private final Bucket bucket;
    private final int delta;
    private final ReasonCode reasonCode;
    private final String reasonNote;
    private final String actorId;
    private final String idempotencyKey;
    private final long version;
    private final Instant createdAt;
    private final String createdBy;
    private final Instant updatedAt;
    private final String updatedBy;

    private StockAdjustment(UUID id, UUID inventoryId, Bucket bucket, int delta,
                            ReasonCode reasonCode, String reasonNote,
                            String actorId, String idempotencyKey,
                            long version, Instant createdAt, String createdBy,
                            Instant updatedAt, String updatedBy) {
        this.id = Objects.requireNonNull(id, "id");
        this.inventoryId = Objects.requireNonNull(inventoryId, "inventoryId");
        this.bucket = Objects.requireNonNull(bucket, "bucket");
        if (delta == 0) {
            throw new InventoryValidationException("StockAdjustment delta must be non-zero");
        }
        this.delta = delta;
        this.reasonCode = Objects.requireNonNull(reasonCode, "reasonCode");
        if (!ALLOWED_REASONS.contains(reasonCode)) {
            throw new InventoryValidationException(
                    "StockAdjustment reasonCode not allowed: " + reasonCode);
        }
        Objects.requireNonNull(reasonNote, "reasonNote");
        if (reasonNote.trim().length() < 3) {
            throw new InventoryValidationException(
                    "StockAdjustment reasonNote must be at least 3 non-blank characters");
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

    /** Factory used by the application service for a fresh adjustment. */
    public static StockAdjustment create(UUID inventoryId, Bucket bucket, int delta,
                                         ReasonCode reasonCode, String reasonNote,
                                         String actorId, String idempotencyKey,
                                         Instant now) {
        return new StockAdjustment(UUID.randomUUID(), inventoryId, bucket, delta,
                reasonCode, reasonNote, actorId, idempotencyKey,
                0, now, actorId, now, actorId);
    }

    /** Reconstruct from persistence. */
    public static StockAdjustment restore(UUID id, UUID inventoryId, Bucket bucket, int delta,
                                          ReasonCode reasonCode, String reasonNote,
                                          String actorId, String idempotencyKey,
                                          long version, Instant createdAt, String createdBy,
                                          Instant updatedAt, String updatedBy) {
        return new StockAdjustment(id, inventoryId, bucket, delta,
                reasonCode, reasonNote, actorId, idempotencyKey,
                version, createdAt, createdBy, updatedAt, updatedBy);
    }

    public UUID id() { return id; }
    public UUID inventoryId() { return inventoryId; }
    public Bucket bucket() { return bucket; }
    public int delta() { return delta; }
    public ReasonCode reasonCode() { return reasonCode; }
    public String reasonNote() { return reasonNote; }
    public String actorId() { return actorId; }
    public String idempotencyKey() { return idempotencyKey; }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }
    public String createdBy() { return createdBy; }
    public Instant updatedAt() { return updatedAt; }
    public String updatedBy() { return updatedBy; }
}
