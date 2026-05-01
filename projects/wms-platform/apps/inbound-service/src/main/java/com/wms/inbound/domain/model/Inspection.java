package com.wms.inbound.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Inspection {

    private final UUID id;
    private final UUID asnId;
    private final String inspectorId;
    private Instant completedAt;
    private final String notes;
    private long version;
    private final Instant createdAt;
    private final String createdBy;
    private Instant updatedAt;
    private String updatedBy;

    private final List<InspectionLine> lines;
    private final List<InspectionDiscrepancy> discrepancies;

    public Inspection(UUID id, UUID asnId, String inspectorId,
                      Instant completedAt, String notes,
                      long version,
                      Instant createdAt, String createdBy,
                      Instant updatedAt, String updatedBy,
                      List<InspectionLine> lines,
                      List<InspectionDiscrepancy> discrepancies) {
        this.id = id;
        this.asnId = asnId;
        this.inspectorId = inspectorId;
        this.completedAt = completedAt;
        this.notes = notes;
        this.version = version;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
        this.lines = new ArrayList<>(lines);
        this.discrepancies = new ArrayList<>(discrepancies);
    }

    public long countUnacknowledgedDiscrepancies() {
        return discrepancies.stream().filter(d -> !d.isAcknowledged()).count();
    }

    public boolean allDiscrepanciesAcknowledged() {
        return discrepancies.stream().allMatch(InspectionDiscrepancy::isAcknowledged);
    }

    public UUID getId() { return id; }
    public UUID getAsnId() { return asnId; }
    public String getInspectorId() { return inspectorId; }
    public Instant getCompletedAt() { return completedAt; }
    public String getNotes() { return notes; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
    public List<InspectionLine> getLines() { return Collections.unmodifiableList(lines); }
    public List<InspectionDiscrepancy> getDiscrepancies() { return Collections.unmodifiableList(discrepancies); }
}
