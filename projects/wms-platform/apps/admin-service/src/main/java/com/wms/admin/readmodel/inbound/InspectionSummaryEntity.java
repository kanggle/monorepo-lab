package com.wms.admin.readmodel.inbound;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Projected from {@code wms.inbound.inspection.completed.v1}. 1:1 per ASN.
 * Per {@code domain-model.md § 7}.
 */
@Entity
@Table(name = "admin_inspection_summary")
public class InspectionSummaryEntity {

    @Id
    @Column(name = "asn_id")
    private UUID asnId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "inspection_completed_at", nullable = false)
    private Instant inspectionCompletedAt;

    @Column(name = "inspector_id", length = 120)
    private String inspectorId;

    @Column(name = "total_lines", nullable = false)
    private int totalLines;

    @Column(name = "discrepancy_count", nullable = false)
    private int discrepancyCount;

    @Column(name = "total_qty_expected", nullable = false)
    private int totalQtyExpected;

    @Column(name = "total_qty_passed", nullable = false)
    private int totalQtyPassed;

    @Column(name = "total_qty_damaged", nullable = false)
    private int totalQtyDamaged;

    @Column(name = "total_qty_short", nullable = false)
    private int totalQtyShort;

    @Column(name = "last_event_at", nullable = false)
    private Instant lastEventAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected InspectionSummaryEntity() {
    }

    public InspectionSummaryEntity(UUID asnId, UUID warehouseId, Instant inspectionCompletedAt,
                                   String inspectorId, int totalLines, int discrepancyCount,
                                   int totalQtyExpected, int totalQtyPassed,
                                   int totalQtyDamaged, int totalQtyShort, Instant lastEventAt) {
        this.asnId = asnId;
        this.warehouseId = warehouseId;
        this.inspectionCompletedAt = inspectionCompletedAt;
        this.inspectorId = inspectorId;
        this.totalLines = totalLines;
        this.discrepancyCount = discrepancyCount;
        this.totalQtyExpected = totalQtyExpected;
        this.totalQtyPassed = totalQtyPassed;
        this.totalQtyDamaged = totalQtyDamaged;
        this.totalQtyShort = totalQtyShort;
        this.lastEventAt = lastEventAt;
    }

    public void apply(UUID warehouseId, Instant inspectionCompletedAt, String inspectorId,
                      int totalLines, int discrepancyCount, int totalQtyExpected,
                      int totalQtyPassed, int totalQtyDamaged, int totalQtyShort,
                      Instant lastEventAt) {
        this.warehouseId = warehouseId;
        this.inspectionCompletedAt = inspectionCompletedAt;
        this.inspectorId = inspectorId;
        this.totalLines = totalLines;
        this.discrepancyCount = discrepancyCount;
        this.totalQtyExpected = totalQtyExpected;
        this.totalQtyPassed = totalQtyPassed;
        this.totalQtyDamaged = totalQtyDamaged;
        this.totalQtyShort = totalQtyShort;
        this.lastEventAt = lastEventAt;
    }

    public UUID getAsnId() { return asnId; }
    public UUID getWarehouseId() { return warehouseId; }
    public Instant getInspectionCompletedAt() { return inspectionCompletedAt; }
    public String getInspectorId() { return inspectorId; }
    public int getTotalLines() { return totalLines; }
    public int getDiscrepancyCount() { return discrepancyCount; }
    public int getTotalQtyExpected() { return totalQtyExpected; }
    public int getTotalQtyPassed() { return totalQtyPassed; }
    public int getTotalQtyDamaged() { return totalQtyDamaged; }
    public int getTotalQtyShort() { return totalQtyShort; }
    public Instant getLastEventAt() { return lastEventAt; }
    public long getVersion() { return version; }
}
