package com.wms.admin.readmodel.master;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Read-model projection of {@code master.location.*}. */
@Entity
@Table(name = "admin_location_ref")
public class LocationRefEntity {

    @Id
    private UUID id;

    @Column(name = "location_code", nullable = false, length = 80)
    private String locationCode;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "zone_id")
    private UUID zoneId;

    @Column(name = "location_type", length = 40)
    private String locationType;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "last_event_at", nullable = false)
    private Instant lastEventAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected LocationRefEntity() {
    }

    public LocationRefEntity(UUID id, String locationCode, UUID warehouseId, UUID zoneId,
                             String locationType, String status, Instant lastEventAt) {
        this.id = id;
        this.locationCode = locationCode;
        this.warehouseId = warehouseId;
        this.zoneId = zoneId;
        this.locationType = locationType;
        this.status = status;
        this.lastEventAt = lastEventAt;
    }

    public void apply(String locationCode, UUID warehouseId, UUID zoneId, String locationType,
                      String status, Instant lastEventAt) {
        this.locationCode = locationCode;
        this.warehouseId = warehouseId;
        this.zoneId = zoneId;
        this.locationType = locationType;
        this.status = status;
        this.lastEventAt = lastEventAt;
    }

    public UUID getId() { return id; }
    public String getLocationCode() { return locationCode; }
    public UUID getWarehouseId() { return warehouseId; }
    public UUID getZoneId() { return zoneId; }
    public String getLocationType() { return locationType; }
    public String getStatus() { return status; }
    public Instant getLastEventAt() { return lastEventAt; }
    public long getVersion() { return version; }
}
