package com.wms.inbound.application.port.in;

import com.wms.inbound.application.command.ConfirmPutawayLineCommand;
import com.wms.inbound.application.result.PutawayConfirmationResult;

public interface ConfirmPutawayLineUseCase {

    PutawayConfirmationResult confirm(ConfirmPutawayLineCommand command);
}
