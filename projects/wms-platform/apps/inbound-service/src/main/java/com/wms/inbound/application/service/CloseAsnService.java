package com.wms.inbound.application.service;

import com.wms.inbound.application.command.CloseAsnCommand;
import com.wms.inbound.application.port.in.CloseAsnUseCase;
import com.wms.inbound.application.port.out.AsnPersistencePort;
import com.wms.inbound.application.port.out.InboundEventPort;
import com.wms.inbound.application.port.out.InspectionPersistencePort;
import com.wms.inbound.application.port.out.PutawayPersistencePort;
import com.wms.inbound.application.result.CloseAsnResult;
import com.wms.inbound.domain.event.AsnClosedEvent;
import com.wms.inbound.domain.exception.AsnNotFoundException;
import com.wms.inbound.domain.model.Asn;
import com.wms.inbound.domain.model.AsnLine;
import com.wms.inbound.domain.model.Inspection;
import com.wms.inbound.domain.model.InspectionDiscrepancy;
import com.wms.inbound.domain.model.InspectionLine;
import com.wms.inbound.domain.model.PutawayConfirmation;
import com.wms.inbound.domain.model.PutawayInstruction;
import com.wms.inbound.domain.model.PutawayLine;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CloseAsnService implements CloseAsnUseCase {

    private static final String ROLE_INBOUND_WRITE = "ROLE_INBOUND_WRITE";
    private static final Logger log = LoggerFactory.getLogger(CloseAsnService.class);

    private final AsnPersistencePort asnPersistence;
    private final InspectionPersistencePort inspectionPersistence;
    private final PutawayPersistencePort putawayPersistence;
    private final InboundEventPort eventPort;
    private final Clock clock;

    public CloseAsnService(AsnPersistencePort asnPersistence,
                            InspectionPersistencePort inspectionPersistence,
                            PutawayPersistencePort putawayPersistence,
                            InboundEventPort eventPort,
                            Clock clock) {
        this.asnPersistence = asnPersistence;
        this.inspectionPersistence = inspectionPersistence;
        this.putawayPersistence = putawayPersistence;
        this.eventPort = eventPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CloseAsnResult close(CloseAsnCommand command) {
        requireRole(command.callerRoles(), ROLE_INBOUND_WRITE);

        Asn asn = asnPersistence.findById(command.asnId())
                .orElseThrow(() -> new AsnNotFoundException(command.asnId()));

        // Compute summary BEFORE state transition (so the event payload is stable
        // even if some downstream rule rejects the transition).
        AsnClosedEvent.Summary summary = computeSummary(asn);

        Instant now = clock.instant();
        asn.close(now, command.actorId());
        Asn saved = asnPersistence.save(asn);

        AsnClosedEvent event = new AsnClosedEvent(
                saved.getId(), saved.getAsnNo(), saved.getWarehouseId(),
                now, command.actorId(), summary, now, command.actorId());
        eventPort.publish(event);

        log.info("asn_closed asnId={} confirmedTotal={}", saved.getId(), summary.putawayConfirmedTotal());

        return new CloseAsnResult(
                saved.getId(), saved.getAsnNo(), saved.getStatus().name(),
                now, command.actorId(),
                new CloseAsnResult.Summary(
                        summary.expectedTotal(), summary.passedTotal(),
                        summary.damagedTotal(), summary.shortTotal(),
                        summary.putawayConfirmedTotal(), summary.discrepancyCount()),
                saved.getVersion());
    }

    private AsnClosedEvent.Summary computeSummary(Asn asn) {
        int expectedTotal = asn.getLines().stream().mapToInt(AsnLine::getExpectedQty).sum();
        int passedTotal = 0;
        int damagedTotal = 0;
        int shortTotal = 0;
        int discrepancyCount = 0;

        Optional<Inspection> inspectionOpt = inspectionPersistence.findByAsnId(asn.getId());
        if (inspectionOpt.isPresent()) {
            Inspection inspection = inspectionOpt.get();
            for (InspectionLine il : inspection.getLines()) {
                passedTotal += il.getQtyPassed();
                damagedTotal += il.getQtyDamaged();
                shortTotal += il.getQtyShort();
            }
            discrepancyCount = inspection.getDiscrepancies().size();
        }

        int putawayConfirmedTotal = 0;
        Optional<PutawayInstruction> putawayOpt = putawayPersistence.findByAsnId(asn.getId());
        if (putawayOpt.isPresent()) {
            PutawayInstruction instruction = putawayOpt.get();
            for (PutawayLine pl : instruction.getLines()) {
                if (pl.isConfirmed()) {
                    Optional<PutawayConfirmation> conf =
                            putawayPersistence.findConfirmationByLineId(pl.getId());
                    if (conf.isPresent()) {
                        putawayConfirmedTotal += conf.get().qtyConfirmed();
                    }
                }
            }
        }

        return new AsnClosedEvent.Summary(
                expectedTotal, passedTotal, damagedTotal, shortTotal,
                putawayConfirmedTotal, discrepancyCount);
    }

    private static void requireRole(java.util.Set<String> roles, String required) {
        if (roles == null || !roles.contains(required)) {
            throw new AccessDeniedException("Role required: " + required);
        }
    }
}
