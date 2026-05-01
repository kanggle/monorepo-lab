package com.wms.outbound.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity backing {@code outbound_saga}.
 *
 * <p>Aligned with {@code specs/services/outbound-service/domain-model.md} §6
 * after TASK-BE-037. Status is the spec-canonical
 * {@code OutboundSaga.SagaStatus} name.
 */
@Entity
@Table(name = "outbound_saga")
public class OutboundSagaEntity {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "picking_request_id")
    private UUID pickingRequestId;

    @Column(name = "shipment_id")
    private UUID shipmentId;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected OutboundSagaEntity() {
    }

    public OutboundSagaEntity(UUID id, UUID orderId, String status,
                              UUID pickingRequestId,
                              String failureReason,
                              Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.orderId = orderId;
        this.status = status;
        this.pickingRequestId = pickingRequestId;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getStatus() {
        return status;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public UUID getPickingRequestId() {
        return pickingRequestId;
    }

    public UUID getShipmentId() {
        return shipmentId;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void setPickingRequestId(UUID pickingRequestId) {
        this.pickingRequestId = pickingRequestId;
    }
}
