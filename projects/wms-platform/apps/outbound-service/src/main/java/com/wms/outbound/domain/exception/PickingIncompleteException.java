package com.wms.outbound.domain.exception;

/**
 * Raised when picking confirmation cannot proceed because at least one line
 * was not fully picked (qty_confirmed != qty_ordered). Mapped to {@code 422}
 * with code {@code PICKING_INCOMPLETE}.
 */
public class PickingIncompleteException extends OutboundDomainException {

    public PickingIncompleteException(String detail) {
        super("Picking incomplete: " + detail);
    }

    @Override
    public String errorCode() {
        return "PICKING_INCOMPLETE";
    }
}
