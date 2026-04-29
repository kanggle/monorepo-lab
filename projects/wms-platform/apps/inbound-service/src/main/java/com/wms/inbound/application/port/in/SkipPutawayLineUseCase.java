package com.wms.inbound.application.port.in;

import com.wms.inbound.application.command.SkipPutawayLineCommand;
import com.wms.inbound.application.result.PutawaySkipResult;

public interface SkipPutawayLineUseCase {

    PutawaySkipResult skip(SkipPutawayLineCommand command);
}
