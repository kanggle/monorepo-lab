package com.wms.outbound.domain.exception;

/**
 * Raised when shipping confirmation cannot proceed because not all packing
 * units are sealed and/or sum of {@code PackingUnitLine.qty} does not cover
 * the order line's {@code qty_ordered}. Mapped to {@code 422} with code
 * {@code PACKING_INCOMPLETE}.
 */
public class PackingIncompleteException extends OutboundDomainException {

    public PackingIncompleteException(String detail) {
        super("Packing incomplete: " + detail);
    }

    @Override
    public String errorCode() {
        return "PACKING_INCOMPLETE";
    }
}
