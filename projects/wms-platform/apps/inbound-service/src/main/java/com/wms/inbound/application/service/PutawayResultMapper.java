package com.wms.inbound.application.service;

import com.wms.inbound.application.port.out.MasterReadModelPort;
import com.wms.inbound.application.result.PutawayConfirmationResult;
import com.wms.inbound.application.result.PutawayInstructionResult;
import com.wms.inbound.domain.model.PutawayInstruction;
import com.wms.inbound.domain.model.PutawayLine;
import java.util.List;

/**
 * Internal mapper between Putaway domain types and application result records.
 * Package-private — only use-case services in {@code application/service/} use it.
 */
final class PutawayResultMapper {

    private PutawayResultMapper() {}

    static PutawayInstructionResult toInstructionResult(PutawayInstruction instruction,
                                                        String asnStatus,
                                                        MasterReadModelPort masterReadModel) {
        List<PutawayInstructionResult.Line> lines = instruction.getLines().stream()
                .map(l -> mapLine(l, masterReadModel))
                .toList();
        return new PutawayInstructionResult(
                instruction.getId(), instruction.getAsnId(),
                asnStatus, instruction.getWarehouseId(),
                instruction.getPlannedBy(), instruction.getStatus().name(),
                instruction.getVersion(), instruction.getCreatedAt(), instruction.getUpdatedAt(),
                lines);
    }

    private static PutawayInstructionResult.Line mapLine(PutawayLine line,
                                                          MasterReadModelPort masterReadModel) {
        String locCode = masterReadModel.findLocation(line.getDestinationLocationId())
                .map(s -> s.locationCode()).orElse(null);
        return new PutawayInstructionResult.Line(
                line.getId(), line.getAsnLineId(), line.getSkuId(),
                line.getLotId(), line.getLotNo(),
                line.getDestinationLocationId(), locCode,
                line.getQtyToPutaway(), line.getStatus().name());
    }

    static PutawayConfirmationResult.InstructionState toInstructionState(PutawayInstruction instruction) {
        return new PutawayConfirmationResult.InstructionState(
                instruction.getStatus().name(),
                instruction.confirmedLineCount(),
                instruction.skippedLineCount(),
                instruction.totalLineCount());
    }
}
