package com.wms.inventory.domain.model;

import com.wms.inventory.domain.exception.InventoryValidationException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Append-only ledger row produced by every {@link Inventory} mutation (W2).
 *
 * <p>Authoritative reference:
 * {@code specs/services/inventory-service/domain-model.md} §2.
 *
 * <p>Structurally enforces {@code qty_after = qty_before + delta} in the
 * {@link #create} factory. A violation is an {@link IllegalStateException}
 * (programming bug), not a business error. Bucket non-negativity is also
 * asserted ({@code qty_after >= 0}); the upstream domain method validates
 * business invariants before calling the factory, so this is defence-in-depth.
 *
 * <p><b>Restore vs Create.</b> Two factories exist with deliberately
 * different contracts:
 * <ul>
 *   <li>{@link #create} is the only entry point that enforces the W2
 *       structural invariant. All in-process callers (domain methods on
 *       {@link Inventory}, {@code Reservation}, {@code StockAdjustment},
 *       {@code StockTransfer}) must go through {@code create} so a buggy
 *       caller fails fast.</li>
 *   <li>{@link #restore} is the JPA-load entry point. It deliberately
 *       trusts persisted data and skips the structural assertion — the row
 *       was already validated by {@code create} when it was first written,
 *       and re-asserting on every load would (a) duplicate guard logic the
 *       database already enforces via NOT NULL / CHECK constraints and
 *       (b) couple read paths to the W2 algebra. This is the same trust-mode
 *       convention used by {@link Inventory#restore},
 *       {@code Reservation.restore}, {@code StockAdjustment.restore}, and
 *       {@code StockTransfer.restore} — kept uniform across aggregates so
 *       that mappers have one rule to follow. If a corrupted row is ever
 *       observed (e.g. via direct SQL tampering), detection is the job of
 *       the database CHECK constraint or an offline reconciliation job, not
 *       this factory.</li>
 * </ul>
 */
public final class InventoryMovement {

    private final UUID id;
    private final UUID inventoryId;
    private final MovementType movementType;
    private final Bucket bucket;
    private final int delta;
    private final int qtyBefore;
    private final int qtyAfter;
    private final ReasonCode reasonCode;
    private final String reasonNote;
    private final UUID reservationId;
    private final UUID transferId;
    private final UUID adjustmentId;
    private final UUID sourceEventId;
    private final String actorId;
    private final Instant occurredAt;
    private final Instant createdAt;

    private InventoryMovement(UUID id, UUID inventoryId, MovementType movementType, Bucket bucket,
                              int delta, int qtyBefore, int qtyAfter,
                              ReasonCode reasonCode, String reasonNote,
                              UUID reservationId, UUID transferId, UUID adjustmentId,
                              UUID sourceEventId, String actorId, Instant occurredAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.inventoryId = Objects.requireNonNull(inventoryId, "inventoryId");
        this.movementType = Objects.requireNonNull(movementType, "movementType");
        this.bucket = Objects.requireNonNull(bucket, "bucket");
        this.delta = delta;
        this.qtyBefore = qtyBefore;
        this.qtyAfter = qtyAfter;
        this.reasonCode = Objects.requireNonNull(reasonCode, "reasonCode");
        this.reasonNote = reasonNote;
        this.reservationId = reservationId;
        this.transferId = transferId;
        this.adjustmentId = adjustmentId;
        this.sourceEventId = sourceEventId;
        this.actorId = Objects.requireNonNull(actorId, "actorId");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.createdAt = occurredAt;
    }

    /**
     * Factory invoked by domain methods. Asserts the W2 structural invariant:
     * {@code qty_after = qty_before + delta} and {@code qty_after >= 0}.
     */
    public static InventoryMovement create(UUID inventoryId, MovementType movementType, Bucket bucket,
                                           int delta, int qtyBefore, int qtyAfter,
                                           ReasonCode reasonCode, String reasonNote,
                                           UUID reservationId, UUID transferId, UUID adjustmentId,
                                           UUID sourceEventId, String actorId, Instant occurredAt) {
        if (qtyAfter != qtyBefore + delta) {
            throw new IllegalStateException(
                    "Movement structural invariant violated: qty_after(" + qtyAfter
                            + ") != qty_before(" + qtyBefore + ") + delta(" + delta + ")");
        }
        if (qtyAfter < 0 || qtyBefore < 0) {
            throw new IllegalStateException(
                    "Movement quantities must be non-negative: qty_before=" + qtyBefore
                            + ", qty_after=" + qtyAfter);
        }
        if (reasonNote != null && reasonNote.trim().length() < 3
                && reasonCode == ReasonCode.ADJUSTMENT_RECLASSIFY) {
            // Adjustments require a non-trivial reasonNote; receive/picking
            // paths leave it null so this is only checked when set.
            throw new InventoryValidationException(
                    "reasonNote must be at least 3 non-blank characters when set for ADJUSTMENT_*");
        }
        return new InventoryMovement(
                UUID.randomUUID(), inventoryId, movementType, bucket,
                delta, qtyBefore, qtyAfter, reasonCode, reasonNote,
                reservationId, transferId, adjustmentId, sourceEventId,
                actorId, occurredAt);
    }

    /**
     * Reconstruct a persisted movement row. Used by the JPA mapper.
     *
     * <p>Trusts persisted data — does not re-run the W2 structural invariant
     * check that {@link #create} performs. See the class-level Javadoc for
     * the rationale and for the convention shared with sibling aggregates.
     */
    public static InventoryMovement restore(UUID id, UUID inventoryId, MovementType movementType,
                                            Bucket bucket, int delta, int qtyBefore, int qtyAfter,
                                            ReasonCode reasonCode, String reasonNote,
                                            UUID reservationId, UUID transferId, UUID adjustmentId,
                                            UUID sourceEventId, String actorId, Instant occurredAt) {
        InventoryMovement m = new InventoryMovement(id, inventoryId, movementType, bucket,
                delta, qtyBefore, qtyAfter, reasonCode, reasonNote,
                reservationId, transferId, adjustmentId, sourceEventId, actorId, occurredAt);
        return m;
    }

    public UUID id() { return id; }
    public UUID inventoryId() { return inventoryId; }
    public MovementType movementType() { return movementType; }
    public Bucket bucket() { return bucket; }
    public int delta() { return delta; }
    public int qtyBefore() { return qtyBefore; }
    public int qtyAfter() { return qtyAfter; }
    public ReasonCode reasonCode() { return reasonCode; }
    public String reasonNote() { return reasonNote; }
    public UUID reservationId() { return reservationId; }
    public UUID transferId() { return transferId; }
    public UUID adjustmentId() { return adjustmentId; }
    public UUID sourceEventId() { return sourceEventId; }
    public String actorId() { return actorId; }
    public Instant occurredAt() { return occurredAt; }
    public Instant createdAt() { return createdAt; }
}
