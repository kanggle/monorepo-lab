package com.wms.master.application.query;

import com.wms.master.domain.model.WarehouseStatus;

/**
 * Filter criteria for listing warehouses.
 *
 * @param status null = any status; concrete value filters to that status only
 *               (default at REST layer is {@link WarehouseStatus#ACTIVE})
 * @param q      case-insensitive substring against warehouse_code and name;
 *               null or blank means no text filter
 */
public record WarehouseListCriteria(WarehouseStatus status, String q) {

    public static WarehouseListCriteria any() {
        return new WarehouseListCriteria(null, null);
    }

    public boolean hasQueryText() {
        return q != null && !q.isBlank();
    }
}
