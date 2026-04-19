package com.wms.master.application.query;

import com.wms.master.domain.model.WarehouseStatus;
import com.wms.master.domain.model.ZoneType;
import java.util.UUID;

/**
 * Filter criteria for listing zones under a specific warehouse.
 *
 * @param warehouseId required parent warehouse id (all list endpoints are nested)
 * @param status      null = any status; concrete value filters to that status only
 *                    (default at REST layer is {@link WarehouseStatus#ACTIVE})
 * @param zoneType    null = any zoneType; concrete value filters to that type
 */
public record ListZonesCriteria(
        UUID warehouseId,
        WarehouseStatus status,
        ZoneType zoneType) {

    public static ListZonesCriteria forWarehouse(UUID warehouseId) {
        return new ListZonesCriteria(warehouseId, null, null);
    }
}
