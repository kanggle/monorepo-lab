package com.wms.outbound.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "location_snapshot")
public class MasterLocationSnapshot {

    @Id
    private UUID id;

    @Column(name = "location_code", nullable = false, length = 40)
    private String locationCode;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "zone_id", nullable = false)
    private UUID zoneId;

    @Column(name = "location_type", nullable = false, length = 20)
    private String locationType;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "cached_at", nullable = false)
    private Instant cachedAt;

    @Column(name = "master_version", nullable = false)
    private long masterVersion;

    protected MasterLocationSnapshot() {
    }

    public MasterLocationSnapshot(UUID id, String locationCode, UUID warehouseId, UUID zoneId,
                                  String locationType, String status, Instant cachedAt,
                                  long masterVersion) {
        this.id = id;
        this.locationCode = locationCode;
        this.warehouseId = warehouseId;
        this.zoneId = zoneId;
        this.locationType = locationType;
        this.status = status;
        this.cachedAt = cachedAt;
        this.masterVersion = masterVersion;
    }

    public UUID getId() {
        return id;
    }

    public String getLocationCode() {
        return locationCode;
    }

    public UUID getWarehouseId() {
        return warehouseId;
    }

    public UUID getZoneId() {
        return zoneId;
    }

    public String getLocationType() {
        return locationType;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCachedAt() {
        return cachedAt;
    }

    public long getMasterVersion() {
        return masterVersion;
    }
}
