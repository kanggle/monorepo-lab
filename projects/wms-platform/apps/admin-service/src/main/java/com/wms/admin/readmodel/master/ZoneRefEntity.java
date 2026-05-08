package com.wms.admin.readmodel.master;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Read-model projection of {@code master.zone.*}. */
@Entity
@Table(name = "admin_zone_ref")
public class ZoneRefEntity {

    @Id
    private UUID id;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "zone_code", nullable = false, length = 40)
    private String zoneCode;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "zone_type", length = 40)
    private String zoneType;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "last_event_at", nullable = false)
    private Instant lastEventAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ZoneRefEntity() {
    }

    public ZoneRefEntity(UUID id, UUID warehouseId, String zoneCode, String name, String zoneType,
                         String status, Instant lastEventAt) {
        this.id = id;
        this.warehouseId = warehouseId;
        this.zoneCode = zoneCode;
        this.name = name;
        this.zoneType = zoneType;
        this.status = status;
        this.lastEventAt = lastEventAt;
    }

    public void apply(UUID warehouseId, String zoneCode, String name, String zoneType,
                      String status, Instant lastEventAt) {
        this.warehouseId = warehouseId;
        this.zoneCode = zoneCode;
        this.name = name;
        this.zoneType = zoneType;
        this.status = status;
        this.lastEventAt = lastEventAt;
    }

    public UUID getId() { return id; }
    public UUID getWarehouseId() { return warehouseId; }
    public String getZoneCode() { return zoneCode; }
    public String getName() { return name; }
    public String getZoneType() { return zoneType; }
    public String getStatus() { return status; }
    public Instant getLastEventAt() { return lastEventAt; }
    public long getVersion() { return version; }
}
