package com.wms.inventory.adapter.out.persistence.movement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the append-only {@code inventory_movement} table.
 *
 * <p>No setters and no {@code @Version} — once persisted, never modified.
 * The W2 trigger in V5 rejects UPDATE/DELETE attempts at the database layer,
 * so this entity intentionally exposes a single insert-friendly constructor.
 */
@Entity
@Table(name = "inventory_movement")
public class InventoryMovementJpaEntity {

    @Id
    private UUID id;

    @Column(name = "inventory_id", nullable = false)
    private UUID inventoryId;

    @Column(name = "movement_type", nullable = false, length = 30)
    private String movementType;

    @Column(name = "bucket", nullable = false, length = 20)
    private String bucket;

    @Column(name = "delta", nullable = false)
    private int delta;

    @Column(name = "qty_before", nullable = false)
    private int qtyBefore;

    @Column(name = "qty_after", nullable = false)
    private int qtyAfter;

    @Column(name = "reason_code", nullable = false, length = 40)
    private String reasonCode;

    @Column(name = "reason_note", length = 500)
    private String reasonNote;

    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "transfer_id")
    private UUID transferId;

    @Column(name = "adjustment_id")
    private UUID adjustmentId;

    @Column(name = "source_event_id")
    private UUID sourceEventId;

    @Column(name = "actor_id", nullable = false, length = 100)
    private String actorId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InventoryMovementJpaEntity() {
    }

    public InventoryMovementJpaEntity(UUID id, UUID inventoryId, String movementType, String bucket,
                                      int delta, int qtyBefore, int qtyAfter,
                                      String reasonCode, String reasonNote,
                                      UUID reservationId, UUID transferId, UUID adjustmentId,
                                      UUID sourceEventId, String actorId, Instant occurredAt) {
        this.id = id;
        this.inventoryId = inventoryId;
        this.movementType = movementType;
        this.bucket = bucket;
        this.delta = delta;
        this.qtyBefore = qtyBefore;
        this.qtyAfter = qtyAfter;
        this.reasonCode = reasonCode;
        this.reasonNote = reasonNote;
        this.reservationId = reservationId;
        this.transferId = transferId;
        this.adjustmentId = adjustmentId;
        this.sourceEventId = sourceEventId;
        this.actorId = actorId;
        this.occurredAt = occurredAt;
        this.createdAt = occurredAt;
    }

    public UUID getId() { return id; }
    public UUID getInventoryId() { return inventoryId; }
    public String getMovementType() { return movementType; }
    public String getBucket() { return bucket; }
    public int getDelta() { return delta; }
    public int getQtyBefore() { return qtyBefore; }
    public int getQtyAfter() { return qtyAfter; }
    public String getReasonCode() { return reasonCode; }
    public String getReasonNote() { return reasonNote; }
    public UUID getReservationId() { return reservationId; }
    public UUID getTransferId() { return transferId; }
    public UUID getAdjustmentId() { return adjustmentId; }
    public UUID getSourceEventId() { return sourceEventId; }
    public String getActorId() { return actorId; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getCreatedAt() { return createdAt; }
}
