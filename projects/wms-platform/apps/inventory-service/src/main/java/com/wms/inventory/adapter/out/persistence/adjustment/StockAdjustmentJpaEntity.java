package com.wms.inventory.adapter.out.persistence.adjustment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_adjustment")
public class StockAdjustmentJpaEntity {

    @Id
    private UUID id;

    @Column(name = "inventory_id", nullable = false)
    private UUID inventoryId;

    @Column(name = "bucket", nullable = false, length = 20)
    private String bucket;

    @Column(name = "delta", nullable = false)
    private int delta;

    @Column(name = "reason_code", nullable = false, length = 40)
    private String reasonCode;

    @Column(name = "reason_note", nullable = false, length = 500)
    private String reasonNote;

    @Column(name = "actor_id", nullable = false, length = 100)
    private String actorId;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    protected StockAdjustmentJpaEntity() {
    }

    public StockAdjustmentJpaEntity(UUID id, UUID inventoryId, String bucket, int delta,
                                    String reasonCode, String reasonNote,
                                    String actorId, String idempotencyKey,
                                    long version, Instant createdAt, String createdBy,
                                    Instant updatedAt, String updatedBy) {
        this.id = id;
        this.inventoryId = inventoryId;
        this.bucket = bucket;
        this.delta = delta;
        this.reasonCode = reasonCode;
        this.reasonNote = reasonNote;
        this.actorId = actorId;
        this.idempotencyKey = idempotencyKey;
        this.version = version;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    public UUID getId() { return id; }
    public UUID getInventoryId() { return inventoryId; }
    public String getBucket() { return bucket; }
    public int getDelta() { return delta; }
    public String getReasonCode() { return reasonCode; }
    public String getReasonNote() { return reasonNote; }
    public String getActorId() { return actorId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
}
