package com.wms.inbound.application.service;

import com.example.common.id.UuidV7;
import com.wms.inbound.application.command.ConfirmPutawayLineCommand;
import com.wms.inbound.application.port.in.ConfirmPutawayLineUseCase;
import com.wms.inbound.application.port.out.AsnPersistencePort;
import com.wms.inbound.application.port.out.InboundEventPort;
import com.wms.inbound.application.port.out.MasterReadModelPort;
import com.wms.inbound.application.port.out.PutawayPersistencePort;
import com.wms.inbound.application.result.PutawayConfirmationResult;
import com.wms.inbound.domain.event.PutawayCompletedEvent;
import com.wms.inbound.domain.exception.LocationInactiveException;
import com.wms.inbound.domain.exception.WarehouseMismatchException;
import com.wms.inbound.domain.model.Asn;
import com.wms.inbound.domain.model.PutawayConfirmation;
import com.wms.inbound.domain.model.PutawayInstruction;
import com.wms.inbound.domain.model.PutawayLine;
import com.wms.inbound.domain.model.masterref.LocationSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfirmPutawayLineService implements ConfirmPutawayLineUseCase {

    private static final String ROLE_INBOUND_WRITE = "ROLE_INBOUND_WRITE";
    private static final Logger log = LoggerFactory.getLogger(ConfirmPutawayLineService.class);

    private final PutawayPersistencePort putawayPersistence;
    private final AsnPersistencePort asnPersistence;
    private final InboundEventPort eventPort;
    private final MasterReadModelPort masterReadModel;
    private final Clock clock;

    public ConfirmPutawayLineService(PutawayPersistencePort putawayPersistence,
                                      AsnPersistencePort asnPersistence,
                                      InboundEventPort eventPort,
                                      MasterReadModelPort masterReadModel,
                                      Clock clock) {
        this.putawayPersistence = putawayPersistence;
        this.asnPersistence = asnPersistence;
        this.eventPort = eventPort;
        this.masterReadModel = masterReadModel;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PutawayConfirmationResult confirm(ConfirmPutawayLineCommand command) {
        requireRole(command.callerRoles(), ROLE_INBOUND_WRITE);

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

        if (transition.isLastLine()) {
            asn.completePutaway(now, command.actorId());
            asn = asnPersistence.save(asn);

            // Build the cross-service event with only confirmed lines and their
            // confirmation rows; load each by line id.
            List<PutawayCompletedEvent.Line> evtLines = new ArrayList<>();
            for (PutawayLine pl : savedInstruction.confirmedLines()) {
                PutawayConfirmation row = pl.getId().equals(line.getId())
                        ? confirmation
                        : putawayPersistence.findConfirmationByLineId(pl.getId())
                                .orElseThrow(() -> new IllegalStateException(
                                        "Missing confirmation for confirmed line " + pl.getId()));
                evtLines.add(new PutawayCompletedEvent.Line(
                        row.id(), pl.getSkuId(), pl.getLotId(),
                        row.actualLocationId(), row.qtyConfirmed()));
            }
            PutawayCompletedEvent event = new PutawayCompletedEvent(
                    savedInstruction.getId(), savedInstruction.getAsnId(),
                    savedInstruction.getWarehouseId(), now, evtLines, now, command.actorId());
            eventPort.publish(event);
            log.info("putaway_completed asnId={} instructionId={} confirmedLines={}",
                    savedInstruction.getAsnId(), savedInstruction.getId(), evtLines.size());
        } else {
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

    private static void requireRole(java.util.Set<String> roles, String required) {
        if (roles == null || !roles.contains(required)) {
            throw new AccessDeniedException("Role required: " + required);
        }
    }
}
