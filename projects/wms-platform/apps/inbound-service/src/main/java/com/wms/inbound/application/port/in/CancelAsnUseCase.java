package com.wms.inbound.application.port.in;

import com.wms.inbound.application.command.CancelAsnCommand;
import com.wms.inbound.application.result.AsnResult;

public interface CancelAsnUseCase {

    AsnResult cancel(CancelAsnCommand command);
}
