package com.wms.master.adapter.out.persistence;

import com.wms.master.domain.model.WarehouseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the warehouses table. Package-private: only the persistence
 * adapter touches this class. Domain code uses {@link com.wms.master.domain.model.Warehouse}.
 */
@Entity
@Table(name = "warehouses")
class WarehouseJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "warehouse_code", nullable = false, length = 10, updatable = false)
    private String warehouseCode;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "address", length = 200)
    private String address;

    @Column(name = "timezone", nullable = false, length = 40)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WarehouseStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    protected WarehouseJpaEntity() {
        // JPA-required no-arg constructor
    }

    WarehouseJpaEntity(
            UUID id,
            String warehouseCode,
            String name,
            String address,
            String timezone,
            WarehouseStatus status,
            Long version,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy) {
        this.id = id;
        this.warehouseCode = warehouseCode;
        this.name = name;
        this.address = address;
        this.timezone = timezone;
        this.status = status;
        this.version = version;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    UUID getId() { return id; }
    String getWarehouseCode() { return warehouseCode; }
    String getName() { return name; }
    String getAddress() { return address; }
    String getTimezone() { return timezone; }
    WarehouseStatus getStatus() { return status; }
    Long getVersion() { return version; }
    Instant getCreatedAt() { return createdAt; }
    String getCreatedBy() { return createdBy; }
    Instant getUpdatedAt() { return updatedAt; }
    String getUpdatedBy() { return updatedBy; }

    void applyMutableFields(
            String name,
            String address,
            String timezone,
            WarehouseStatus status,
            Instant updatedAt,
            String updatedBy) {
        this.name = name;
        this.address = address;
        this.timezone = timezone;
        this.status = status;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }
}
