package com.wms.master.application.query;

import com.wms.master.domain.model.BaseUom;
import com.wms.master.domain.model.TrackingType;
import com.wms.master.domain.model.WarehouseStatus;

/**
 * Filter criteria for listing SKUs.
 *
 * @param status       null = any status; concrete value filters to that status
 *                     only (default at REST layer is {@link WarehouseStatus#ACTIVE})
 * @param q            case-insensitive substring against {@code sku_code} and
 *                     {@code name}; null or blank means no text filter
 * @param trackingType null = any; concrete value filters to that tracking type
 * @param baseUom      null = any; concrete value filters to that base UoM
 * @param barcode      null = any; concrete value is an exact match (no case fold,
 *                     no partial)
 */
public record ListSkusCriteria(
        WarehouseStatus status,
        String q,
        TrackingType trackingType,
        BaseUom baseUom,
        String barcode) {

    public static ListSkusCriteria any() {
        return new ListSkusCriteria(null, null, null, null, null);
    }

    public boolean hasQueryText() {
        return q != null && !q.isBlank();
    }
}
