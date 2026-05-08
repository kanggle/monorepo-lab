package com.wms.admin.readmodel.master;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Read-model projection of {@code master.warehouse.*}. Per
 * {@code domain-model.md § 5}.
 *
 * <p>Upsert table — projection consumer applies last-write-wins via
 * {@code last_event_at}. The {@code @Version} column is the JPA optimistic
 * lock used to detect concurrent projections from parallel listener threads.
 */
@Entity
@Table(name = "admin_warehouse_ref")
public class WarehouseRefEntity {

    @Id
    private UUID id;

    @Column(name = "warehouse_code", nullable = false, length = 40)
    private String warehouseCode;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 40)
    private String timezone;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "last_event_at", nullable = false)
    private Instant lastEventAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected WarehouseRefEntity() {
    }

    public WarehouseRefEntity(UUID id, String warehouseCode, String name, String timezone,
                              String status, Instant lastEventAt) {
        this.id = id;
        this.warehouseCode = warehouseCode;
        this.name = name;
        this.timezone = timezone;
        this.status = status;
        this.lastEventAt = lastEventAt;
    }

    public void apply(String warehouseCode, String name, String timezone, String status,
                      Instant lastEventAt) {
        this.warehouseCode = warehouseCode;
        this.name = name;
        this.timezone = timezone;
        this.status = status;
        this.lastEventAt = lastEventAt;
    }

    public UUID getId() { return id; }
    public String getWarehouseCode() { return warehouseCode; }
    public String getName() { return name; }
    public String getTimezone() { return timezone; }
    public String getStatus() { return status; }
    public Instant getLastEventAt() { return lastEventAt; }
    public long getVersion() { return version; }
}
