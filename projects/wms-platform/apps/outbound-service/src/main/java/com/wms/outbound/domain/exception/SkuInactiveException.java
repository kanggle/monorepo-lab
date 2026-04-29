package com.wms.outbound.domain.exception;

import java.util.UUID;

/**
 * Raised when an order line references an inactive (or unknown) SKU per
 * the local MasterReadModel. Mapped to {@code 422} with code
 * {@code SKU_INACTIVE}.
 */
public class SkuInactiveException extends OutboundDomainException {

    private final UUID skuId;

    public SkuInactiveException(UUID skuId) {
        super("SKU is not ACTIVE in master read model: " + skuId);
        this.skuId = skuId;
    }

    public UUID getSkuId() {
        return skuId;
    }

    @Override
    public String errorCode() {
        return "SKU_INACTIVE";
    }
}
