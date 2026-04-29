package com.wms.inbound.application.port.in;

import com.wms.inbound.application.command.AcknowledgeDiscrepancyCommand;
import com.wms.inbound.application.result.InspectionResult;

public interface AcknowledgeDiscrepancyUseCase {

    InspectionResult acknowledge(AcknowledgeDiscrepancyCommand command);
}
