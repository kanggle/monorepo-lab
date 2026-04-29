package com.wms.inbound.domain.model;

import java.time.Instant;
import java.util.UUID;

public class InspectionDiscrepancy {

    private final UUID id;
    private final UUID inspectionId;
    private final UUID asnLineId;
    private final DiscrepancyType discrepancyType;
    private final int expectedQty;
    private final int actualTotalQty;
    private final int variance;
    private boolean acknowledged;
    private String acknowledgedBy;
    private Instant acknowledgedAt;
    private final String notes;

    public InspectionDiscrepancy(UUID id, UUID inspectionId, UUID asnLineId,
                                  DiscrepancyType discrepancyType,
                                  int expectedQty, int actualTotalQty, int variance,
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

    public static InspectionDiscrepancy createNew(UUID id, UUID inspectionId, UUID asnLineId,
                                                   DiscrepancyType type,
                                                   int expectedQty, int actualTotalQty) {
        return new InspectionDiscrepancy(id, inspectionId, asnLineId, type,
                expectedQty, actualTotalQty, actualTotalQty - expectedQty,
                false, null, null, null);
    }

    public void acknowledge(String actorId, Instant at) {
        this.acknowledged = true;
        this.acknowledgedBy = actorId;
        this.acknowledgedAt = at;
    }

    public UUID getId() { return id; }
    public UUID getInspectionId() { return inspectionId; }
    public UUID getAsnLineId() { return asnLineId; }
    public DiscrepancyType getDiscrepancyType() { return discrepancyType; }
    public int getExpectedQty() { return expectedQty; }
    public int getActualTotalQty() { return actualTotalQty; }
    public int getVariance() { return variance; }
    public boolean isAcknowledged() { return acknowledged; }
    public String getAcknowledgedBy() { return acknowledgedBy; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public String getNotes() { return notes; }
}
