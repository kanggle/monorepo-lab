package com.wms.admin.readmodel.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only projection of {@code wms.inventory.adjusted.v1}. PK = source
 * eventId so duplicate insert silently fails (extra dedupe safety net beyond
 * {@code admin_event_dedupe}). Per {@code domain-model.md § 11}.
 */
@Entity
@Table(name = "admin_adjustment_audit")
public class AdjustmentAuditEntity {

    @Id
    private UUID id;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "sku_id", nullable = false)
    private UUID skuId;

    @Column(name = "lot_id")
    private UUID lotId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(nullable = false, length = 40)
    private String bucket;

    @Column(nullable = false)
    private int delta;

    @Column(name = "reason_code", length = 60)
    private String reasonCode;

    @Column(name = "reason_note", length = 500)
    private String reasonNote;

    @Column(name = "actor_id", length = 120)
    private String actorId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "projected_at", nullable = false)
    private Instant projectedAt;

    protected AdjustmentAuditEntity() {
    }

    public AdjustmentAuditEntity(UUID id, UUID locationId, UUID skuId, UUID lotId,
                                 UUID warehouseId, String bucket, int delta, String reasonCode,
                                 String reasonNote, String actorId, Instant occurredAt,
                                 Instant projectedAt) {
        this.id = id;
        this.locationId = locationId;
        this.skuId = skuId;
        this.lotId = lotId;
        this.warehouseId = warehouseId;
        this.bucket = bucket;
        this.delta = delta;
        this.reasonCode = reasonCode;
        this.reasonNote = reasonNote;
        this.actorId = actorId;
        this.occurredAt = occurredAt;
        this.projectedAt = projectedAt;
    }

    public UUID getId() { return id; }
    public UUID getLocationId() { return locationId; }
    public UUID getSkuId() { return skuId; }
    public UUID getLotId() { return lotId; }
    public UUID getWarehouseId() { return warehouseId; }
    public String getBucket() { return bucket; }
    public int getDelta() { return delta; }
    public String getReasonCode() { return reasonCode; }
    public String getReasonNote() { return reasonNote; }
    public String getActorId() { return actorId; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getProjectedAt() { return projectedAt; }
}
