package com.wms.outbound.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity backing {@code outbound_order_line}. Children are loaded by the
 * persistence adapter explicitly (no JPA association) so the read path keeps
 * full control over fetch shape.
 *
 * <p>The schema also has a {@code requested_qty} column from the V2 bootstrap;
 * it is kept in sync with {@code qty_ordered} for zero-downtime compatibility.
 */
@Entity
@Table(name = "outbound_order_line")
public class OrderLineEntity {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "sku_id", nullable = false)
    private UUID skuId;

    @Column(name = "lot_id")
    private UUID lotId;

    @Column(name = "requested_qty", nullable = false)
    private int requestedQty;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OrderLineEntity() {
    }

    public OrderLineEntity(UUID id, UUID orderId, int lineNumber,
                           UUID skuId, UUID lotId, int requestedQty,
                           Instant createdAt) {
        this.id = id;
        this.orderId = orderId;
        this.lineNumber = lineNumber;
        this.skuId = skuId;
        this.lotId = lotId;
        this.requestedQty = requestedQty;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public int getLineNumber() { return lineNumber; }
    public UUID getSkuId() { return skuId; }
    public UUID getLotId() { return lotId; }
    public int getRequestedQty() { return requestedQty; }
    public Instant getCreatedAt() { return createdAt; }
}
