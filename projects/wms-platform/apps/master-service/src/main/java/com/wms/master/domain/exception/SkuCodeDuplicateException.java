package com.wms.master.domain.exception;

public class SkuCodeDuplicateException extends MasterDomainException {

    public SkuCodeDuplicateException(String skuCode) {
        super("SKU_CODE_DUPLICATE", "skuCode is already taken: " + skuCode);
    }
}
