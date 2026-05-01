package com.wms.inbound.adapter.out.persistence.asn;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "asn")
class AsnJpaEntity {

    @Id
    private UUID id;

    @Column(name = "asn_no", nullable = false, length = 40, unique = true)
    private String asnNo;

    @Column(name = "source", nullable = false, length = 20)
    private String source;

    @Column(name = "supplier_partner_id", nullable = false)
    private UUID supplierPartnerId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "expected_arrive_date")
    private LocalDate expectedArriveDate;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "status", nullable = false, length = 20)
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

    @OneToMany(mappedBy = "asnId", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AsnLineJpaEntity> lines = new ArrayList<>();

    protected AsnJpaEntity() {}

    AsnJpaEntity(UUID id, String asnNo, String source, UUID supplierPartnerId,
                  UUID warehouseId, LocalDate expectedArriveDate, String notes,
                  String status, long version, Instant createdAt, String createdBy,
                  Instant updatedAt, String updatedBy) {
        this.id = id;
        this.asnNo = asnNo;
        this.source = source;
        this.supplierPartnerId = supplierPartnerId;
        this.warehouseId = warehouseId;
        this.expectedArriveDate = expectedArriveDate;
        this.notes = notes;
        this.status = status;
        this.version = version;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    UUID getId() { return id; }
    String getAsnNo() { return asnNo; }
    String getSource() { return source; }
    UUID getSupplierPartnerId() { return supplierPartnerId; }
    UUID getWarehouseId() { return warehouseId; }
    LocalDate getExpectedArriveDate() { return expectedArriveDate; }
    String getNotes() { return notes; }
    String getStatus() { return status; }
    long getVersion() { return version; }
    Instant getCreatedAt() { return createdAt; }
    String getCreatedBy() { return createdBy; }
    Instant getUpdatedAt() { return updatedAt; }
    String getUpdatedBy() { return updatedBy; }
    List<AsnLineJpaEntity> getLines() { return lines; }

    void setStatus(String status) { this.status = status; }
    void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    void setLines(List<AsnLineJpaEntity> lines) {
        this.lines.clear();
        this.lines.addAll(lines);
    }
}
