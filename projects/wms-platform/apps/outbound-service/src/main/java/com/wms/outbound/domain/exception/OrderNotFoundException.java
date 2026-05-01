package com.wms.outbound.domain.exception;

import java.util.UUID;

/**
 * Raised when an Order id cannot be located. Mapped to {@code 404}
 * with code {@code ORDER_NOT_FOUND}.
 */
public class OrderNotFoundException extends OutboundDomainException {

    private final UUID orderId;

    public OrderNotFoundException(UUID orderId) {
        super("Outbound order not found: " + orderId);
        this.orderId = orderId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    @Override
    public String errorCode() {
        return "ORDER_NOT_FOUND";
    }
}
