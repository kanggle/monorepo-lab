package com.wms.outbound.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Child entity of {@link PackingUnit}.
 *
 * <p>Authoritative reference:
 * {@code specs/services/outbound-service/domain-model.md} §4 (PackingUnitLine).
 */
public final class PackingUnitLine {

    private final UUID id;
    private final UUID packingUnitId;
    private final UUID orderLineId;
    private final UUID skuId;
    private final UUID lotId;
    private final int qty;

    public PackingUnitLine(UUID id,
                           UUID packingUnitId,
                           UUID orderLineId,
                           UUID skuId,
                           UUID lotId,
                           int qty) {
        this.id = Objects.requireNonNull(id, "id");
        this.packingUnitId = Objects.requireNonNull(packingUnitId, "packingUnitId");
        this.orderLineId = Objects.requireNonNull(orderLineId, "orderLineId");
        this.skuId = Objects.requireNonNull(skuId, "skuId");
        this.lotId = lotId;
        if (qty <= 0) {
            throw new IllegalArgumentException("qty must be > 0");
        }
        this.qty = qty;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPackingUnitId() {
        return packingUnitId;
    }

    public UUID getOrderLineId() {
        return orderLineId;
    }

    public UUID getSkuId() {
        return skuId;
    }

    public UUID getLotId() {
        return lotId;
    }

    public int getQty() {
        return qty;
    }
}
