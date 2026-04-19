package com.wms.master.application.query;

import com.wms.master.domain.model.LocationType;
import com.wms.master.domain.model.WarehouseStatus;
import java.util.UUID;

/**
 * Filter criteria for listing locations. Unlike Zone (nested under warehouse),
 * Location list is flat — every filter is optional.
 *
 * @param warehouseId  null = any warehouse
 * @param zoneId       null = any zone
 * @param locationType null = any type
 * @param locationCode null = any code; non-null = exact match
 * @param status       null = any status; concrete value filters
 *                     (default at REST layer is {@link WarehouseStatus#ACTIVE})
 */
public record ListLocationsCriteria(
        UUID warehouseId,
        UUID zoneId,
        LocationType locationType,
        String locationCode,
        WarehouseStatus status) {

    public static ListLocationsCriteria empty() {
        return new ListLocationsCriteria(null, null, null, null, null);
    }
}
