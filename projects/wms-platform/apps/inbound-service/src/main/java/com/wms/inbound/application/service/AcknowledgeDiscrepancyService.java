package com.wms.inbound.application.service;

import com.wms.inbound.application.command.AcknowledgeDiscrepancyCommand;
import com.wms.inbound.application.port.in.AcknowledgeDiscrepancyUseCase;
import com.wms.inbound.application.port.out.AsnPersistencePort;
import com.wms.inbound.application.port.out.InboundEventPort;
import com.wms.inbound.application.port.out.InspectionPersistencePort;
import com.wms.inbound.application.result.InspectionResult;
import com.wms.inbound.domain.event.InspectionCompletedEvent;
import com.wms.inbound.domain.exception.InspectionNotFoundException;
import com.wms.inbound.domain.model.Asn;
import com.wms.inbound.domain.model.Inspection;
import com.wms.inbound.domain.model.InspectionDiscrepancy;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcknowledgeDiscrepancyService implements AcknowledgeDiscrepancyUseCase {

    private static final Logger log = LoggerFactory.getLogger(AcknowledgeDiscrepancyService.class);

    private final InspectionPersistencePort inspectionPersistence;
    private final AsnPersistencePort asnPersistence;
    private final InboundEventPort eventPort;
    private final Clock clock;

    public AcknowledgeDiscrepancyService(InspectionPersistencePort inspectionPersistence,
                                          AsnPersistencePort asnPersistence,
                                          InboundEventPort eventPort,
                                          Clock clock) {
        this.inspectionPersistence = inspectionPersistence;
        this.asnPersistence = asnPersistence;
        this.eventPort = eventPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public InspectionResult acknowledge(AcknowledgeDiscrepancyCommand command) {
        AuthorizationGuards.requireRole(command.callerRoles(), InboundRoles.ROLE_INBOUND_ADMIN);

        Inspection inspection = inspectionPersistence.findById(command.inspectionId())
                .orElseThrow(() -> new InspectionNotFoundException(command.inspectionId()));

        InspectionDiscrepancy discrepancy = inspectionPersistence
                .findDiscrepancyById(command.discrepancyId())
                .orElseThrow(() -> new InspectionNotFoundException(
                        "Discrepancy not found: " + command.discrepancyId()));

        if (discrepancy.isAcknowledged()) {
            return InspectionService.toResult(inspection);
        }

        Instant now = clock.instant();
        discrepancy.acknowledge(command.actorId(), now);
        inspectionPersistence.saveDiscrepancy(discrepancy);

        Inspection fresh = inspectionPersistence.findById(command.inspectionId())
                .orElseThrow(() -> new InspectionNotFoundException(command.inspectionId()));

        if (fresh.allDiscrepanciesAcknowledged()) {
            Asn asn = asnPersistence.findById(fresh.getAsnId()).orElseThrow();
            asn.completeInspection(now, command.actorId());
            asnPersistence.save(asn);

            publishInspectionCompleted(fresh, asn, now, command.actorId());
            log.info("inspection_completed_after_ack asnId={} inspectionId={}",
                    asn.getId(), fresh.getId());
        }

        log.info("discrepancy_acknowledged discrepancyId={} by={}", command.discrepancyId(), command.actorId());
        return InspectionService.toResult(fresh);
    }

    private void publishInspectionCompleted(Inspection inspection, Asn asn, Instant now, String actorId) {
        // discrepancyCount=0: all discrepancies are acknowledged by the time this fires.
        // NOTE: diverges from InspectionService which computes actual unacked count —
        // preserved as-is pending follow-up to verify and unify the logic.
        InspectionCompletedEvent event = InspectionCompletedEvent.fromInspection(
                inspection, asn.getAsnNo(), asn.getWarehouseId(), 0, now, actorId);
        eventPort.publish(event);
    }

}
