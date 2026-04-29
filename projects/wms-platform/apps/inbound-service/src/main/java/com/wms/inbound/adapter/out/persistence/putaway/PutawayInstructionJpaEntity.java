package com.wms.inbound.adapter.out.persistence.putaway;

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
@Table(name = "putaway_instruction")
class PutawayInstructionJpaEntity {

    @Id
    private UUID id;

    @Column(name = "asn_id", nullable = false, unique = true)
    private UUID asnId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "planned_by", nullable = false, length = 100)
    private String plannedBy;

    @Column(name = "status", nullable = false, length = 40)
    private String status;

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

    @OneToMany(mappedBy = "putawayInstructionId", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PutawayLineJpaEntity> lines = new ArrayList<>();

    protected PutawayInstructionJpaEntity() {}

    PutawayInstructionJpaEntity(UUID id, UUID asnId, UUID warehouseId, String plannedBy,
                                 String status, long version,
                                 Instant createdAt, String createdBy,
                                 Instant updatedAt, String updatedBy) {
        this.id = id;
        this.asnId = asnId;
        this.warehouseId = warehouseId;
        this.plannedBy = plannedBy;
        this.status = status;
        this.version = version;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    UUID getId() { return id; }
    UUID getAsnId() { return asnId; }
    UUID getWarehouseId() { return warehouseId; }
    String getPlannedBy() { return plannedBy; }
    String getStatus() { return status; }
    long getVersion() { return version; }
    Instant getCreatedAt() { return createdAt; }
    String getCreatedBy() { return createdBy; }
    Instant getUpdatedAt() { return updatedAt; }
    String getUpdatedBy() { return updatedBy; }
    List<PutawayLineJpaEntity> getLines() { return lines; }

    void setStatus(String status) { this.status = status; }
    void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    void setLines(List<PutawayLineJpaEntity> lines) {
        this.lines.clear();
        this.lines.addAll(lines);
    }
}
