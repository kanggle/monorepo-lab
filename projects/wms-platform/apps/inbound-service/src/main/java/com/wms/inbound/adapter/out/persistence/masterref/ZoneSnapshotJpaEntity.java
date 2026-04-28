package com.wms.inbound.adapter.out.persistence.masterref;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "zone_snapshot")
public class ZoneSnapshotJpaEntity {

    @Id
    private UUID id;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "zone_code", nullable = false, length = 20)
    private String zoneCode;

    @Column(name = "zone_type", nullable = false, length = 20)
    private String zoneType;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "cached_at", nullable = false)
    private Instant cachedAt;

    @Column(name = "master_version", nullable = false)
    private long masterVersion;

    protected ZoneSnapshotJpaEntity() {
    }

    public ZoneSnapshotJpaEntity(UUID id, UUID warehouseId, String zoneCode, String zoneType,
                                 String status, Instant cachedAt, long masterVersion) {
        this.id = id;
        this.warehouseId = warehouseId;
        this.zoneCode = zoneCode;
        this.zoneType = zoneType;
        this.status = status;
        this.cachedAt = cachedAt;
        this.masterVersion = masterVersion;
    }

    public UUID getId() {
        return id;
    }

    public UUID getWarehouseId() {
        return warehouseId;
    }

    public String getZoneCode() {
        return zoneCode;
    }

    public String getZoneType() {
        return zoneType;
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
