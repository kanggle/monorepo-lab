package com.wms.inbound.domain.exception;

import java.util.UUID;

public class WarehouseMismatchException extends InboundDomainException {
    public WarehouseMismatchException(UUID expectedWarehouseId, UUID actualWarehouseId) {
        super("Location belongs to warehouse " + actualWarehouseId
                + " but ASN warehouse is " + expectedWarehouseId);
    }

    @Override
    public String errorCode() {
        return "WAREHOUSE_MISMATCH";
    }
}
