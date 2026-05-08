package com.wms.admin.readmodel.master;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Read-model projection of {@code master.sku.*}. */
@Entity
@Table(name = "admin_sku_ref")
public class SkuRefEntity {

    @Id
    private UUID id;

    @Column(name = "sku_code", nullable = false, length = 40)
    private String skuCode;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "base_uom", length = 20)
    private String baseUom;

    @Column(name = "tracking_type", length = 20)
    private String trackingType;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "last_event_at", nullable = false)
    private Instant lastEventAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected SkuRefEntity() {
    }

    public SkuRefEntity(UUID id, String skuCode, String name, String baseUom, String trackingType,
                        String status, Instant lastEventAt) {
        this.id = id;
        this.skuCode = skuCode;
        this.name = name;
        this.baseUom = baseUom;
        this.trackingType = trackingType;
        this.status = status;
        this.lastEventAt = lastEventAt;
    }

    public void apply(String skuCode, String name, String baseUom, String trackingType,
                      String status, Instant lastEventAt) {
        this.skuCode = skuCode;
        this.name = name;
        this.baseUom = baseUom;
        this.trackingType = trackingType;
        this.status = status;
        this.lastEventAt = lastEventAt;
    }

    public UUID getId() { return id; }
    public String getSkuCode() { return skuCode; }
    public String getName() { return name; }
    public String getBaseUom() { return baseUom; }
    public String getTrackingType() { return trackingType; }
    public String getStatus() { return status; }
    public Instant getLastEventAt() { return lastEventAt; }
    public long getVersion() { return version; }
}
