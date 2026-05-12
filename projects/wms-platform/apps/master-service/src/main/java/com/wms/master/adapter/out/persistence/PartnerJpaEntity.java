package com.wms.master.adapter.out.persistence;

import com.wms.master.domain.model.PartnerType;
import com.wms.master.domain.model.WarehouseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the partners table. Package-private: only the persistence
 * adapter touches this class. Domain code uses
 * {@link com.wms.master.domain.model.Partner}.
 */
@Entity
@Table(
        name = "partners",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_partners_partner_code",
                columnNames = "partner_code"),
        indexes = {
                @Index(name = "idx_partners_partner_type", columnList = "partner_type"),
                @Index(name = "idx_partners_status_updated_at", columnList = "status, updated_at")
        })
class PartnerJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "partner_code", nullable = false, length = 20, updatable = false)
    private String partnerCode;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "partner_type", nullable = false, length = 10)
    private PartnerType partnerType;

    @Column(name = "business_number", length = 20)
    private String businessNumber;

    @Column(name = "contact_name", length = 100)
    private String contactName;

    @Column(name = "contact_email", length = 200)
    private String contactEmail;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Column(name = "address", length = 300)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WarehouseStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    protected PartnerJpaEntity() {
        // JPA-required no-arg constructor
    }

    PartnerJpaEntity(
            UUID id,
            String partnerCode,
            String name,
            PartnerType partnerType,
            String businessNumber,
            String contactName,
            String contactEmail,
            String contactPhone,
            String address,
            WarehouseStatus status,
            Long version,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy) {
        this.id = id;
        this.partnerCode = partnerCode;
        this.name = name;
        this.partnerType = partnerType;
        this.businessNumber = businessNumber;
        this.contactName = contactName;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.address = address;
        this.status = status;
        this.version = version;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    UUID getId() { return id; }
    String getPartnerCode() { return partnerCode; }
    String getName() { return name; }
    PartnerType getPartnerType() { return partnerType; }
    String getBusinessNumber() { return businessNumber; }
    String getContactName() { return contactName; }
    String getContactEmail() { return contactEmail; }
    String getContactPhone() { return contactPhone; }
    String getAddress() { return address; }
    WarehouseStatus getStatus() { return status; }
    Long getVersion() { return version; }
    Instant getCreatedAt() { return createdAt; }
    String getCreatedBy() { return createdBy; }
    Instant getUpdatedAt() { return updatedAt; }
    String getUpdatedBy() { return updatedBy; }
}
