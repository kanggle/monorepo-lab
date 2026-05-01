package com.wms.inbound.application.service;

import com.example.common.id.UuidV7;
import com.wms.inbound.application.command.InstructPutawayCommand;
import com.wms.inbound.application.port.in.InstructPutawayUseCase;
import com.wms.inbound.application.port.out.AsnPersistencePort;
import com.wms.inbound.application.port.out.InboundEventPort;
import com.wms.inbound.application.port.out.InspectionPersistencePort;
import com.wms.inbound.application.port.out.MasterReadModelPort;
import com.wms.inbound.application.port.out.PutawayPersistencePort;
import com.wms.inbound.application.result.PutawayInstructionResult;
import com.wms.inbound.domain.event.PutawayInstructedEvent;
import com.wms.inbound.domain.exception.AsnNotFoundException;
import com.wms.inbound.domain.exception.InspectionNotFoundException;
import com.wms.inbound.domain.exception.LocationInactiveException;
import com.wms.inbound.domain.exception.LotRequiredException;
import com.wms.inbound.domain.exception.PutawayQuantityExceededException;
import com.wms.inbound.domain.exception.WarehouseMismatchException;
import com.wms.inbound.domain.model.Asn;
import com.wms.inbound.domain.model.AsnLine;
import com.wms.inbound.domain.model.Inspection;
import com.wms.inbound.domain.model.InspectionLine;
import com.wms.inbound.domain.model.PutawayInstruction;
import com.wms.inbound.domain.model.PutawayLine;
import com.wms.inbound.domain.model.PutawayLineStatus;
import com.wms.inbound.domain.model.masterref.LocationSnapshot;
import com.wms.inbound.domain.model.masterref.SkuSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstructPutawayService implements InstructPutawayUseCase {

    private static final String ROLE_INBOUND_WRITE = "ROLE_INBOUND_WRITE";
    private static final Logger log = LoggerFactory.getLogger(InstructPutawayService.class);

    private final AsnPersistencePort asnPersistence;
    private final InspectionPersistencePort inspectionPersistence;
    private final PutawayPersistencePort putawayPersistence;
    private final InboundEventPort eventPort;
    private final MasterReadModelPort masterReadModel;
    private final Clock clock;

    public InstructPutawayService(AsnPersistencePort asnPersistence,
                                   InspectionPersistencePort inspectionPersistence,
                                   PutawayPersistencePort putawayPersistence,
                                   InboundEventPort eventPort,
                                   MasterReadModelPort masterReadModel,
                                   Clock clock) {
        this.asnPersistence = asnPersistence;
        this.inspectionPersistence = inspectionPersistence;
        this.putawayPersistence = putawayPersistence;
        this.eventPort = eventPort;
        this.masterReadModel = masterReadModel;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PutawayInstructionResult instruct(InstructPutawayCommand command) {
        requireRole(command.callerRoles(), ROLE_INBOUND_WRITE);

        Asn asn = asnPersistence.findById(command.asnId())
                .orElseThrow(() -> new AsnNotFoundException(command.asnId()));

        Inspection inspection = inspectionPersistence.findByAsnId(command.asnId())
                .orElseThrow(() -> new InspectionNotFoundException(
                        "Inspection not found for ASN: " + command.asnId()));

        // Build maps for fast lookup.
        Map<UUID, AsnLine> asnLineById = new HashMap<>();
        for (AsnLine al : asn.getLines()) {
            asnLineById.put(al.getId(), al);
        }
        Map<UUID, InspectionLine> inspectionLineByAsnLineId = new HashMap<>();
        for (InspectionLine il : inspection.getLines()) {
            inspectionLineByAsnLineId.put(il.getAsnLineId(), il);
        }

        // Aggregate qty-to-putaway by AsnLine to verify it never exceeds qty_passed.
        Map<UUID, Integer> qtySumByAsnLineId = new HashMap<>();
        for (InstructPutawayCommand.Line cmdLine : command.lines()) {
            qtySumByAsnLineId.merge(cmdLine.asnLineId(), cmdLine.qtyToPutaway(), Integer::sum);
        }
        for (Map.Entry<UUID, Integer> e : qtySumByAsnLineId.entrySet()) {
            InspectionLine il = inspectionLineByAsnLineId.get(e.getKey());
            if (il == null) {
                throw new IllegalArgumentException(
                        "Unknown asnLineId in instruct command: " + e.getKey());
            }
            if (e.getValue() > il.getQtyPassed()) {
                throw new PutawayQuantityExceededException(e.getKey(), e.getValue(), il.getQtyPassed());
            }
        }

        Instant now = clock.instant();
        UUID instructionId = UuidV7.randomUuid();

        // Build domain PutawayLines and validate location + lot guards.
        List<PutawayLine> putawayLines = new ArrayList<>();
        List<PutawayInstructedEvent.Line> eventLines = new ArrayList<>();
        for (InstructPutawayCommand.Line cmdLine : command.lines()) {
            AsnLine asnLine = asnLineById.get(cmdLine.asnLineId());
            if (asnLine == null) {
                throw new IllegalArgumentException(
                        "AsnLine not found in ASN: " + cmdLine.asnLineId());
            }

            // LOT_REQUIRED guard: lot-tracked SKU must have a lot id on the AsnLine
            // (carried forward from inspection if revealed at dock).
            SkuSnapshot sku = masterReadModel.findSku(asnLine.getSkuId()).orElse(null);
            InspectionLine il = inspectionLineByAsnLineId.get(cmdLine.asnLineId());
            UUID effectiveLotId = il != null ? il.getLotId() : asnLine.getLotId();
            String effectiveLotNo = il != null ? il.getLotNo() : null;
            if (sku != null && sku.requiresLot() && effectiveLotId == null
                    && (effectiveLotNo == null || effectiveLotNo.isBlank())) {
                throw new LotRequiredException(asnLine.getSkuId());
            }

            LocationSnapshot loc = masterReadModel.findLocation(cmdLine.destinationLocationId())
                    .orElseThrow(() -> new LocationInactiveException(cmdLine.destinationLocationId()));
            if (!loc.isActive()) {
                throw new LocationInactiveException(cmdLine.destinationLocationId());
            }
            if (!loc.warehouseId().equals(asn.getWarehouseId())) {
                throw new WarehouseMismatchException(asn.getWarehouseId(), loc.warehouseId());
            }

            UUID putawayLineId = UuidV7.randomUuid();
            putawayLines.add(new PutawayLine(putawayLineId, instructionId,
                    cmdLine.asnLineId(), asnLine.getSkuId(),
                    effectiveLotId, effectiveLotNo,
                    cmdLine.destinationLocationId(), cmdLine.qtyToPutaway(),
                    PutawayLineStatus.PENDING));
            eventLines.add(new PutawayInstructedEvent.Line(putawayLineId,
                    cmdLine.asnLineId(), asnLine.getSkuId(), effectiveLotId,
                    cmdLine.destinationLocationId(), loc.locationCode(),
                    cmdLine.qtyToPutaway()));
        }

        PutawayInstruction instruction = PutawayInstruction.createNew(
                instructionId, asn.getId(), asn.getWarehouseId(),
                command.actorId(), now, putawayLines);
        PutawayInstruction saved = putawayPersistence.save(instruction);

        asn.instructPutaway(now, command.actorId());
        Asn savedAsn = asnPersistence.save(asn);

        PutawayInstructedEvent event = new PutawayInstructedEvent(
                saved.getId(), savedAsn.getId(), savedAsn.getWarehouseId(),
                command.actorId(), eventLines, now, command.actorId());
        eventPort.publish(event);

        log.info("putaway_instructed asnId={} instructionId={} lines={}",
                savedAsn.getId(), saved.getId(), saved.totalLineCount());

        return PutawayResultMapper.toInstructionResult(saved, savedAsn.getStatus().name(), masterReadModel);
    }

    private static void requireRole(java.util.Set<String> roles, String required) {
        if (roles == null || !roles.contains(required)) {
            throw new AccessDeniedException("Role required: " + required);
        }
    }
}
