package com.wms.inbound.domain.exception;

import java.util.UUID;

public class SkuInactiveException extends InboundDomainException {
    public SkuInactiveException(UUID skuId) {
        super("SKU is inactive: " + skuId);
    }

    @Override
    public String errorCode() {
        return "SKU_INACTIVE";
    }
}
