package com.wms.master.application.port.in;

import com.wms.master.application.command.CreateLotCommand;
import com.wms.master.application.command.DeactivateLotCommand;
import com.wms.master.application.command.ReactivateLotCommand;
import com.wms.master.application.command.UpdateLotCommand;
import com.wms.master.application.result.LotResult;

/**
 * Inbound port for Lot write-side operations.
 */
public interface LotCrudUseCase {

    LotResult create(CreateLotCommand command);

    LotResult update(UpdateLotCommand command);

    LotResult deactivate(DeactivateLotCommand command);

    LotResult reactivate(ReactivateLotCommand command);
}
