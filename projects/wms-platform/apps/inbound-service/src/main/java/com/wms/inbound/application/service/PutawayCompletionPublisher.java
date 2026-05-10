package com.wms.inbound.application.service;

import com.wms.inbound.application.port.out.AsnPersistencePort;
import com.wms.inbound.application.port.out.InboundEventPort;
import com.wms.inbound.application.port.out.PutawayPersistencePort;
import com.wms.inbound.domain.event.PutawayCompletedEvent;
import com.wms.inbound.domain.model.Asn;
import com.wms.inbound.domain.model.PutawayConfirmation;
import com.wms.inbound.domain.model.PutawayInstruction;
import com.wms.inbound.domain.model.PutawayInstruction.Transition;
import com.wms.inbound.domain.model.PutawayLine;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Package-private helper: publishes {@code inbound.putaway.completed} when the last
 * putaway line is resolved (confirmed or skipped).
 *
 * <p>Not {@code @Transactional} — callers ({@link ConfirmPutawayLineService} and
 * {@link SkipPutawayLineService}) already hold the outer {@code REQUIRED} TX boundary;
 * this component participates in that same transaction via normal Spring TX propagation.
 */
@Component
class PutawayCompletionPublisher {

    private static final Logger log = LoggerFactory.getLogger(PutawayCompletionPublisher.class);

    private final AsnPersistencePort asnPersistence;
    private final PutawayPersistencePort putawayPersistence;
    private final InboundEventPort eventPort;

    PutawayCompletionPublisher(AsnPersistencePort asnPersistence,
                                PutawayPersistencePort putawayPersistence,
                                InboundEventPort eventPort) {
        this.asnPersistence = asnPersistence;
        this.putawayPersistence = putawayPersistence;
        this.eventPort = eventPort;
    }

    /**
     * If {@code transition} signals the last line, completes putaway on the ASN,
     * builds the {@link PutawayCompletedEvent}, and publishes it.
     *
     * @param transition        result of the line state change
     * @param savedInstruction  the already-saved putaway instruction
     * @param now               event timestamp
     * @param actorId           actor performing the action
     * @param currentConfirmation the just-saved confirmation row for the triggering line,
     *                            or {@code null} if the line was skipped (no confirmation row)
     * @return the reloaded {@link Asn} if last-line (with updated status), or the original
     *         unchanged {@link Asn} if not last-line
     */
    Asn publishIfTerminal(Transition transition,
                           PutawayInstruction savedInstruction,
                           Asn asn,
                           Instant now,
                           String actorId,
                           PutawayConfirmation currentConfirmation) {
        if (!transition.isLastLine()) {
            return asn;
        }

        asn.completePutaway(now, actorId);
        Asn savedAsn = asnPersistence.save(asn);

        List<PutawayCompletedEvent.Line> evtLines = buildEventLines(
                savedInstruction, currentConfirmation);

        PutawayCompletedEvent event = new PutawayCompletedEvent(
                savedInstruction.getId(), savedInstruction.getAsnId(),
                savedInstruction.getWarehouseId(), now, evtLines, now, actorId);
        eventPort.publish(event);

        String marker = currentConfirmation == null ? "putaway_completed_via_skip" : "putaway_completed";
        log.info("{} asnId={} instructionId={} confirmedLines={}",
                marker, savedInstruction.getAsnId(), savedInstruction.getId(), evtLines.size());

        return savedAsn;
    }

    private List<PutawayCompletedEvent.Line> buildEventLines(
            PutawayInstruction savedInstruction,
            PutawayConfirmation currentConfirmation) {
        List<PutawayCompletedEvent.Line> evtLines = new ArrayList<>();
        for (PutawayLine pl : savedInstruction.confirmedLines()) {
            PutawayConfirmation row = resolveConfirmation(pl, currentConfirmation);
            evtLines.add(new PutawayCompletedEvent.Line(
                    row.id(), pl.getSkuId(), pl.getLotId(),
                    row.actualLocationId(), row.qtyConfirmed()));
        }
        return evtLines;
    }

    private PutawayConfirmation resolveConfirmation(PutawayLine pl,
                                                     PutawayConfirmation currentConfirmation) {
        if (currentConfirmation != null && pl.getId().equals(currentConfirmation.putawayLineId())) {
            return currentConfirmation;
        }
        return putawayPersistence.findConfirmationByLineId(pl.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Missing confirmation for confirmed line " + pl.getId()));
    }
}
