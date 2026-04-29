package com.wms.outbound.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * JPA entity backing {@code picking_confirmation_line}.
 *
 * <p>V3 has columns {@code id}, {@code picking_confirmation_id},
 * {@code picking_line_id}, {@code picked_qty}. V11 added the spec columns
 * {@code order_line_id}, {@code sku_id}, {@code lot_id},
 * {@code actual_location_id}, {@code qty_confirmed}. The bootstrap
 * {@code picking_line_id} column is filled with {@code order_line_id} for
 * compatibility (it's reachable through the picking_request).
 */
@Entity
@Table(name = "picking_confirmation_line")
public class PickingConfirmationLineEntity {

    @Id
    private UUID id;

    @Column(name = "picking_confirmation_id", nullable = false)
    private UUID pickingConfirmationId;

    /** Bootstrap-era column. We mirror order_line_id here for compatibility. */
    @Column(name = "picking_line_id", nullable = false)
    private UUID pickingLineId;

    @Column(name = "order_line_id")
    private UUID orderLineId;

    @Column(name = "sku_id")
    private UUID skuId;

    @Column(name = "lot_id")
    private UUID lotId;

    @Column(name = "actual_location_id")
    private UUID actualLocationId;

    /** Bootstrap-era qty column; mirrored from qty_confirmed. */
    @Column(name = "picked_qty", nullable = false)
    private int pickedQty;

    @Column(name = "qty_confirmed")
    private Integer qtyConfirmed;

    protected PickingConfirmationLineEntity() {
    }

    public PickingConfirmationLineEntity(UUID id, UUID pickingConfirmationId,
                                         UUID orderLineId, UUID skuId, UUID lotId,
                                         UUID actualLocationId, int qtyConfirmed) {
        this.id = id;
        this.pickingConfirmationId = pickingConfirmationId;
        this.orderLineId = orderLineId;
        this.pickingLineId = orderLineId; // bootstrap mirror
        this.skuId = skuId;
        this.lotId = lotId;
        this.actualLocationId = actualLocationId;
        this.qtyConfirmed = qtyConfirmed;
        this.pickedQty = qtyConfirmed;
    }

    public UUID getId() { return id; }
    public UUID getPickingConfirmationId() { return pickingConfirmationId; }
    public UUID getOrderLineId() { return orderLineId; }
    public UUID getSkuId() { return skuId; }
    public UUID getLotId() { return lotId; }
    public UUID getActualLocationId() { return actualLocationId; }
    public Integer getQtyConfirmed() { return qtyConfirmed; }
    public int getPickedQty() { return pickedQty; }
}
