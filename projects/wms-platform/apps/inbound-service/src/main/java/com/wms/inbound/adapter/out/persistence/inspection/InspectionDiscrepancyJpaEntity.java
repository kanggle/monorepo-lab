package com.wms.inbound.adapter.out.persistence.inspection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inspection_discrepancy")
class InspectionDiscrepancyJpaEntity {

    @Id
    private UUID id;

    @Column(name = "inspection_id", nullable = false)
    private UUID inspectionId;

    @Column(name = "asn_line_id", nullable = false)
    private UUID asnLineId;

    @Column(name = "discrepancy_type", nullable = false, length = 40)
    private String discrepancyType;

    @Column(name = "expected_qty", nullable = false)
    private int expectedQty;

    @Column(name = "actual_total_qty", nullable = false)
    private int actualTotalQty;

    @Column(name = "variance", nullable = false)
    private int variance;

    @Column(name = "acknowledged", nullable = false)
    private boolean acknowledged;

    @Column(name = "acknowledged_by", length = 100)
    private String acknowledgedBy;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "notes", length = 500)
    private String notes;

    protected InspectionDiscrepancyJpaEntity() {}

    InspectionDiscrepancyJpaEntity(UUID id, UUID inspectionId, UUID asnLineId,
                                    String discrepancyType, int expectedQty,
                                    int actualTotalQty, int variance,
                                    boolean acknowledged, String acknowledgedBy,
                                    Instant acknowledgedAt, String notes) {
        this.id = id;
        this.inspectionId = inspectionId;
        this.asnLineId = asnLineId;
        this.discrepancyType = discrepancyType;
        this.expectedQty = expectedQty;
        this.actualTotalQty = actualTotalQty;
        this.variance = variance;
        this.acknowledged = acknowledged;
        this.acknowledgedBy = acknowledgedBy;
        this.acknowledgedAt = acknowledgedAt;
        this.notes = notes;
    }

    UUID getId() { return id; }
    UUID getInspectionId() { return inspectionId; }
    UUID getAsnLineId() { return asnLineId; }
    String getDiscrepancyType() { return discrepancyType; }
    int getExpectedQty() { return expectedQty; }
    int getActualTotalQty() { return actualTotalQty; }
    int getVariance() { return variance; }
    boolean isAcknowledged() { return acknowledged; }
    String getAcknowledgedBy() { return acknowledgedBy; }
    Instant getAcknowledgedAt() { return acknowledgedAt; }
    String getNotes() { return notes; }

    void setAcknowledged(boolean acknowledged) { this.acknowledged = acknowledged; }
    void setAcknowledgedBy(String acknowledgedBy) { this.acknowledgedBy = acknowledgedBy; }
    void setAcknowledgedAt(Instant acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
}
