package com.wms.outbound.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Child entity of {@link Order}.
 *
 * <p>Authoritative reference:
 * {@code specs/services/outbound-service/domain-model.md} §1 (OrderLine).
 *
 * <p>Once the parent {@code Order} transitions to {@code PICKING}
 * (via {@link Order#startPicking}), an OrderLine becomes immutable —
 * the aggregate enforces this by rejecting any further line mutation.
 */
public final class OrderLine {

    private final UUID id;
    private final UUID orderId;
    private final int lineNo;
    private final UUID skuId;
    private final UUID lotId;
    private final int qtyOrdered;

    public OrderLine(UUID id,
                     UUID orderId,
                     int lineNo,
                     UUID skuId,
                     UUID lotId,
                     int qtyOrdered) {
        this.id = Objects.requireNonNull(id, "id");
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        if (lineNo < 1) {
            throw new IllegalArgumentException("lineNo must be >= 1");
        }
        this.lineNo = lineNo;
        this.skuId = Objects.requireNonNull(skuId, "skuId");
        this.lotId = lotId;
        if (qtyOrdered <= 0) {
            throw new IllegalArgumentException("qtyOrdered must be > 0");
        }
        this.qtyOrdered = qtyOrdered;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public int getLineNo() {
        return lineNo;
    }

    public UUID getSkuId() {
        return skuId;
    }

    public UUID getLotId() {
        return lotId;
    }

    public int getQtyOrdered() {
        return qtyOrdered;
    }
}
