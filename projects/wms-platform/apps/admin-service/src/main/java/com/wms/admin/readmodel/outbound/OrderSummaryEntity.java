package com.wms.admin.readmodel.outbound;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Projected from {@code wms.outbound.order.received|cancelled}. Per
 * {@code domain-model.md § 8}.
 *
 * <p>{@code tenantId} is the isolation axis of this projection (ADR-MONO-065 § D1) —
 * it comes from the source event's <b>envelope</b> and is stored verbatim. It is
 * nullable on purpose: rows projected before that decision are not back-stamped, and
 * orders created by an unrestricted caller genuinely have no tenant. Such rows are
 * visible only to unrestricted readers.
 */
@Entity
@Table(name = "admin_order_summary")
public class OrderSummaryEntity {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    @Column(name = "order_no", nullable = false, length = 80)
    private String orderNo;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "customer_partner_id")
    private UUID customerPartnerId;

    @Column(name = "customer_name", length = 200)
    private String customerName;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(length = 40)
    private String source;

    @Column(name = "required_ship_date")
    private LocalDate requiredShipDate;

    @Column(name = "line_count", nullable = false)
    private int lineCount;

    @Column(name = "saga_state", length = 40)
    private String sagaState;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "last_event_at", nullable = false)
    private Instant lastEventAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected OrderSummaryEntity() {
    }

    /**
     * {@code tenantId} is last, after an {@code Instant}, deliberately: this class has
     * five adjacent {@code String} parameters, so appending the new one there would let
     * a stale call site keep compiling with the tenant silently landing in the wrong
     * field. After {@code lastEventAt} the arity change is a compile error instead.
     */
    public OrderSummaryEntity(UUID orderId, String orderNo, UUID warehouseId,
                              UUID customerPartnerId, String customerName, String status,
                              String source, LocalDate requiredShipDate, int lineCount,
                              String sagaState, Instant receivedAt, Instant shippedAt,
                              Instant lastEventAt, String tenantId) {
        this.tenantId = tenantId;
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.warehouseId = warehouseId;
        this.customerPartnerId = customerPartnerId;
        this.customerName = customerName;
        this.status = status;
        this.source = source;
        this.requiredShipDate = requiredShipDate;
        this.lineCount = lineCount;
        this.sagaState = sagaState;
        this.receivedAt = receivedAt;
        this.shippedAt = shippedAt;
        this.lastEventAt = lastEventAt;
    }

    /**
     * {@code tenantId} last, for the same reason as the constructor.
     *
     * <p>{@code order.received} is the authoritative statement of an order's tenant, so
     * this overwrites — including with {@code null}, which is a real value here ("this
     * order has no tenant") and not "unknown".
     */
    public void applyReceived(String orderNo, UUID warehouseId, UUID customerPartnerId,
                              String customerName, String source, LocalDate requiredShipDate,
                              int lineCount, Instant receivedAt, Instant lastEventAt,
                              String tenantId) {
        this.tenantId = tenantId;
        this.orderNo = orderNo;
        this.warehouseId = warehouseId;
        this.customerPartnerId = customerPartnerId;
        this.customerName = customerName;
        this.source = source;
        this.requiredShipDate = requiredShipDate;
        this.lineCount = lineCount;
        this.receivedAt = receivedAt;
        this.status = "RECEIVED";
        this.lastEventAt = lastEventAt;
    }

    public void applyCancelled(Instant lastEventAt) {
        this.status = "CANCELLED";
        this.lastEventAt = lastEventAt;
    }

    public void applyShipped(Instant shippedAt, Instant lastEventAt) {
        this.status = "SHIPPED";
        this.shippedAt = shippedAt;
        this.lastEventAt = lastEventAt;
    }

    public UUID getOrderId() { return orderId; }
    public String getTenantId() { return tenantId; }
    public String getOrderNo() { return orderNo; }
    public UUID getWarehouseId() { return warehouseId; }
    public UUID getCustomerPartnerId() { return customerPartnerId; }
    public String getCustomerName() { return customerName; }
    public String getStatus() { return status; }
    public String getSource() { return source; }
    public LocalDate getRequiredShipDate() { return requiredShipDate; }
    public int getLineCount() { return lineCount; }
    public String getSagaState() { return sagaState; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getShippedAt() { return shippedAt; }
    public Instant getLastEventAt() { return lastEventAt; }
    public long getVersion() { return version; }
}
