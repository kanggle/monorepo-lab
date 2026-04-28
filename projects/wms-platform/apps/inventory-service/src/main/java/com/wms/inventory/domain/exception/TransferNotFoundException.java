package com.wms.inventory.domain.exception;

/**
 * Maps to {@code 404 TRANSFER_NOT_FOUND}.
 */
public class TransferNotFoundException extends InventoryDomainException {

    public TransferNotFoundException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "TRANSFER_NOT_FOUND";
    }
}
