package com.wms.admin.readmodel.alert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only projection of {@code wms.inventory.alert.v1} (low-stock /
 * anomaly alerts). PK = source eventId. Per {@code domain-model.md § 12}.
 *
 * <p><strong>The only mutable columns from the application layer are
 * {@code acknowledged_at} and {@code acknowledged_by}.</strong> All other
 * columns are immutable post-projection-insert. Acknowledgement is the single
 * documented exception to the "read-model written by projections only" rule
 * (architecture.md § 1.6 Justification).
 */
@Entity
@Table(name = "admin_alert_log")
public class AlertLogEntity {

    @Id
    private UUID id;

    @Column(name = "alert_type", nullable = false, length = 40)
    private String alertType;

    @Column(name = "warehouse_id")
    private UUID warehouseId;

    @Column(name = "location_id")
    private UUID locationId;

    @Column(name = "sku_id")
    private UUID skuId;

    @Column(name = "lot_id")
    private UUID lotId;

    @Column(name = "threshold_qty")
    private Integer thresholdQty;

    @Column(name = "actual_qty")
    private Integer actualQty;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "acknowledged_by", length = 120)
    private String acknowledgedBy;

    @Column(name = "projected_at", nullable = false)
    private Instant projectedAt;

    protected AlertLogEntity() {
    }

    public AlertLogEntity(UUID id, String alertType, UUID warehouseId, UUID locationId,
                          UUID skuId, UUID lotId, Integer thresholdQty, Integer actualQty,
                          Instant detectedAt, Instant projectedAt) {
        this.id = id;
        this.alertType = alertType;
        this.warehouseId = warehouseId;
        this.locationId = locationId;
        this.skuId = skuId;
        this.lotId = lotId;
        this.thresholdQty = thresholdQty;
        this.actualQty = actualQty;
        this.detectedAt = detectedAt;
        this.projectedAt = projectedAt;
    }

    /**
     * Mutates only {@code acknowledged_at} / {@code acknowledged_by}. All
     * other columns remain at their projection-inserted values.
     */
    public void acknowledge(String actorId, Instant acknowledgedAt) {
        if (this.acknowledgedAt != null) {
            throw new IllegalStateException("alert already acknowledged: " + id);
        }
        this.acknowledgedBy = actorId;
        this.acknowledgedAt = acknowledgedAt;
    }

    public UUID getId() { return id; }
    public String getAlertType() { return alertType; }
    public UUID getWarehouseId() { return warehouseId; }
    public UUID getLocationId() { return locationId; }
    public UUID getSkuId() { return skuId; }
    public UUID getLotId() { return lotId; }
    public Integer getThresholdQty() { return thresholdQty; }
    public Integer getActualQty() { return actualQty; }
    public Instant getDetectedAt() { return detectedAt; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public String getAcknowledgedBy() { return acknowledgedBy; }
    public Instant getProjectedAt() { return projectedAt; }
}
