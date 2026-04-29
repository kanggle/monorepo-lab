package com.wms.inbound.application.port.in;

import com.wms.inbound.application.command.RecordInspectionCommand;
import com.wms.inbound.application.result.InspectionResult;

public interface RecordInspectionUseCase {

    InspectionResult record(RecordInspectionCommand command);
}
