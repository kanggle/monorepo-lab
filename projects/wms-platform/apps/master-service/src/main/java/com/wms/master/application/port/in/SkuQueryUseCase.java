package com.wms.master.application.port.in;

import com.example.common.page.PageResult;
import com.wms.master.application.query.ListSkusQuery;
import com.wms.master.application.result.SkuResult;
import java.util.UUID;

/**
 * Inbound port for SKU read-side operations.
 */
public interface SkuQueryUseCase {

    SkuResult findById(UUID id);

    /**
     * Lookup by business sku_code. Input may be any case; the service uppercases
     * before delegating to the port.
     */
    SkuResult findBySkuCode(String skuCode);

    /**
     * Lookup by barcode (exact match, no case fold — EAN/UPC are digits only).
     */
    SkuResult findByBarcode(String barcode);

    PageResult<SkuResult> list(ListSkusQuery query);
}
