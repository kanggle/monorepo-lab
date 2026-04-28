package com.wms.inventory.domain.model;

import com.wms.inventory.domain.exception.InsufficientStockException;
import com.wms.inventory.domain.exception.InventoryValidationException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Quantity buckets at one {@code (location_id, sku_id, lot_id)} keying.
 *
 * <p>Authoritative reference:
 * {@code specs/services/inventory-service/domain-model.md} §1.
 *
 * <p>Domain methods are <em>pure</em> mutators on the in-memory aggregate:
 * they update the bucket fields, advance {@link #lastMovementAt}, and return
 * the {@link InventoryMovement} row(s) that the caller must persist alongside
 * the aggregate save in the same {@code @Transactional} boundary. The
 * application service is responsible for writing the Outbox row.
 */
public class Inventory {

    private final UUID id;
    private final UUID warehouseId;
    private final UUID locationId;
    private final UUID skuId;
    private final UUID lotId;
    private int availableQty;
    private int reservedQty;
    private int damagedQty;
    private Instant lastMovementAt;
    private long version;
    private final Instant createdAt;
    private final String createdBy;
    private Instant updatedAt;
    private String updatedBy;

    private Inventory(UUID id, UUID warehouseId, UUID locationId, UUID skuId, UUID lotId,
                      int availableQty, int reservedQty, int damagedQty,
                      Instant lastMovementAt, long version,
                      Instant createdAt, String createdBy,
                      Instant updatedAt, String updatedBy) {
        this.id = Objects.requireNonNull(id, "id");
        this.warehouseId = Objects.requireNonNull(warehouseId, "warehouseId");
        this.locationId = Objects.requireNonNull(locationId, "locationId");
        this.skuId = Objects.requireNonNull(skuId, "skuId");
        this.lotId = lotId;
        if (availableQty < 0 || reservedQty < 0 || damagedQty < 0) {
            throw new IllegalStateException(
                    "Inventory bucket quantities must be non-negative");
        }
        this.availableQty = availableQty;
        this.reservedQty = reservedQty;
        this.damagedQty = damagedQty;
        this.lastMovementAt = Objects.requireNonNull(lastMovementAt, "lastMovementAt");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.updatedBy = Objects.requireNonNull(updatedBy, "updatedBy");
    }

    /**
     * Reconstruct an existing aggregate from persistence. Used by the JPA
     * mapper in the persistence adapter.
     */
    public static Inventory restore(UUID id, UUID warehouseId, UUID locationId, UUID skuId, UUID lotId,
                                    int availableQty, int reservedQty, int damagedQty,
                                    Instant lastMovementAt, long version,
                                    Instant createdAt, String createdBy,
                                    Instant updatedAt, String updatedBy) {
        return new Inventory(id, warehouseId, locationId, skuId, lotId,
                availableQty, reservedQty, damagedQty, lastMovementAt, version,
                createdAt, createdBy, updatedAt, updatedBy);
    }

    /**
     * Create a fresh inventory row with all buckets at zero. Used by the
     * receive use-case when no row exists for the {@code (location, sku, lot)}
     * tuple.
     */
    public static Inventory createEmpty(UUID id, UUID warehouseId, UUID locationId, UUID skuId,
                                        UUID lotId, Instant now, String actorId) {
        return new Inventory(id, warehouseId, locationId, skuId, lotId,
                0, 0, 0, now, 0, now, actorId, now, actorId);
    }

    // ---- Domain methods ------------------------------------------------------

    /**
     * Increment {@link #availableQty} by {@code qty} (W2 — stock receipt).
     *
     * @return the {@link InventoryMovement} record that the caller must persist
     *         alongside this aggregate; carries {@code qty_before} /
     *         {@code qty_after} satisfying the structural invariant.
     */
    public InventoryMovement receive(int qty, ReasonCode reasonCode,
                                     UUID sourceEventId, String actorId, Instant now) {
        if (qty <= 0) {
            throw new InventoryValidationException(
                    "Receive quantity must be > 0, got: " + qty);
        }
        int before = this.availableQty;
        int after = before + qty;
        this.availableQty = after;
        this.lastMovementAt = now;
        this.updatedAt = now;
        this.updatedBy = actorId;
        return InventoryMovement.create(
                id, MovementType.RECEIVE, Bucket.AVAILABLE,
                qty, before, after,
                reasonCode, null, null, null, null,
                sourceEventId, actorId, now);
    }

    /**
     * W4 reserve phase: {@code available -= qty} and {@code reserved += qty}.
     *
     * @return two Movement rows — AVAILABLE -N, RESERVED +N — both carrying
     *         the supplied {@code reservationId}, with the same {@code occurredAt}.
     */
    public List<InventoryMovement> reserve(int qty, UUID reservationId, ReasonCode reasonCode,
                                           UUID sourceEventId, String actorId, Instant now) {
        requirePositive(qty, "reserve");
        Objects.requireNonNull(reservationId, "reservationId");
        if (this.availableQty < qty) {
            throw new InsufficientStockException(id, Bucket.AVAILABLE, this.availableQty, qty);
        }
        int availBefore = this.availableQty;
        int availAfter = availBefore - qty;
        int reservedBefore = this.reservedQty;
        int reservedAfter = reservedBefore + qty;
        this.availableQty = availAfter;
        this.reservedQty = reservedAfter;
        touchAudit(actorId, now);
        InventoryMovement availLeg = InventoryMovement.create(
                id, MovementType.RESERVE, Bucket.AVAILABLE,
                -qty, availBefore, availAfter,
                reasonCode, null, reservationId, null, null,
                sourceEventId, actorId, now);
        InventoryMovement reservedLeg = InventoryMovement.create(
                id, MovementType.RESERVE, Bucket.RESERVED,
                qty, reservedBefore, reservedAfter,
                reasonCode, null, reservationId, null, null,
                sourceEventId, actorId, now);
        return List.of(availLeg, reservedLeg);
    }

    /**
     * Release a reservation: {@code reserved -= qty} and {@code available += qty}.
     */
    public List<InventoryMovement> release(int qty, UUID reservationId, ReasonCode reasonCode,
                                           UUID sourceEventId, String actorId, Instant now) {
        requirePositive(qty, "release");
        Objects.requireNonNull(reservationId, "reservationId");
        if (this.reservedQty < qty) {
            throw new InsufficientStockException(id, Bucket.RESERVED, this.reservedQty, qty);
        }
        int reservedBefore = this.reservedQty;
        int reservedAfter = reservedBefore - qty;
        int availBefore = this.availableQty;
        int availAfter = availBefore + qty;
        this.reservedQty = reservedAfter;
        this.availableQty = availAfter;
        touchAudit(actorId, now);
        InventoryMovement reservedLeg = InventoryMovement.create(
                id, MovementType.RELEASE, Bucket.RESERVED,
                -qty, reservedBefore, reservedAfter,
                reasonCode, null, reservationId, null, null,
                sourceEventId, actorId, now);
        InventoryMovement availLeg = InventoryMovement.create(
                id, MovementType.RELEASE, Bucket.AVAILABLE,
                qty, availBefore, availAfter,
                reasonCode, null, reservationId, null, null,
                sourceEventId, actorId, now);
        return List.of(reservedLeg, availLeg);
    }

    /**
     * W5 confirm phase: terminal consume. {@code reserved -= qty} only —
     * {@code available} is unchanged because the picked qty was already
     * deducted from {@code available} when reserved.
     */
    public InventoryMovement confirm(int qty, UUID reservationId,
                                     UUID sourceEventId, String actorId, Instant now) {
        requirePositive(qty, "confirm");
        Objects.requireNonNull(reservationId, "reservationId");
        if (this.reservedQty < qty) {
            throw new InsufficientStockException(id, Bucket.RESERVED, this.reservedQty, qty);
        }
        int before = this.reservedQty;
        int after = before - qty;
        this.reservedQty = after;
        touchAudit(actorId, now);
        return InventoryMovement.create(
                id, MovementType.CONFIRM, Bucket.RESERVED,
                -qty, before, after,
                ReasonCode.SHIPPING_CONFIRMED, null, reservationId, null, null,
                sourceEventId, actorId, now);
    }

    /**
     * Manual reason-coded adjustment on a single bucket. {@code delta} may be
     * negative (loss / cycle-count shortfall) or positive (found stock); zero
     * is a {@link InventoryValidationException}. The resulting bucket value
     * must be {@code >= 0} or {@link InsufficientStockException} is raised.
     */
    public InventoryMovement adjust(int delta, Bucket bucket, ReasonCode reasonCode,
                                    String reasonNote, UUID adjustmentId,
                                    String actorId, Instant now) {
        Objects.requireNonNull(bucket, "bucket");
        Objects.requireNonNull(reasonCode, "reasonCode");
        Objects.requireNonNull(adjustmentId, "adjustmentId");
        if (delta == 0) {
            throw new InventoryValidationException("Adjust delta must be non-zero");
        }
        int before = bucketValue(bucket);
        int after = before + delta;
        if (after < 0) {
            throw new InsufficientStockException(id, bucket, before, -delta);
        }
        setBucket(bucket, after);
        touchAudit(actorId, now);
        return InventoryMovement.create(
                id, MovementType.ADJUSTMENT, bucket,
                delta, before, after,
                reasonCode, reasonNote, null, null, adjustmentId,
                null, actorId, now);
    }

    /**
     * W1 transfer source leg — {@code available -= qty}. Pair with
     * {@link #transferIn(int, UUID, UUID, String, Instant)} on the target row
     * inside the same {@code @Transactional} boundary.
     */
    public InventoryMovement transferOut(int qty, UUID transferId, UUID targetInventoryId,
                                         String actorId, Instant now) {
        requirePositive(qty, "transferOut");
        Objects.requireNonNull(transferId, "transferId");
        if (this.availableQty < qty) {
            throw new InsufficientStockException(id, Bucket.AVAILABLE, this.availableQty, qty);
        }
        int before = this.availableQty;
        int after = before - qty;
        this.availableQty = after;
        touchAudit(actorId, now);
        return InventoryMovement.create(
                id, MovementType.TRANSFER_OUT, Bucket.AVAILABLE,
                -qty, before, after,
                ReasonCode.TRANSFER_INTERNAL, null, null, transferId, null,
                null, actorId, now);
    }

    /**
     * W1 transfer target leg — {@code available += qty}. No precondition on
     * the existing bucket value (target may be a freshly-upserted empty row).
     */
    public InventoryMovement transferIn(int qty, UUID transferId, UUID sourceInventoryId,
                                        String actorId, Instant now) {
        requirePositive(qty, "transferIn");
        Objects.requireNonNull(transferId, "transferId");
        int before = this.availableQty;
        int after = before + qty;
        this.availableQty = after;
        touchAudit(actorId, now);
        return InventoryMovement.create(
                id, MovementType.TRANSFER_IN, Bucket.AVAILABLE,
                qty, before, after,
                ReasonCode.TRANSFER_INTERNAL, null, null, transferId, null,
                null, actorId, now);
    }

    /**
     * Move {@code qty} from AVAILABLE to DAMAGED on the same row. Returns two
     * Movement legs (AVAILABLE -N, DAMAGED +N) sharing the supplied
     * {@code adjustmentId}.
     */
    public List<InventoryMovement> markDamaged(int qty, String reasonNote,
                                               UUID adjustmentId, String actorId, Instant now) {
        requirePositive(qty, "markDamaged");
        Objects.requireNonNull(adjustmentId, "adjustmentId");
        if (this.availableQty < qty) {
            throw new InsufficientStockException(id, Bucket.AVAILABLE, this.availableQty, qty);
        }
        int availBefore = this.availableQty;
        int availAfter = availBefore - qty;
        int damagedBefore = this.damagedQty;
        int damagedAfter = damagedBefore + qty;
        this.availableQty = availAfter;
        this.damagedQty = damagedAfter;
        touchAudit(actorId, now);
        InventoryMovement availLeg = InventoryMovement.create(
                id, MovementType.DAMAGE_MARK, Bucket.AVAILABLE,
                -qty, availBefore, availAfter,
                ReasonCode.ADJUSTMENT_DAMAGE, reasonNote, null, null, adjustmentId,
                null, actorId, now);
        InventoryMovement damagedLeg = InventoryMovement.create(
                id, MovementType.DAMAGE_MARK, Bucket.DAMAGED,
                qty, damagedBefore, damagedAfter,
                ReasonCode.ADJUSTMENT_DAMAGE, reasonNote, null, null, adjustmentId,
                null, actorId, now);
        return List.of(availLeg, damagedLeg);
    }

    /**
     * Decrement {@link #damagedQty} (write-off). Caller must hold
     * {@code INVENTORY_ADMIN} — enforced at the controller layer.
     */
    public InventoryMovement writeOffDamaged(int qty, String reasonNote, UUID adjustmentId,
                                             String actorId, Instant now) {
        requirePositive(qty, "writeOffDamaged");
        Objects.requireNonNull(adjustmentId, "adjustmentId");
        if (this.damagedQty < qty) {
            throw new InsufficientStockException(id, Bucket.DAMAGED, this.damagedQty, qty);
        }
        int before = this.damagedQty;
        int after = before - qty;
        this.damagedQty = after;
        touchAudit(actorId, now);
        return InventoryMovement.create(
                id, MovementType.DAMAGE_WRITE_OFF, Bucket.DAMAGED,
                -qty, before, after,
                ReasonCode.DAMAGE_WRITE_OFF, reasonNote, null, null, adjustmentId,
                null, actorId, now);
    }

    private int bucketValue(Bucket bucket) {
        return switch (bucket) {
            case AVAILABLE -> availableQty;
            case RESERVED -> reservedQty;
            case DAMAGED -> damagedQty;
        };
    }

    private void setBucket(Bucket bucket, int value) {
        switch (bucket) {
            case AVAILABLE -> this.availableQty = value;
            case RESERVED -> this.reservedQty = value;
            case DAMAGED -> this.damagedQty = value;
        }
    }

    private void requirePositive(int qty, String operation) {
        if (qty <= 0) {
            throw new InventoryValidationException(
                    operation + " quantity must be > 0, got: " + qty);
        }
    }

    private void touchAudit(String actorId, Instant now) {
        this.lastMovementAt = now;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    // ---- Accessors -----------------------------------------------------------

    public UUID id() { return id; }
    public UUID warehouseId() { return warehouseId; }
    public UUID locationId() { return locationId; }
    public UUID skuId() { return skuId; }
    public UUID lotId() { return lotId; }
    public int availableQty() { return availableQty; }
    public int reservedQty() { return reservedQty; }
    public int damagedQty() { return damagedQty; }
    public int onHandQty() { return availableQty + reservedQty + damagedQty; }
    public Instant lastMovementAt() { return lastMovementAt; }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }
    public String createdBy() { return createdBy; }
    public Instant updatedAt() { return updatedAt; }
    public String updatedBy() { return updatedBy; }

    /**
     * Used by the persistence adapter to advance the version after a
     * version-checked UPDATE succeeds. Not a domain operation.
     */
    public void incrementVersion() {
        this.version += 1;
    }
}
