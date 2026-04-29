package com.wms.inbound.domain.exception;

import java.util.UUID;

public class WarehouseNotFoundInReadModelException extends InboundDomainException {
    public WarehouseNotFoundInReadModelException(UUID warehouseId) {
        super("Warehouse not found in read model: " + warehouseId);
    }

    @Override
    public String errorCode() {
        return "WAREHOUSE_NOT_FOUND";
    }
}
