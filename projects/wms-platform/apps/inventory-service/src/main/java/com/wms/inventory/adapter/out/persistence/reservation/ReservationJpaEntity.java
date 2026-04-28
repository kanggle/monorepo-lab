package com.wms.inventory.adapter.out.persistence.reservation;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "reservation")
public class ReservationJpaEntity {

    @Id
    private UUID id;

    @Column(name = "picking_request_id", nullable = false, unique = true)
    private UUID pickingRequestId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "released_reason", length = 20)
    private String releasedReason;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "reservation_id")
    private List<ReservationLineJpaEntity> lines = new ArrayList<>();

    protected ReservationJpaEntity() {
    }

    public ReservationJpaEntity(UUID id, UUID pickingRequestId, UUID warehouseId,
                                String status, Instant expiresAt, String releasedReason,
                                Instant confirmedAt, Instant releasedAt,
                                long version,
                                Instant createdAt, String createdBy,
                                Instant updatedAt, String updatedBy,
                                List<ReservationLineJpaEntity> lines) {
        this.id = id;
        this.pickingRequestId = pickingRequestId;
        this.warehouseId = warehouseId;
        this.status = status;
        this.expiresAt = expiresAt;
        this.releasedReason = releasedReason;
        this.confirmedAt = confirmedAt;
        this.releasedAt = releasedAt;
        this.version = version;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
        this.lines = new ArrayList<>(lines);
    }

    public UUID getId() { return id; }
    public UUID getPickingRequestId() { return pickingRequestId; }
    public UUID getWarehouseId() { return warehouseId; }
    public String getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getReleasedReason() { return releasedReason; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public Instant getReleasedAt() { return releasedAt; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
    public List<ReservationLineJpaEntity> getLines() { return lines; }

    void copyMutableFields(String status, String releasedReason,
                           Instant confirmedAt, Instant releasedAt,
                           Instant updatedAt, String updatedBy) {
        this.status = status;
        this.releasedReason = releasedReason;
        this.confirmedAt = confirmedAt;
        this.releasedAt = releasedAt;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }
}
