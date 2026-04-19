package com.wms.master.adapter.out.persistence;

import com.wms.master.domain.model.BaseUom;
import com.wms.master.domain.model.TrackingType;
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
 * JPA entity for the skus table. Package-private: only the persistence adapter
 * touches this class. Domain code uses {@link com.wms.master.domain.model.Sku}.
 *
 * <p>The partial unique index on {@code barcode} (unique when non-null) lives
 * in the Flyway migration only — {@code @UniqueConstraint} does not support a
 * filter, and the DB-side index is the canonical guard.
 */
@Entity
@Table(
        name = "skus",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_skus_sku_code",
                columnNames = "sku_code"),
        indexes = @Index(
                name = "idx_skus_status_updated_at",
                columnList = "status, updated_at"))
class SkuJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "sku_code", nullable = false, length = 40, updatable = false)
    private String skuCode;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "barcode", length = 40)
    private String barcode;

    @Enumerated(EnumType.STRING)
    @Column(name = "base_uom", nullable = false, length = 10, updatable = false)
    private BaseUom baseUom;

    @Enumerated(EnumType.STRING)
    @Column(name = "tracking_type", nullable = false, length = 10, updatable = false)
    private TrackingType trackingType;

    @Column(name = "weight_grams")
    private Integer weightGrams;

    @Column(name = "volume_ml")
    private Integer volumeMl;

    @Column(name = "hazard_class", length = 20)
    private String hazardClass;

    @Column(name = "shelf_life_days")
    private Integer shelfLifeDays;

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

    protected SkuJpaEntity() {
        // JPA-required no-arg constructor
    }

    SkuJpaEntity(
            UUID id,
            String skuCode,
            String name,
            String description,
            String barcode,
            BaseUom baseUom,
            TrackingType trackingType,
            Integer weightGrams,
            Integer volumeMl,
            String hazardClass,
            Integer shelfLifeDays,
            WarehouseStatus status,
            Long version,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy) {
        this.id = id;
        this.skuCode = skuCode;
        this.name = name;
        this.description = description;
        this.barcode = barcode;
        this.baseUom = baseUom;
        this.trackingType = trackingType;
        this.weightGrams = weightGrams;
        this.volumeMl = volumeMl;
        this.hazardClass = hazardClass;
        this.shelfLifeDays = shelfLifeDays;
        this.status = status;
        this.version = version;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    UUID getId() { return id; }
    String getSkuCode() { return skuCode; }
    String getName() { return name; }
    String getDescription() { return description; }
    String getBarcode() { return barcode; }
    BaseUom getBaseUom() { return baseUom; }
    TrackingType getTrackingType() { return trackingType; }
    Integer getWeightGrams() { return weightGrams; }
    Integer getVolumeMl() { return volumeMl; }
    String getHazardClass() { return hazardClass; }
    Integer getShelfLifeDays() { return shelfLifeDays; }
    WarehouseStatus getStatus() { return status; }
    Long getVersion() { return version; }
    Instant getCreatedAt() { return createdAt; }
    String getCreatedBy() { return createdBy; }
    Instant getUpdatedAt() { return updatedAt; }
    String getUpdatedBy() { return updatedBy; }

    void applyMutableFields(
            String name,
            String description,
            String barcode,
            Integer weightGrams,
            Integer volumeMl,
            String hazardClass,
            Integer shelfLifeDays,
            WarehouseStatus status,
            Instant updatedAt,
            String updatedBy) {
        this.name = name;
        this.description = description;
        this.barcode = barcode;
        this.weightGrams = weightGrams;
        this.volumeMl = volumeMl;
        this.hazardClass = hazardClass;
        this.shelfLifeDays = shelfLifeDays;
        this.status = status;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }
}
