package com.wms.master.domain.exception;

import java.util.UUID;

/**
 * Thrown when a Lot insert / rename violates the
 * {@code uq_lots_sku_lotno} per-SKU uniqueness constraint. Mapped to HTTP 409
 * {@code LOT_NO_DUPLICATE} by {@code GlobalExceptionHandler}.
 */
public class LotNoDuplicateException extends MasterDomainException {

    public LotNoDuplicateException(UUID skuId, String lotNo) {
        super(
                "LOT_NO_DUPLICATE",
                "lotNo is already taken for sku " + skuId + ": " + lotNo);
    }
}
