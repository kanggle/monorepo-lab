package com.wms.inbound.adapter.out.persistence.masterref;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "warehouse_snapshot")
public class WarehouseSnapshotJpaEntity {

    @Id
    private UUID id;

    @Column(name = "warehouse_code", nullable = false, length = 20)
    private String warehouseCode;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "cached_at", nullable = false)
    private Instant cachedAt;

    @Column(name = "master_version", nullable = false)
    private long masterVersion;

    protected WarehouseSnapshotJpaEntity() {
    }

    public WarehouseSnapshotJpaEntity(UUID id, String warehouseCode, String status,
                                      Instant cachedAt, long masterVersion) {
        this.id = id;
        this.warehouseCode = warehouseCode;
        this.status = status;
        this.cachedAt = cachedAt;
        this.masterVersion = masterVersion;
    }

    public UUID getId() {
        return id;
    }

    public String getWarehouseCode() {
        return warehouseCode;
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
