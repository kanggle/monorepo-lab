package com.wms.outbound.domain.exception;

import java.util.UUID;

/**
 * Raised when an attempt is made to mutate an already-{@code SHIPPED} order
 * (e.g., cancellation). Mapped to {@code 422} with code
 * {@code ORDER_ALREADY_SHIPPED}. v1 forbids post-ship cancellation; v2
 * models returns as a separate RMA inbound flow.
 */
public class OrderAlreadyShippedException extends OutboundDomainException {

    private final UUID orderId;

    public OrderAlreadyShippedException(UUID orderId) {
        super("Order already shipped: " + orderId);
        this.orderId = orderId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    @Override
    public String errorCode() {
        return "ORDER_ALREADY_SHIPPED";
    }
}
