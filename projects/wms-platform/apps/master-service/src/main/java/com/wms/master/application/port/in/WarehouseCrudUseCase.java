package com.wms.master.application.port.in;

import com.wms.master.application.command.CreateWarehouseCommand;
import com.wms.master.application.command.DeactivateWarehouseCommand;
import com.wms.master.application.command.ReactivateWarehouseCommand;
import com.wms.master.application.command.UpdateWarehouseCommand;
import com.wms.master.application.result.WarehouseResult;

/**
 * Inbound port for Warehouse write-side operations.
 */
public interface WarehouseCrudUseCase {

    WarehouseResult create(CreateWarehouseCommand command);

    WarehouseResult update(UpdateWarehouseCommand command);

    WarehouseResult deactivate(DeactivateWarehouseCommand command);

    WarehouseResult reactivate(ReactivateWarehouseCommand command);
}
