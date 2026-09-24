package com.wms.outbound.domain.exception;

import java.util.UUID;

/**
 * Raised when a pick-confirmation or packing line does not describe the order line it
 * names: the {@code orderLineId} is not one of this order's lines, or the {@code skuId}
 * is not that order line's SKU. Mapped to {@code 422} with code
 * {@code ORDER_LINE_MISMATCH} (TASK-BE-596).
 *
 * <p>Before this check the request's values were stored and published as-is, so the
 * pick/pack records and the admin projection could name a SKU the order never had.
 */
public class OrderLineMismatchException extends OutboundDomainException {

    private final UUID orderLineId;

    public OrderLineMismatchException(UUID orderLineId, String detail) {
        super("order line mismatch on orderLineId=" + orderLineId + ": " + detail);
        this.orderLineId = orderLineId;
    }

    public UUID getOrderLineId() {
        return orderLineId;
    }

    @Override
    public String errorCode() {
        return "ORDER_LINE_MISMATCH";
    }
}
