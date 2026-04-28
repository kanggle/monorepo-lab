package com.wms.inbound.adapter.out.persistence.masterref;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "lot_snapshot")
public class LotSnapshotJpaEntity {

    @Id
    private UUID id;

    @Column(name = "sku_id", nullable = false)
    private UUID skuId;

    @Column(name = "lot_no", nullable = false, length = 40)
    private String lotNo;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "cached_at", nullable = false)
    private Instant cachedAt;

    @Column(name = "master_version", nullable = false)
    private long masterVersion;

    protected LotSnapshotJpaEntity() {
    }

    public LotSnapshotJpaEntity(UUID id, UUID skuId, String lotNo, LocalDate expiryDate,
                                String status, Instant cachedAt, long masterVersion) {
        this.id = id;
        this.skuId = skuId;
        this.lotNo = lotNo;
        this.expiryDate = expiryDate;
        this.status = status;
        this.cachedAt = cachedAt;
        this.masterVersion = masterVersion;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSkuId() {
        return skuId;
    }

    public String getLotNo() {
        return lotNo;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
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
