package com.wms.master.adapter.out.persistence;

import com.wms.master.domain.model.LotStatus;
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
import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA entity for the {@code lots} table. Package-private: only the
 * persistence adapter touches this class. Domain code uses
 * {@link com.wms.master.domain.model.Lot}.
 *
 * <p>The partial index {@code idx_lots_expiry_active} (WHERE status='ACTIVE')
 * lives in the Flyway migration only — JPA's {@code @Index} cannot express a
 * filter.
 */
@Entity
@Table(
        name = "lots",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_lots_sku_lotno",
                columnNames = {"sku_id", "lot_no"}),
        indexes = @Index(
                name = "idx_lots_sku_status",
                columnList = "sku_id, status"))
class LotJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "sku_id", nullable = false, updatable = false)
    private UUID skuId;

    @Column(name = "lot_no", nullable = false, length = 40, updatable = false)
    private String lotNo;

    @Column(name = "manufactured_date", updatable = false)
    private LocalDate manufacturedDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "supplier_partner_id")
    private UUID supplierPartnerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private LotStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 255, updatable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 255)
    private String updatedBy;

    protected LotJpaEntity() {
        // JPA-required no-arg constructor
    }

    LotJpaEntity(
            UUID id,
            UUID skuId,
            String lotNo,
            LocalDate manufacturedDate,
            LocalDate expiryDate,
            UUID supplierPartnerId,
            LotStatus status,
            Long version,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy) {
        this.id = id;
        this.skuId = skuId;
        this.lotNo = lotNo;
        this.manufacturedDate = manufacturedDate;
        this.expiryDate = expiryDate;
        this.supplierPartnerId = supplierPartnerId;
        this.status = status;
        this.version = version;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    UUID getId() { return id; }
    UUID getSkuId() { return skuId; }
    String getLotNo() { return lotNo; }
    LocalDate getManufacturedDate() { return manufacturedDate; }
    LocalDate getExpiryDate() { return expiryDate; }
    UUID getSupplierPartnerId() { return supplierPartnerId; }
    LotStatus getStatus() { return status; }
    Long getVersion() { return version; }
    Instant getCreatedAt() { return createdAt; }
    String getCreatedBy() { return createdBy; }
    Instant getUpdatedAt() { return updatedAt; }
    String getUpdatedBy() { return updatedBy; }
}
