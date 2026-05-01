package com.wms.inbound.application.port.in;

import com.wms.inbound.application.command.InstructPutawayCommand;
import com.wms.inbound.application.result.PutawayInstructionResult;

public interface InstructPutawayUseCase {

    PutawayInstructionResult instruct(InstructPutawayCommand command);
}
