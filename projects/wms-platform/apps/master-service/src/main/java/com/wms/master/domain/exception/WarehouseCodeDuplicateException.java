package com.wms.master.domain.exception;

public class WarehouseCodeDuplicateException extends MasterDomainException {

    public WarehouseCodeDuplicateException(String warehouseCode) {
        super("WAREHOUSE_CODE_DUPLICATE", "warehouseCode is already taken: " + warehouseCode);
    }
}
