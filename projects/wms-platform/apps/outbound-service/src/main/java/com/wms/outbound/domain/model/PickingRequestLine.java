package com.wms.outbound.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Child entity of {@link PickingRequest}.
 *
 * <p>Authoritative reference:
 * {@code specs/services/outbound-service/domain-model.md} §2 (PickingRequestLine).
 */
public final class PickingRequestLine {

    private final UUID id;
    private final UUID pickingRequestId;
    private final UUID orderLineId;
    private final UUID skuId;
    private final UUID lotId;
    private final UUID locationId;
    private final int qtyToPick;

    public PickingRequestLine(UUID id,
                              UUID pickingRequestId,
                              UUID orderLineId,
                              UUID skuId,
                              UUID lotId,
                              UUID locationId,
                              int qtyToPick) {
        this.id = Objects.requireNonNull(id, "id");
        this.pickingRequestId = Objects.requireNonNull(pickingRequestId, "pickingRequestId");
        this.orderLineId = Objects.requireNonNull(orderLineId, "orderLineId");
        this.skuId = Objects.requireNonNull(skuId, "skuId");
        this.lotId = lotId;
        this.locationId = Objects.requireNonNull(locationId, "locationId");
        if (qtyToPick <= 0) {
            throw new IllegalArgumentException("qtyToPick must be > 0");
        }
        this.qtyToPick = qtyToPick;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPickingRequestId() {
        return pickingRequestId;
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

    public UUID getLocationId() {
        return locationId;
    }

    public int getQtyToPick() {
        return qtyToPick;
    }
}
