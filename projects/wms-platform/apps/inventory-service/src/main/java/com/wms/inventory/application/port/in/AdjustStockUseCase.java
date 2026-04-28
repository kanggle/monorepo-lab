package com.wms.inventory.application.port.in;

import com.wms.inventory.application.command.AdjustStockCommand;
import com.wms.inventory.application.result.AdjustmentResult;

public interface AdjustStockUseCase {

    AdjustmentResult adjust(AdjustStockCommand command);
}
