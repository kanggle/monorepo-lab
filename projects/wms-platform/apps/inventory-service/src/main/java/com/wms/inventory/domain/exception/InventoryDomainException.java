package com.wms.inventory.domain.exception;

/**
 * Base type for inventory domain exceptions. Each subclass carries an
 * {@link #errorCode()} that maps to a stable HTTP error code per
 * {@code specs/contracts/http/inventory-service-api.md} § Error Envelope.
 */
public abstract class InventoryDomainException extends RuntimeException {

    protected InventoryDomainException(String message) {
        super(message);
    }

    protected InventoryDomainException(String message, Throwable cause) {
        super(message, cause);
    }

    public abstract String errorCode();
}
