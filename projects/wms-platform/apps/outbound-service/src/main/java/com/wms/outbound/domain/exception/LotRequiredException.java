package com.wms.outbound.domain.exception;

import java.util.UUID;

/**
 * Raised when a LOT-tracked SKU's pick / pack / order line is missing
 * {@code lotId}. Mapped to {@code 422} with code {@code LOT_REQUIRED}.
 */
public class LotRequiredException extends OutboundDomainException {

    private final UUID skuId;

    public LotRequiredException(UUID skuId) {
        super("LOT id is required for LOT-tracked SKU: " + skuId);
        this.skuId = skuId;
    }

    public UUID getSkuId() {
        return skuId;
    }

    @Override
    public String errorCode() {
        return "LOT_REQUIRED";
    }
}
