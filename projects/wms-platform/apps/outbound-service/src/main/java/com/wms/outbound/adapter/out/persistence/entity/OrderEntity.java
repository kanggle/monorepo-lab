package com.wms.outbound.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA entity backing {@code outbound_order}.
 *
 * <p>Columns align with {@code specs/services/outbound-service/domain-model.md} §1.
 * The {@code erp_order_number} column from V2 is retained (not dropped) for
 * zero-downtime compatibility; the new {@code order_no} column is the
 * authoritative business identifier going forward.
 */
@Entity
@Table(name = "outbound_order")
public class OrderEntity {

    @Id
    private UUID id;

    @Column(name = "order_no", length = 40)
    private String orderNo;

    @Column(name = "source", length = 20)
    private String source;

    @Column(name = "customer_partner_id")
    private UUID customerPartnerId;

    /** Bootstrap-era column. Retained for zero-downtime; populated from {@code orderNo}. */
    @Column(name = "erp_order_number", nullable = false, length = 100)
    private String erpOrderNumber;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    /** Bootstrap-era column. Mirror of {@code customerPartnerId} for now. */
    @Column(name = "partner_id", nullable = false)
    private UUID partnerId;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "requested_ship_date")
    private LocalDate requestedShipDate;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected OrderEntity() {
    }

    public OrderEntity(UUID id,
                       String orderNo,
                       String source,
                       UUID customerPartnerId,
                       UUID warehouseId,
                       String status,
                       LocalDate requestedShipDate,
                       String notes,
                       Instant createdAt,
                       String createdBy,
                       Instant updatedAt,
                       String updatedBy) {
        this.id = id;
        this.orderNo = orderNo;
        this.source = source;
        this.customerPartnerId = customerPartnerId;
        this.erpOrderNumber = orderNo; // bootstrap mirror
        this.warehouseId = warehouseId;
        this.partnerId = customerPartnerId; // bootstrap mirror
        this.status = status;
        this.requestedShipDate = requestedShipDate;
        this.notes = notes;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    public UUID getId() { return id; }
    public String getOrderNo() { return orderNo; }
    public String getSource() { return source; }
    public UUID getCustomerPartnerId() { return customerPartnerId; }
    public UUID getWarehouseId() { return warehouseId; }
    public String getStatus() { return status; }
    public LocalDate getRequestedShipDate() { return requestedShipDate; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
    public long getVersion() { return version; }

    public void setStatus(String status) { this.status = status; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public void setNotes(String notes) { this.notes = notes; }
}
