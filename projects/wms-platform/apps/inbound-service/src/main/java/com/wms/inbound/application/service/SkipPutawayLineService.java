package com.wms.inbound.application.service;

import com.wms.inbound.application.command.SkipPutawayLineCommand;
import com.wms.inbound.application.port.in.SkipPutawayLineUseCase;
import com.wms.inbound.application.port.out.AsnPersistencePort;
import com.wms.inbound.application.port.out.InboundEventPort;
import com.wms.inbound.application.port.out.PutawayPersistencePort;
import com.wms.inbound.application.result.PutawayConfirmationResult;
import com.wms.inbound.application.result.PutawaySkipResult;
import com.wms.inbound.domain.event.PutawayCompletedEvent;
import com.wms.inbound.domain.model.Asn;
import com.wms.inbound.domain.model.PutawayConfirmation;
import com.wms.inbound.domain.model.PutawayInstruction;
import com.wms.inbound.domain.model.PutawayLine;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkipPutawayLineService implements SkipPutawayLineUseCase {

    private static final String ROLE_INBOUND_WRITE = "ROLE_INBOUND_WRITE";
    private static final Logger log = LoggerFactory.getLogger(SkipPutawayLineService.class);

    private final PutawayPersistencePort putawayPersistence;
    private final AsnPersistencePort asnPersistence;
    private final InboundEventPort eventPort;
    private final Clock clock;

    public SkipPutawayLineService(PutawayPersistencePort putawayPersistence,
                                   AsnPersistencePort asnPersistence,
                                   InboundEventPort eventPort,
                                   Clock clock) {
        this.putawayPersistence = putawayPersistence;
        this.asnPersistence = asnPersistence;
        this.eventPort = eventPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PutawaySkipResult skip(SkipPutawayLineCommand command) {
        requireRole(command.callerRoles(), ROLE_INBOUND_WRITE);

        PutawayInstruction instruction = putawayPersistence
                .findByIdForUpdateOrThrow(command.putawayInstructionId());
        PutawayLine line = instruction.getLine(command.putawayLineId());

        Instant now = clock.instant();
        PutawayInstruction.Transition transition =
                instruction.skipLine(line.getId(), now, command.actorId());

        PutawayInstruction savedInstruction = putawayPersistence.save(instruction);

        Asn asn = asnPersistence.findById(instruction.getAsnId())
                .orElseThrow();

        if (transition.isLastLine()) {
            asn.completePutaway(now, command.actorId());
            asn = asnPersistence.save(asn);

            // Confirmed lines may be empty (all lines skipped) — that is allowed
            // per AC-14; inventory-service treats empty lines[] as a no-op.
            List<PutawayCompletedEvent.Line> evtLines = new ArrayList<>();
            for (PutawayLine pl : savedInstruction.confirmedLines()) {
                PutawayConfirmation row = putawayPersistence.findConfirmationByLineId(pl.getId())
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
            log.info("putaway_completed_via_skip asnId={} instructionId={} confirmedLines={}",
                    savedInstruction.getAsnId(), savedInstruction.getId(), evtLines.size());
        } else {
            log.info("putaway_line_skipped instructionId={} lineId={}",
                    savedInstruction.getId(), line.getId());
        }

        return new PutawaySkipResult(
                line.getId(), line.getStatus().name(), command.reason(),
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
