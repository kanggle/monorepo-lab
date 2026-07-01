package com.wms.outbound.domain.exception;

/**
 * Raised when an attempt is made to insert an order with an
 * {@code orderNo} that already exists. Mapped to {@code 409} with code
 * {@code ORDER_NO_DUPLICATE} (per {@code outbound-service} § Error Codes /
 * {@code platform/error-handling.md}; mirror of inbound {@code ASN_NO_DUPLICATE}).
 */
public class OrderNoDuplicateException extends OutboundDomainException {

    private final String orderNo;

    public OrderNoDuplicateException(String orderNo) {
        super("Order number already exists: " + orderNo);
        this.orderNo = orderNo;
    }

    public String getOrderNo() {
        return orderNo;
    }

    @Override
    public String errorCode() {
        return "ORDER_NO_DUPLICATE";
    }
}
