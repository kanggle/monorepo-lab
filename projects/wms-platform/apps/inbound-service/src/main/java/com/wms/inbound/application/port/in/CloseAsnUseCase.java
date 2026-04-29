package com.wms.inbound.application.port.in;

import com.wms.inbound.application.command.CloseAsnCommand;
import com.wms.inbound.application.result.CloseAsnResult;

public interface CloseAsnUseCase {

    CloseAsnResult close(CloseAsnCommand command);
}
