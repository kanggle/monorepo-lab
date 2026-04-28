package com.wms.inventory.domain.exception;

public class InventoryNotFoundException extends InventoryDomainException {

    public InventoryNotFoundException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "INVENTORY_NOT_FOUND";
    }
}
