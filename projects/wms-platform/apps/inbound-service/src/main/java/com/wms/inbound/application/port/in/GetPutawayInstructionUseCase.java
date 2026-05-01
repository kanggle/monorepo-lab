package com.wms.inbound.application.port.in;

import com.wms.inbound.application.result.PutawayConfirmationResult;
import com.wms.inbound.application.result.PutawayInstructionResult;
import java.util.UUID;

public interface GetPutawayInstructionUseCase {

    PutawayInstructionResult findByInstructionId(UUID instructionId);

    PutawayInstructionResult findByAsnId(UUID asnId);

    PutawayConfirmationResult findConfirmationByLineId(UUID lineId);
}
