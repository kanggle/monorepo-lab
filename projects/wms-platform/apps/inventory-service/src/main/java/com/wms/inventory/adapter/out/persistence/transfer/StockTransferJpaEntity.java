package com.wms.inventory.adapter.out.persistence.transfer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_transfer")
public class StockTransferJpaEntity {

    @Id
    private UUID id;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "source_location_id", nullable = false)
    private UUID sourceLocationId;

    @Column(name = "target_location_id", nullable = false)
    private UUID targetLocationId;

    @Column(name = "sku_id", nullable = false)
    private UUID skuId;

    @Column(name = "lot_id")
    private UUID lotId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "reason_code", nullable = false, length = 40)
    private String reasonCode;

    @Column(name = "reason_note", length = 500)
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

    protected StockTransferJpaEntity() {
    }

    public StockTransferJpaEntity(UUID id, UUID warehouseId,
                                  UUID sourceLocationId, UUID targetLocationId,
                                  UUID skuId, UUID lotId, int quantity,
                                  String reasonCode, String reasonNote,
                                  String actorId, String idempotencyKey,
                                  long version, Instant createdAt, String createdBy,
                                  Instant updatedAt, String updatedBy) {
        this.id = id;
        this.warehouseId = warehouseId;
        this.sourceLocationId = sourceLocationId;
        this.targetLocationId = targetLocationId;
        this.skuId = skuId;
        this.lotId = lotId;
        this.quantity = quantity;
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
    public UUID getWarehouseId() { return warehouseId; }
    public UUID getSourceLocationId() { return sourceLocationId; }
    public UUID getTargetLocationId() { return targetLocationId; }
    public UUID getSkuId() { return skuId; }
    public UUID getLotId() { return lotId; }
    public int getQuantity() { return quantity; }
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
