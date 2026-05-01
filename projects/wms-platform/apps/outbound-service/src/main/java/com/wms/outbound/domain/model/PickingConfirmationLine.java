package com.wms.outbound.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Child entity of {@link PickingConfirmation}.
 *
 * <p>Authoritative reference:
 * {@code specs/services/outbound-service/domain-model.md} §3 (PickingConfirmationLine).
 */
public final class PickingConfirmationLine {

    private final UUID id;
    private final UUID pickingConfirmationId;
    private final UUID orderLineId;
    private final UUID skuId;
    private final UUID lotId;
    private final UUID actualLocationId;
    private final int qtyConfirmed;

    public PickingConfirmationLine(UUID id,
                                   UUID pickingConfirmationId,
                                   UUID orderLineId,
                                   UUID skuId,
                                   UUID lotId,
                                   UUID actualLocationId,
                                   int qtyConfirmed) {
        this.id = Objects.requireNonNull(id, "id");
        this.pickingConfirmationId = Objects.requireNonNull(pickingConfirmationId, "pickingConfirmationId");
        this.orderLineId = Objects.requireNonNull(orderLineId, "orderLineId");
        this.skuId = Objects.requireNonNull(skuId, "skuId");
        this.lotId = lotId;
        this.actualLocationId = Objects.requireNonNull(actualLocationId, "actualLocationId");
        if (qtyConfirmed <= 0) {
            throw new IllegalArgumentException("qtyConfirmed must be > 0");
        }
        this.qtyConfirmed = qtyConfirmed;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPickingConfirmationId() {
        return pickingConfirmationId;
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

    public UUID getActualLocationId() {
        return actualLocationId;
    }

    public int getQtyConfirmed() {
        return qtyConfirmed;
    }
}
