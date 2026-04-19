package com.wms.master.adapter.out.persistence;

import com.wms.master.domain.model.LocationType;
import com.wms.master.domain.model.WarehouseStatus;
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
 * JPA entity for the locations table. Package-private: only the persistence
 * adapter touches this class. Domain code uses {@link com.wms.master.domain.model.Location}.
 *
 * <p>FKs to {@code warehouses(id)} and {@code zones(id)} are declared in SQL
 * (V4 migration); we do NOT model cross-aggregate {@code @ManyToOne}
 * relationships — the parent lookup is an explicit application-layer call, not
 * a JPA association traversal (keeps aggregates aggregate-scoped).
 *
 * <p>{@code location_code} is globally unique via
 * {@code uq_locations_location_code} — the constraint name matters because the
 * adapter's exception translator matches on it.
 */
@Entity
@Table(
        name = "locations",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_locations_location_code",
                columnNames = "location_code"),
        indexes = {
                @Index(
                        name = "idx_locations_warehouse_status",
                        columnList = "warehouse_id, status"),
                @Index(
                        name = "idx_locations_zone_status",
                        columnList = "zone_id, status")
        })
class LocationJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "warehouse_id", nullable = false, updatable = false)
    private UUID warehouseId;

    @Column(name = "zone_id", nullable = false, updatable = false)
    private UUID zoneId;

    @Column(name = "location_code", nullable = false, length = 40, updatable = false)
    private String locationCode;

    @Column(name = "aisle", length = 10)
    private String aisle;

    @Column(name = "rack", length = 10)
    private String rack;

    @Column(name = "level", length = 10)
    private String level;

    @Column(name = "bin", length = 10)
    private String bin;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_type", nullable = false, length = 20)
    private LocationType locationType;

    @Column(name = "capacity_units")
    private Integer capacityUnits;

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

    protected LocationJpaEntity() {
        // JPA-required no-arg constructor
    }

    LocationJpaEntity(
            UUID id,
            UUID warehouseId,
            UUID zoneId,
            String locationCode,
            String aisle,
            String rack,
            String level,
            String bin,
            LocationType locationType,
            Integer capacityUnits,
            WarehouseStatus status,
            Long version,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy) {
        this.id = id;
        this.warehouseId = warehouseId;
        this.zoneId = zoneId;
        this.locationCode = locationCode;
        this.aisle = aisle;
        this.rack = rack;
        this.level = level;
        this.bin = bin;
        this.locationType = locationType;
        this.capacityUnits = capacityUnits;
        this.status = status;
        this.version = version;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    UUID getId() { return id; }
    UUID getWarehouseId() { return warehouseId; }
    UUID getZoneId() { return zoneId; }
    String getLocationCode() { return locationCode; }
    String getAisle() { return aisle; }
    String getRack() { return rack; }
    String getLevel() { return level; }
    String getBin() { return bin; }
    LocationType getLocationType() { return locationType; }
    Integer getCapacityUnits() { return capacityUnits; }
    WarehouseStatus getStatus() { return status; }
    Long getVersion() { return version; }
    Instant getCreatedAt() { return createdAt; }
    String getCreatedBy() { return createdBy; }
    Instant getUpdatedAt() { return updatedAt; }
    String getUpdatedBy() { return updatedBy; }

    void applyMutableFields(
            String aisle,
            String rack,
            String level,
            String bin,
            LocationType locationType,
            Integer capacityUnits,
            WarehouseStatus status,
            Instant updatedAt,
            String updatedBy) {
        this.aisle = aisle;
        this.rack = rack;
        this.level = level;
        this.bin = bin;
        this.locationType = locationType;
        this.capacityUnits = capacityUnits;
        this.status = status;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }
}
