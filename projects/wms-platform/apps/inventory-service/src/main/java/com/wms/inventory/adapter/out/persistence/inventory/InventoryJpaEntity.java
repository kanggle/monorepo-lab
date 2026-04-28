package com.wms.inventory.adapter.out.persistence.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code inventory} table.
 *
 * <p>The {@code @Version} column is managed by Hibernate to enforce optimistic
 * locking on every UPDATE — concurrent mutations on the same row throw
 * {@code OptimisticLockingFailureException}, which the application service
 * surfaces as {@code 409 CONFLICT}.
 *
 * <p>This entity is the persistence-shape only; the domain model
 * ({@link com.wms.inventory.domain.model.Inventory}) is a separate POJO and
 * the mapper translates between them.
 */
@Entity
@Table(name = "inventory")
public class InventoryJpaEntity {

    @Id
    private UUID id;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "sku_id", nullable = false)
    private UUID skuId;

    @Column(name = "lot_id")
    private UUID lotId;

    @Column(name = "available_qty", nullable = false)
    private int availableQty;

    @Column(name = "reserved_qty", nullable = false)
    private int reservedQty;

    @Column(name = "damaged_qty", nullable = false)
    private int damagedQty;

    @Column(name = "last_movement_at", nullable = false)
    private Instant lastMovementAt;

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

    protected InventoryJpaEntity() {
    }

    public InventoryJpaEntity(UUID id, UUID warehouseId, UUID locationId, UUID skuId, UUID lotId,
                              int availableQty, int reservedQty, int damagedQty,
                              Instant lastMovementAt, long version,
                              Instant createdAt, String createdBy,
                              Instant updatedAt, String updatedBy) {
        this.id = id;
        this.warehouseId = warehouseId;
        this.locationId = locationId;
        this.skuId = skuId;
        this.lotId = lotId;
        this.availableQty = availableQty;
        this.reservedQty = reservedQty;
        this.damagedQty = damagedQty;
        this.lastMovementAt = lastMovementAt;
        this.version = version;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    public UUID getId() { return id; }
    public UUID getWarehouseId() { return warehouseId; }
    public UUID getLocationId() { return locationId; }
    public UUID getSkuId() { return skuId; }
    public UUID getLotId() { return lotId; }
    public int getAvailableQty() { return availableQty; }
    public int getReservedQty() { return reservedQty; }
    public int getDamagedQty() { return damagedQty; }
    public Instant getLastMovementAt() { return lastMovementAt; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }

    void copyMutableFields(int availableQty, int reservedQty, int damagedQty,
                           Instant lastMovementAt, Instant updatedAt, String updatedBy) {
        this.availableQty = availableQty;
        this.reservedQty = reservedQty;
        this.damagedQty = damagedQty;
        this.lastMovementAt = lastMovementAt;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }
}
