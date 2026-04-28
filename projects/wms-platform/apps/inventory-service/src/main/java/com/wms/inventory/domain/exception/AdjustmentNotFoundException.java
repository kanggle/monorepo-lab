package com.wms.inventory.domain.exception;

/**
 * Maps to {@code 404 ADJUSTMENT_NOT_FOUND}.
 */
public class AdjustmentNotFoundException extends InventoryDomainException {

    public AdjustmentNotFoundException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "ADJUSTMENT_NOT_FOUND";
    }
}
