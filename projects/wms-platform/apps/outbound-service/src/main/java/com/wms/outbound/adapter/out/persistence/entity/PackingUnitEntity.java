package com.wms.outbound.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity backing {@code packing_unit}.
 *
 * <p>V4 created core columns; V11 added {@code carton_no},
 * {@code packing_type}, dimensions, {@code notes}, {@code version},
 * {@code updated_at}.
 */
@Entity
@Table(name = "packing_unit")
public class PackingUnitEntity {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    /** Bootstrap-era column; not used in v1. */
    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;

    @Column(name = "carton_no", length = 40)
    private String cartonNo;

    @Column(name = "packing_type", length = 20)
    private String packingType;

    @Column(name = "weight_grams")
    private Integer weightGrams;

    @Column(name = "length_mm")
    private Integer lengthMm;

    @Column(name = "width_mm")
    private Integer widthMm;

    @Column(name = "height_mm")
    private Integer heightMm;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "packed_at")
    private Instant packedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PackingUnitEntity() {
    }

    public PackingUnitEntity(UUID id, UUID orderId, String cartonNo, String packingType,
                             Integer weightGrams, Integer lengthMm, Integer widthMm,
                             Integer heightMm, String notes, String status,
                             Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.orderId = orderId;
        this.cartonNo = cartonNo;
        this.packingType = packingType;
        this.weightGrams = weightGrams;
        this.lengthMm = lengthMm;
        this.widthMm = widthMm;
        this.heightMm = heightMm;
        this.notes = notes;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public String getCartonNo() { return cartonNo; }
    public String getPackingType() { return packingType; }
    public Integer getWeightGrams() { return weightGrams; }
    public Integer getLengthMm() { return lengthMm; }
    public Integer getWidthMm() { return widthMm; }
    public Integer getHeightMm() { return heightMm; }
    public String getNotes() { return notes; }
    public String getStatus() { return status; }
    public Instant getPackedAt() { return packedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    public void setStatus(String status) { this.status = status; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setPackedAt(Instant packedAt) { this.packedAt = packedAt; }
}
