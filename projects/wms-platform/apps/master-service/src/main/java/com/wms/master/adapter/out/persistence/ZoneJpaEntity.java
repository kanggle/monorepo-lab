package com.wms.master.adapter.out.persistence;

import com.wms.master.domain.model.WarehouseStatus;
import com.wms.master.domain.model.ZoneType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the zones table. Package-private: only the persistence adapter
 * touches this class. Domain code uses {@link com.wms.master.domain.model.Zone}.
 *
 * <p>The FK to {@code warehouses(id)} is declared in SQL (V3 migration); we do
 * not model it as a {@code @ManyToOne} relationship here because the Zone
 * aggregate stays aggregate-scoped — the parent lookup is an explicit
 * application-layer call, not a JPA association traversal.
 */
@Entity
@Table(
        name = "zones",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_zones_warehouse_code",
                columnNames = {"warehouse_id", "zone_code"}),
        indexes = @Index(
                name = "idx_zones_warehouse_status_updated_at",
                columnList = "warehouse_id, status, updated_at"))
class ZoneJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "warehouse_id", nullable = false, updatable = false)
    private UUID warehouseId;

    @Column(name = "zone_code", nullable = false, length = 20, updatable = false)
    private String zoneCode;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone_type", nullable = false, length = 20)
    private ZoneType zoneType;

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

    protected ZoneJpaEntity() {
        // JPA-required no-arg constructor
    }

    ZoneJpaEntity(
            UUID id,
            UUID warehouseId,
            String zoneCode,
            String name,
            ZoneType zoneType,
            WarehouseStatus status,
            Long version,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy) {
        this.id = id;
        this.warehouseId = warehouseId;
        this.zoneCode = zoneCode;
        this.name = name;
        this.zoneType = zoneType;
        this.status = status;
        this.version = version;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    UUID getId() { return id; }
    UUID getWarehouseId() { return warehouseId; }
    String getZoneCode() { return zoneCode; }
    String getName() { return name; }
    ZoneType getZoneType() { return zoneType; }
    WarehouseStatus getStatus() { return status; }
    Long getVersion() { return version; }
    Instant getCreatedAt() { return createdAt; }
    String getCreatedBy() { return createdBy; }
    Instant getUpdatedAt() { return updatedAt; }
    String getUpdatedBy() { return updatedBy; }

    void applyMutableFields(
            String name,
            ZoneType zoneType,
            WarehouseStatus status,
            Instant updatedAt,
            String updatedBy) {
        this.name = name;
        this.zoneType = zoneType;
        this.status = status;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }
}
