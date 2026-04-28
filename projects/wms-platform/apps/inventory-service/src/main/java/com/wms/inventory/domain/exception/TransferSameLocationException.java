package com.wms.inventory.domain.exception;

/**
 * Thrown when a stock transfer's source and target locations are identical.
 * Maps to {@code 422 TRANSFER_SAME_LOCATION}.
 */
public class TransferSameLocationException extends InventoryDomainException {

    public TransferSameLocationException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "TRANSFER_SAME_LOCATION";
    }
}
