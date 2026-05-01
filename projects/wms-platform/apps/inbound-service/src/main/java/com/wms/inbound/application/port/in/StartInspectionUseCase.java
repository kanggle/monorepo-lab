package com.wms.inbound.application.port.in;

import com.wms.inbound.application.command.StartInspectionCommand;
import com.wms.inbound.application.result.AsnResult;

public interface StartInspectionUseCase {

    AsnResult start(StartInspectionCommand command);
}
