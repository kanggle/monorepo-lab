package com.wms.inbound.application.port.in;

import com.wms.inbound.application.command.ReceiveAsnCommand;
import com.wms.inbound.application.result.AsnResult;

public interface ReceiveAsnUseCase {

    AsnResult receive(ReceiveAsnCommand command);
}
