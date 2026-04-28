package com.wms.inventory.application.port.in;

import com.wms.inventory.application.command.TransferStockCommand;
import com.wms.inventory.application.result.TransferResult;

public interface TransferStockUseCase {

    TransferResult transfer(TransferStockCommand command);
}
