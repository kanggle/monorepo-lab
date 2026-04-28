package com.wms.inventory.domain.exception;

/**
 * Domain validation failures (e.g., {@code qty <= 0}). Maps to
 * {@code 400 VALIDATION_ERROR}.
 */
public class InventoryValidationException extends InventoryDomainException {

    public InventoryValidationException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "VALIDATION_ERROR";
    }
}
