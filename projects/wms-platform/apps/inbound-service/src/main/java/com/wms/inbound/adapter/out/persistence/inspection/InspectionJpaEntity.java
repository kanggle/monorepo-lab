package com.wms.inbound.adapter.out.persistence.inspection;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "inspection")
class InspectionJpaEntity {

    @Id
    private UUID id;

    @Column(name = "asn_id", nullable = false, unique = true)
    private UUID asnId;

    @Column(name = "inspector_id", nullable = false, length = 100)
    private String inspectorId;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    @OneToMany(mappedBy = "inspectionId", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InspectionLineJpaEntity> lines = new ArrayList<>();

    @OneToMany(mappedBy = "inspectionId", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InspectionDiscrepancyJpaEntity> discrepancies = new ArrayList<>();

    protected InspectionJpaEntity() {}

    InspectionJpaEntity(UUID id, UUID asnId, String inspectorId, Instant completedAt,
                         String notes, long version, Instant createdAt, String createdBy,
                         Instant updatedAt, String updatedBy) {
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
    }

    UUID getId() { return id; }
    UUID getAsnId() { return asnId; }
    String getInspectorId() { return inspectorId; }
    Instant getCompletedAt() { return completedAt; }
    String getNotes() { return notes; }
    long getVersion() { return version; }
    Instant getCreatedAt() { return createdAt; }
    String getCreatedBy() { return createdBy; }
    Instant getUpdatedAt() { return updatedAt; }
    String getUpdatedBy() { return updatedBy; }
    List<InspectionLineJpaEntity> getLines() { return lines; }
    List<InspectionDiscrepancyJpaEntity> getDiscrepancies() { return discrepancies; }

    void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    void setLines(List<InspectionLineJpaEntity> lines) {
        this.lines.clear();
        this.lines.addAll(lines);
    }
    void setDiscrepancies(List<InspectionDiscrepancyJpaEntity> discrepancies) {
        this.discrepancies.clear();
        this.discrepancies.addAll(discrepancies);
    }
}
