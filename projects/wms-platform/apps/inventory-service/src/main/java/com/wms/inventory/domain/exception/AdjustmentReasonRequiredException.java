package com.wms.inventory.domain.exception;

/**
 * Thrown when a stock adjustment is submitted without a {@code reasonNote}
 * (or with one shorter than 3 non-blank chars). Maps to
 * {@code 400 ADJUSTMENT_REASON_REQUIRED}.
 */
public class AdjustmentReasonRequiredException extends InventoryDomainException {

    public AdjustmentReasonRequiredException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "ADJUSTMENT_REASON_REQUIRED";
    }
}
