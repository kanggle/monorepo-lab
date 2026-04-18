package com.wms.master.domain.exception;

public class WarehouseNotFoundException extends MasterDomainException {

    public WarehouseNotFoundException(String identifier) {
        super("WAREHOUSE_NOT_FOUND", "Warehouse not found: " + identifier);
    }
}
