package com.wms.outbound.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * JPA entity backing {@code packing_unit_line}.
 */
@Entity
@Table(name = "packing_unit_line")
public class PackingUnitLineEntity {

    @Id
    private UUID id;

    @Column(name = "packing_unit_id", nullable = false)
    private UUID packingUnitId;

    @Column(name = "order_line_id", nullable = false)
    private UUID orderLineId;

    @Column(name = "sku_id", nullable = false)
    private UUID skuId;

    @Column(name = "lot_id")
    private UUID lotId;

    @Column(name = "packed_qty", nullable = false)
    private int qty;

    protected PackingUnitLineEntity() {
    }

    public PackingUnitLineEntity(UUID id, UUID packingUnitId, UUID orderLineId,
                                 UUID skuId, UUID lotId, int qty) {
        this.id = id;
        this.packingUnitId = packingUnitId;
        this.orderLineId = orderLineId;
        this.skuId = skuId;
        this.lotId = lotId;
        this.qty = qty;
    }

    public UUID getId() { return id; }
    public UUID getPackingUnitId() { return packingUnitId; }
    public UUID getOrderLineId() { return orderLineId; }
    public UUID getSkuId() { return skuId; }
    public UUID getLotId() { return lotId; }
    public int getQty() { return qty; }
}
