package com.wms.inbound.adapter.out.persistence.masterref;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "partner_snapshot")
public class PartnerSnapshotJpaEntity {

    @Id
    private UUID id;

    @Column(name = "partner_code", nullable = false, length = 40)
    private String partnerCode;

    @Column(name = "partner_type", nullable = false, length = 20)
    private String partnerType;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "cached_at", nullable = false)
    private Instant cachedAt;

    @Column(name = "master_version", nullable = false)
    private long masterVersion;

    protected PartnerSnapshotJpaEntity() {
    }

    public PartnerSnapshotJpaEntity(UUID id, String partnerCode, String partnerType,
                                    String status, Instant cachedAt, long masterVersion) {
        this.id = id;
        this.partnerCode = partnerCode;
        this.partnerType = partnerType;
        this.status = status;
        this.cachedAt = cachedAt;
        this.masterVersion = masterVersion;
    }

    public UUID getId() {
        return id;
    }

    public String getPartnerCode() {
        return partnerCode;
    }

    public String getPartnerType() {
        return partnerType;
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
