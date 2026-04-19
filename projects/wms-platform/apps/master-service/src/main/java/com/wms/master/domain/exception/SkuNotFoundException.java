package com.wms.master.domain.exception;

public class SkuNotFoundException extends MasterDomainException {

    public SkuNotFoundException(String identifier) {
        super("SKU_NOT_FOUND", "SKU not found: " + identifier);
    }
}
