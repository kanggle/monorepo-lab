package com.wms.admin.readmodel.master;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Read-model projection of {@code master.partner.*}. */
@Entity
@Table(name = "admin_partner_ref")
public class PartnerRefEntity {

    @Id
    private UUID id;

    @Column(name = "partner_code", nullable = false, length = 40)
    private String partnerCode;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "partner_type", length = 40)
    private String partnerType;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "last_event_at", nullable = false)
    private Instant lastEventAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected PartnerRefEntity() {
    }

    public PartnerRefEntity(UUID id, String partnerCode, String name, String partnerType,
                            String status, Instant lastEventAt) {
        this.id = id;
        this.partnerCode = partnerCode;
        this.name = name;
        this.partnerType = partnerType;
        this.status = status;
        this.lastEventAt = lastEventAt;
    }

    public void apply(String partnerCode, String name, String partnerType, String status,
                      Instant lastEventAt) {
        this.partnerCode = partnerCode;
        this.name = name;
        this.partnerType = partnerType;
        this.status = status;
        this.lastEventAt = lastEventAt;
    }

    public UUID getId() { return id; }
    public String getPartnerCode() { return partnerCode; }
    public String getName() { return name; }
    public String getPartnerType() { return partnerType; }
    public String getStatus() { return status; }
    public Instant getLastEventAt() { return lastEventAt; }
    public long getVersion() { return version; }
}
