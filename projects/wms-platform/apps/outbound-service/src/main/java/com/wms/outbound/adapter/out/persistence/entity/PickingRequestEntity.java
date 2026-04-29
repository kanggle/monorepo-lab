package com.wms.outbound.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity backing {@code picking_request}.
 *
 * <p>Aligned with {@code specs/services/outbound-service/domain-model.md} §2.
 * The {@code saga_id} column was added in V11.
 */
@Entity
@Table(name = "picking_request")
public class PickingRequestEntity {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "saga_id")
    private UUID sagaId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PickingRequestEntity() {
    }

    public PickingRequestEntity(UUID id, UUID orderId, UUID sagaId, UUID warehouseId,
                                String status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.orderId = orderId;
        this.sagaId = sagaId;
        this.warehouseId = warehouseId;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getSagaId() { return sagaId; }
    public UUID getWarehouseId() { return warehouseId; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    public void setStatus(String status) { this.status = status; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setSagaId(UUID sagaId) { this.sagaId = sagaId; }
}
