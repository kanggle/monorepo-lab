package com.wms.inbound.domain.exception;

import java.util.UUID;

public class LotRequiredException extends InboundDomainException {
    public LotRequiredException(UUID skuId) {
        super("LOT information is required for LOT-tracked SKU: " + skuId);
    }

    @Override
    public String errorCode() {
        return "LOT_REQUIRED";
    }
}
