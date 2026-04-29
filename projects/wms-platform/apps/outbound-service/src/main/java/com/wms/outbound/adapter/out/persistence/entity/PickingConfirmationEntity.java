package com.wms.outbound.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity backing {@code picking_confirmation}.
 *
 * <p>V3 created core columns; V11 added {@code order_id} and {@code notes}.
 */
@Entity
@Table(name = "picking_confirmation")
public class PickingConfirmationEntity {

    @Id
    private UUID id;

    @Column(name = "picking_request_id", nullable = false)
    private UUID pickingRequestId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "confirmed_by", length = 100)
    private String confirmedBy;

    @Column(name = "confirmed_at", nullable = false)
    private Instant confirmedAt;

    @Column(name = "notes", length = 500)
    private String notes;

    protected PickingConfirmationEntity() {
    }

    public PickingConfirmationEntity(UUID id, UUID pickingRequestId, UUID orderId,
                                     String confirmedBy, Instant confirmedAt, String notes) {
        this.id = id;
        this.pickingRequestId = pickingRequestId;
        this.orderId = orderId;
        this.confirmedBy = confirmedBy;
        this.confirmedAt = confirmedAt;
        this.notes = notes;
    }

    public UUID getId() { return id; }
    public UUID getPickingRequestId() { return pickingRequestId; }
    public UUID getOrderId() { return orderId; }
    public String getConfirmedBy() { return confirmedBy; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public String getNotes() { return notes; }
}
