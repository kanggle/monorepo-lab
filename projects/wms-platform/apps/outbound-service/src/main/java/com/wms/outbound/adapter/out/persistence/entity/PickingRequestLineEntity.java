package com.wms.outbound.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * JPA entity backing {@code picking_request_line}.
 *
 * <p>V3 created columns {@code requested_qty} and {@code picked_qty}; the
 * spec field is {@code qty_to_pick}, mapped to {@code requested_qty} for
 * zero-downtime compatibility.
 */
@Entity
@Table(name = "picking_request_line")
public class PickingRequestLineEntity {

    @Id
    private UUID id;

    @Column(name = "picking_request_id", nullable = false)
    private UUID pickingRequestId;

    @Column(name = "order_line_id", nullable = false)
    private UUID orderLineId;

    @Column(name = "sku_id", nullable = false)
    private UUID skuId;

    @Column(name = "lot_id")
    private UUID lotId;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "requested_qty", nullable = false)
    private int qtyToPick;

    @Column(name = "picked_qty", nullable = false)
    private int pickedQty;

    protected PickingRequestLineEntity() {
    }

    public PickingRequestLineEntity(UUID id, UUID pickingRequestId, UUID orderLineId,
                                    UUID skuId, UUID lotId, UUID locationId, int qtyToPick) {
        this.id = id;
        this.pickingRequestId = pickingRequestId;
        this.orderLineId = orderLineId;
        this.skuId = skuId;
        this.lotId = lotId;
        this.locationId = locationId;
        this.qtyToPick = qtyToPick;
        this.pickedQty = 0;
    }

    public UUID getId() { return id; }
    public UUID getPickingRequestId() { return pickingRequestId; }
    public UUID getOrderLineId() { return orderLineId; }
    public UUID getSkuId() { return skuId; }
    public UUID getLotId() { return lotId; }
    public UUID getLocationId() { return locationId; }
    public int getQtyToPick() { return qtyToPick; }
    public int getPickedQty() { return pickedQty; }
}
