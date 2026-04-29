package com.wms.inbound.application.service;

import com.wms.inbound.application.port.in.GetPutawayInstructionUseCase;
import com.wms.inbound.application.port.out.AsnPersistencePort;
import com.wms.inbound.application.port.out.MasterReadModelPort;
import com.wms.inbound.application.port.out.PutawayPersistencePort;
import com.wms.inbound.application.result.PutawayConfirmationResult;
import com.wms.inbound.application.result.PutawayInstructionResult;
import com.wms.inbound.domain.exception.PutawayInstructionNotFoundException;
import com.wms.inbound.domain.exception.PutawayLineNotFoundException;
import com.wms.inbound.domain.model.Asn;
import com.wms.inbound.domain.model.PutawayConfirmation;
import com.wms.inbound.domain.model.PutawayInstruction;
import com.wms.inbound.domain.model.masterref.LocationSnapshot;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PutawayQueryService implements GetPutawayInstructionUseCase {

    private final PutawayPersistencePort putawayPersistence;
    private final AsnPersistencePort asnPersistence;
    private final MasterReadModelPort masterReadModel;

    public PutawayQueryService(PutawayPersistencePort putawayPersistence,
                                AsnPersistencePort asnPersistence,
                                MasterReadModelPort masterReadModel) {
        this.putawayPersistence = putawayPersistence;
        this.asnPersistence = asnPersistence;
        this.masterReadModel = masterReadModel;
    }

    @Override
    @Transactional(readOnly = true)
    public PutawayInstructionResult findByInstructionId(UUID instructionId) {
        PutawayInstruction instruction = putawayPersistence.findById(instructionId)
                .orElseThrow(() -> new PutawayInstructionNotFoundException(instructionId));
        Asn asn = asnPersistence.findById(instruction.getAsnId()).orElseThrow();
        return PutawayResultMapper.toInstructionResult(instruction, asn.getStatus().name(), masterReadModel);
    }

    @Override
    @Transactional(readOnly = true)
    public PutawayInstructionResult findByAsnId(UUID asnId) {
        PutawayInstruction instruction = putawayPersistence.findByAsnId(asnId)
                .orElseThrow(() -> new PutawayInstructionNotFoundException(asnId));
        Asn asn = asnPersistence.findById(instruction.getAsnId()).orElseThrow();
        return PutawayResultMapper.toInstructionResult(instruction, asn.getStatus().name(), masterReadModel);
    }

    @Override
    @Transactional(readOnly = true)
    public PutawayConfirmationResult findConfirmationByLineId(UUID lineId) {
        PutawayConfirmation row = putawayPersistence.findConfirmationByLineId(lineId)
                .orElseThrow(() -> new PutawayLineNotFoundException(lineId));
        PutawayInstruction instruction = putawayPersistence.findById(row.putawayInstructionId())
                .orElseThrow(() -> new PutawayInstructionNotFoundException(row.putawayInstructionId()));
        Asn asn = asnPersistence.findById(instruction.getAsnId()).orElseThrow();
        String locCode = masterReadModel.findLocation(row.actualLocationId())
                .map(LocationSnapshot::locationCode).orElse(null);
        return new PutawayConfirmationResult(
                row.id(), row.putawayLineId(), row.putawayInstructionId(),
                row.actualLocationId(), locCode, row.qtyConfirmed(),
                row.confirmedBy(), row.confirmedAt(),
                PutawayResultMapper.toInstructionState(instruction),
                new PutawayConfirmationResult.AsnState(asn.getStatus().name()));
    }
}
