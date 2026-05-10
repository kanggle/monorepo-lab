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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstructPutawayService implements InstructPutawayUseCase {

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
        AuthorizationGuards.requireRole(command.callerRoles(), InboundRoles.ROLE_INBOUND_WRITE);

        Asn asn = asnPersistence.findById(command.asnId())
                .orElseThrow(() -> new AsnNotFoundException(command.asnId()));

        Inspection inspection = inspectionPersistence.findByAsnId(command.asnId())
                .orElseThrow(() -> new InspectionNotFoundException(
                        "Inspection not found for ASN: " + command.asnId()));

        Map<UUID, AsnLine> asnLineById = buildAsnLineMap(asn);
        Map<UUID, InspectionLine> inspectionLineByAsnLineId = buildInspectionLineMap(inspection);

        validateLineCoverageAndCumQty(command, inspectionLineByAsnLineId);

        Instant now = clock.instant();
        UUID instructionId = UuidV7.randomUuid();

        List<PutawayLine> putawayLines = new ArrayList<>();
        List<PutawayInstructedEvent.Line> eventLines = new ArrayList<>();
        buildPutawayLines(command, asn, instructionId, asnLineById, inspectionLineByAsnLineId,
                putawayLines, eventLines);

        PutawayInstruction instruction = PutawayInstruction.createNew(
                instructionId, asn.getId(), asn.getWarehouseId(),
                command.actorId(), now, putawayLines);
        PutawayInstruction saved = putawayPersistence.save(instruction);

        asn.instructPutaway(now, command.actorId());
        Asn savedAsn = asnPersistence.save(asn);

        publishInstructed(saved, savedAsn, command.actorId(), eventLines, now);

        log.info("putaway_instructed asnId={} instructionId={} lines={}",
                savedAsn.getId(), saved.getId(), saved.totalLineCount());

        return PutawayResultMapper.toInstructionResult(saved, savedAsn.getStatus().name(), masterReadModel);
    }

    /** Builds a fast-lookup map of AsnLine by id. */
    private Map<UUID, AsnLine> buildAsnLineMap(Asn asn) {
        Map<UUID, AsnLine> asnLineById = new HashMap<>();
        for (AsnLine al : asn.getLines()) {
            asnLineById.put(al.getId(), al);
        }
        return asnLineById;
    }

    /** Builds a fast-lookup map of InspectionLine indexed by its parent AsnLine id. */
    private Map<UUID, InspectionLine> buildInspectionLineMap(Inspection inspection) {
        Map<UUID, InspectionLine> inspectionLineByAsnLineId = new HashMap<>();
        for (InspectionLine il : inspection.getLines()) {
            inspectionLineByAsnLineId.put(il.getAsnLineId(), il);
        }
        return inspectionLineByAsnLineId;
    }

    /**
     * Validates that every command line references a known inspection line and that
     * the cumulative qty-to-putaway never exceeds the qty_passed from inspection.
     */
    private void validateLineCoverageAndCumQty(InstructPutawayCommand command,
                                               Map<UUID, InspectionLine> inspectionLineByAsnLineId) {
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
    }

    /**
     * Builds PutawayLine domain objects and their corresponding event line DTOs,
     * enforcing location/lot/warehouse guards per line.
     *
     * <p>Results are accumulated into the provided {@code putawayLines} and
     * {@code eventLines} lists (output parameters).
     */
    private void buildPutawayLines(InstructPutawayCommand command,
                                   Asn asn,
                                   UUID instructionId,
                                   Map<UUID, AsnLine> asnLineById,
                                   Map<UUID, InspectionLine> inspectionLineByAsnLineId,
                                   List<PutawayLine> putawayLines,
                                   List<PutawayInstructedEvent.Line> eventLines) {
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
    }

    /** Transitions ASN state, publishes the PutawayInstructed domain event. */
    private void publishInstructed(PutawayInstruction saved,
                                   Asn savedAsn,
                                   String actorId,
                                   List<PutawayInstructedEvent.Line> eventLines,
                                   Instant now) {
        PutawayInstructedEvent event = new PutawayInstructedEvent(
                saved.getId(), savedAsn.getId(), savedAsn.getWarehouseId(),
                actorId, eventLines, now, actorId);
        eventPort.publish(event);
    }
}
