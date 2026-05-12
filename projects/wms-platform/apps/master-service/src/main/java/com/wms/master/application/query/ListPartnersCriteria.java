package com.wms.master.application.query;

import com.wms.master.domain.model.PartnerType;
import com.wms.master.domain.model.WarehouseStatus;

/**
 * Filter criteria for listing partners.
 *
 * @param status      null = any status; concrete value filters to that status
 *                    only (default at REST layer is {@link WarehouseStatus#ACTIVE})
 * @param q           case-insensitive substring against {@code partner_code} and
 *                    {@code name}; null or blank means no text filter
 * @param partnerType null = any; concrete value filters by partner type
 */
public record ListPartnersCriteria(
        WarehouseStatus status,
        String q,
        PartnerType partnerType) {

    public static ListPartnersCriteria any() {
        return new ListPartnersCriteria(null, null, null);
    }

    public boolean hasQueryText() {
        return q != null && !q.isBlank();
    }
}
