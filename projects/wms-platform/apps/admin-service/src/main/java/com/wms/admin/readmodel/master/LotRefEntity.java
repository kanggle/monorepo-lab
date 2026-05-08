package com.wms.admin.readmodel.master;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Read-model projection of {@code master.lot.*}. */
@Entity
@Table(name = "admin_lot_ref")
public class LotRefEntity {

    @Id
    private UUID id;

    @Column(name = "sku_id", nullable = false)
    private UUID skuId;

    @Column(name = "lot_no", nullable = false, length = 80)
    private String lotNo;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "last_event_at", nullable = false)
    private Instant lastEventAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected LotRefEntity() {
    }

    public LotRefEntity(UUID id, UUID skuId, String lotNo, LocalDate expiryDate, String status,
                        Instant lastEventAt) {
        this.id = id;
        this.skuId = skuId;
        this.lotNo = lotNo;
        this.expiryDate = expiryDate;
        this.status = status;
        this.lastEventAt = lastEventAt;
    }

    public void apply(UUID skuId, String lotNo, LocalDate expiryDate, String status,
                      Instant lastEventAt) {
        this.skuId = skuId;
        this.lotNo = lotNo;
        this.expiryDate = expiryDate;
        this.status = status;
        this.lastEventAt = lastEventAt;
    }

    public UUID getId() { return id; }
    public UUID getSkuId() { return skuId; }
    public String getLotNo() { return lotNo; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public String getStatus() { return status; }
    public Instant getLastEventAt() { return lastEventAt; }
    public long getVersion() { return version; }
}
