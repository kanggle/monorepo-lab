package com.wms.inbound.application.service;

import com.example.common.id.UuidV7;
import com.wms.inbound.application.command.ConfirmPutawayLineCommand;
import com.wms.inbound.application.port.in.ConfirmPutawayLineUseCase;
import com.wms.inbound.application.port.out.AsnPersistencePort;
import com.wms.inbound.application.port.out.MasterReadModelPort;
import com.wms.inbound.application.port.out.PutawayPersistencePort;
import com.wms.inbound.application.result.PutawayConfirmationResult;
import com.wms.inbound.domain.exception.LocationInactiveException;
import com.wms.inbound.domain.exception.WarehouseMismatchException;
import com.wms.inbound.domain.model.Asn;
import com.wms.inbound.domain.model.PutawayConfirmation;
import com.wms.inbound.domain.model.PutawayInstruction;
import com.wms.inbound.domain.model.PutawayLine;
import com.wms.inbound.domain.model.masterref.LocationSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfirmPutawayLineService implements ConfirmPutawayLineUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConfirmPutawayLineService.class);

    private final PutawayPersistencePort putawayPersistence;
    private final AsnPersistencePort asnPersistence;
    private final MasterReadModelPort masterReadModel;
    private final PutawayCompletionPublisher completionPublisher;
    private final Clock clock;

    public ConfirmPutawayLineService(PutawayPersistencePort putawayPersistence,
                                      AsnPersistencePort asnPersistence,
                                      MasterReadModelPort masterReadModel,
                                      PutawayCompletionPublisher completionPublisher,
                                      Clock clock) {
        this.putawayPersistence = putawayPersistence;
        this.asnPersistence = asnPersistence;
        this.masterReadModel = masterReadModel;
        this.completionPublisher = completionPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PutawayConfirmationResult confirm(ConfirmPutawayLineCommand command) {
        AuthorizationGuards.requireRole(command.callerRoles(), InboundRoles.ROLE_INBOUND_WRITE);

        PutawayInstruction instruction = putawayPersistence
                .findByIdForUpdateOrThrow(command.putawayInstructionId());
        PutawayLine line = instruction.getLine(command.putawayLineId());

        // Validate qty matches the planned (no partial in v1).
        if (command.qtyConfirmed() != line.getQtyToPutaway()) {
            throw new IllegalArgumentException("qtyConfirmed " + command.qtyConfirmed()
                    + " must equal qtyToPutaway " + line.getQtyToPutaway());
        }

        // Validate actual location.
        LocationSnapshot loc = masterReadModel.findLocation(command.actualLocationId())
                .orElseThrow(() -> new LocationInactiveException(command.actualLocationId()));
        if (!loc.isActive()) {
            throw new LocationInactiveException(command.actualLocationId());
        }
        if (!loc.warehouseId().equals(instruction.getWarehouseId())) {
            throw new WarehouseMismatchException(instruction.getWarehouseId(), loc.warehouseId());
        }

        Instant now = clock.instant();
        UUID confirmationId = UuidV7.randomUuid();
        PutawayConfirmation confirmation = new PutawayConfirmation(
                confirmationId, instruction.getId(), line.getId(),
                line.getSkuId(), line.getLotId(),
                line.getDestinationLocationId(), command.actualLocationId(),
                command.qtyConfirmed(), command.actorId(), now);
        putawayPersistence.saveConfirmation(confirmation);

        PutawayInstruction.Transition transition =
                instruction.confirmLine(line.getId(), now, command.actorId());

        PutawayInstruction savedInstruction = putawayPersistence.save(instruction);

        Asn asn = asnPersistence.findById(instruction.getAsnId())
                .orElseThrow();

        asn = completionPublisher.publishIfTerminal(transition, savedInstruction, asn,
                now, command.actorId(), confirmation);

        if (!transition.isLastLine()) {
            log.info("putaway_line_confirmed instructionId={} lineId={}",
                    savedInstruction.getId(), line.getId());
        }

        String locCode = loc.locationCode();
        return new PutawayConfirmationResult(
                confirmationId, line.getId(), savedInstruction.getId(),
                command.actualLocationId(), locCode, command.qtyConfirmed(),
                command.actorId(), now,
                PutawayResultMapper.toInstructionState(savedInstruction),
                new PutawayConfirmationResult.AsnState(asn.getStatus().name()));
    }

}
