package com.wms.inbound.application.service;

import com.wms.inbound.application.command.SkipPutawayLineCommand;
import com.wms.inbound.application.port.in.SkipPutawayLineUseCase;
import com.wms.inbound.application.port.out.AsnPersistencePort;
import com.wms.inbound.application.port.out.PutawayPersistencePort;
import com.wms.inbound.application.result.PutawayConfirmationResult;
import com.wms.inbound.application.result.PutawaySkipResult;
import com.wms.inbound.domain.model.Asn;
import com.wms.inbound.domain.model.PutawayInstruction;
import com.wms.inbound.domain.model.PutawayLine;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkipPutawayLineService implements SkipPutawayLineUseCase {

    private static final Logger log = LoggerFactory.getLogger(SkipPutawayLineService.class);

    private final PutawayPersistencePort putawayPersistence;
    private final AsnPersistencePort asnPersistence;
    private final PutawayCompletionPublisher completionPublisher;
    private final Clock clock;

    public SkipPutawayLineService(PutawayPersistencePort putawayPersistence,
                                   AsnPersistencePort asnPersistence,
                                   PutawayCompletionPublisher completionPublisher,
                                   Clock clock) {
        this.putawayPersistence = putawayPersistence;
        this.asnPersistence = asnPersistence;
        this.completionPublisher = completionPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PutawaySkipResult skip(SkipPutawayLineCommand command) {
        AuthorizationGuards.requireRole(command.callerRoles(), InboundRoles.ROLE_INBOUND_WRITE);

        PutawayInstruction instruction = putawayPersistence
                .findByIdForUpdateOrThrow(command.putawayInstructionId());
        PutawayLine line = instruction.getLine(command.putawayLineId());

        Instant now = clock.instant();
        PutawayInstruction.Transition transition =
                instruction.skipLine(line.getId(), now, command.actorId());

        PutawayInstruction savedInstruction = putawayPersistence.save(instruction);

        Asn asn = asnPersistence.findById(instruction.getAsnId())
                .orElseThrow();

        // null currentConfirmation: skipped lines have no confirmation row.
        // Confirmed lines may be empty (all lines skipped) — allowed per AC-14;
        // inventory-service treats empty lines[] as a no-op.
        asn = completionPublisher.publishIfTerminal(transition, savedInstruction, asn,
                now, command.actorId(), null);

        if (!transition.isLastLine()) {
            log.info("putaway_line_skipped instructionId={} lineId={}",
                    savedInstruction.getId(), line.getId());
        }

        return new PutawaySkipResult(
                line.getId(), line.getStatus().name(), command.reason(),
                command.actorId(), now,
                PutawayResultMapper.toInstructionState(savedInstruction),
                new PutawayConfirmationResult.AsnState(asn.getStatus().name()));
    }

}
