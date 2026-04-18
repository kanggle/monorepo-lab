package com.wms.master.application.port.in;

import com.example.common.page.PageResult;
import com.wms.master.application.query.ListWarehousesQuery;
import com.wms.master.application.result.WarehouseResult;
import java.util.UUID;

/**
 * Inbound port for Warehouse read-side operations.
 */
public interface WarehouseQueryUseCase {

    WarehouseResult findById(UUID id);

    WarehouseResult findByCode(String warehouseCode);

    PageResult<WarehouseResult> list(ListWarehousesQuery query);
}
