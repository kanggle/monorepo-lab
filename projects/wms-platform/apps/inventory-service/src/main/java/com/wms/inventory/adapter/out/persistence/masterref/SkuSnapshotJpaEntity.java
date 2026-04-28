package com.wms.inventory.adapter.out.persistence.masterref;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sku_snapshot")
public class SkuSnapshotJpaEntity {

    @Id
    private UUID id;

    @Column(name = "sku_code", nullable = false, length = 40)
    private String skuCode;

    @Column(name = "tracking_type", nullable = false, length = 10)
    private String trackingType;

    @Column(name = "base_uom", nullable = false, length = 10)
    private String baseUom;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "cached_at", nullable = false)
    private Instant cachedAt;

    @Column(name = "master_version", nullable = false)
    private long masterVersion;

    protected SkuSnapshotJpaEntity() {
    }

    public SkuSnapshotJpaEntity(UUID id, String skuCode, String trackingType, String baseUom,
                                String status, Instant cachedAt, long masterVersion) {
        this.id = id;
        this.skuCode = skuCode;
        this.trackingType = trackingType;
        this.baseUom = baseUom;
        this.status = status;
        this.cachedAt = cachedAt;
        this.masterVersion = masterVersion;
    }

    public UUID getId() {
        return id;
    }

    public String getSkuCode() {
        return skuCode;
    }

    public String getTrackingType() {
        return trackingType;
    }

    public String getBaseUom() {
        return baseUom;
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
