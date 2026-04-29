package com.wms.inbound.application.service;

import com.example.common.id.UuidV7;
import com.wms.inbound.application.command.RecordInspectionCommand;
import com.wms.inbound.application.command.StartInspectionCommand;
import com.wms.inbound.application.port.in.RecordInspectionUseCase;
import com.wms.inbound.application.port.in.StartInspectionUseCase;
import com.wms.inbound.application.port.out.AsnPersistencePort;
import com.wms.inbound.application.port.out.InboundEventPort;
import com.wms.inbound.application.port.out.InspectionPersistencePort;
import com.wms.inbound.application.port.out.MasterReadModelPort;
import com.wms.inbound.application.result.AsnResult;
import com.wms.inbound.application.result.InspectionResult;
import com.wms.inbound.domain.event.InspectionCompletedEvent;
import com.wms.inbound.domain.exception.AsnNotFoundException;
import com.wms.inbound.domain.exception.InspectionQuantityMismatchException;
import com.wms.inbound.domain.exception.LotRequiredException;
import com.wms.inbound.domain.model.Asn;
import com.wms.inbound.domain.model.AsnLine;
import com.wms.inbound.domain.model.DiscrepancyType;
import com.wms.inbound.domain.model.Inspection;
import com.wms.inbound.domain.model.InspectionDiscrepancy;
import com.wms.inbound.domain.model.InspectionLine;
import com.wms.inbound.domain.model.masterref.SkuSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspectionService implements StartInspectionUseCase, RecordInspectionUseCase {

    private static final Logger log = LoggerFactory.getLogger(InspectionService.class);

    private final AsnPersistencePort asnPersistence;
    private final InspectionPersistencePort inspectionPersistence;
    private final InboundEventPort eventPort;
    private final MasterReadModelPort masterReadModel;
    private final Clock clock;

    public InspectionService(AsnPersistencePort asnPersistence,
                             InspectionPersistencePort inspectionPersistence,
                             InboundEventPort eventPort,
                             MasterReadModelPort masterReadModel,
                             Clock clock) {
        this.asnPersistence = asnPersistence;
        this.inspectionPersistence = inspectionPersistence;
        this.eventPort = eventPort;
        this.masterReadModel = masterReadModel;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AsnResult start(StartInspectionCommand command) {
        Asn asn = asnPersistence.findById(command.asnId())
                .orElseThrow(() -> new AsnNotFoundException(command.asnId()));
        Instant now = clock.instant();
        asn.startInspection(now, command.actorId());
        Asn saved = asnPersistence.save(asn);
        log.info("inspection_started asnId={}", saved.getId());
        return ReceiveAsnService.toResult(saved);
    }

    @Override
    @Transactional
    public InspectionResult record(RecordInspectionCommand command) {
        Asn asn = asnPersistence.findById(command.asnId())
                .orElseThrow(() -> new AsnNotFoundException(command.asnId()));

        Map<UUID, AsnLine> lineMap = asn.getLines().stream()
                .collect(Collectors.toMap(AsnLine::getId, Function.identity()));

        validateLinesCoverage(command, lineMap);

        Instant now = clock.instant();
        UUID inspectionId = UuidV7.randomUuid();

        List<InspectionLine> inspectionLines = new ArrayList<>();
        List<InspectionDiscrepancy> discrepancies = new ArrayList<>();

        for (RecordInspectionCommand.Line cmdLine : command.lines()) {
            AsnLine asnLine = lineMap.get(cmdLine.asnLineId());

            SkuSnapshot sku = masterReadModel.findSku(asnLine.getSkuId()).orElse(null);
            if (sku != null && sku.requiresLot()
                    && cmdLine.lotId() == null && (cmdLine.lotNo() == null || cmdLine.lotNo().isBlank())) {
                throw new LotRequiredException(asnLine.getSkuId());
            }

            int total = cmdLine.qtyPassed() + cmdLine.qtyDamaged() + cmdLine.qtyShort();
            if (total > asnLine.getExpectedQty()) {
                throw new InspectionQuantityMismatchException(
                        cmdLine.asnLineId(), asnLine.getExpectedQty(), total);
            }

            UUID lineId = UuidV7.randomUuid();
            inspectionLines.add(new InspectionLine(lineId, inspectionId,
                    cmdLine.asnLineId(), asnLine.getSkuId(),
                    cmdLine.lotId(), cmdLine.lotNo(),
                    cmdLine.qtyPassed(), cmdLine.qtyDamaged(), cmdLine.qtyShort()));

            if (total < asnLine.getExpectedQty()) {
                discrepancies.add(InspectionDiscrepancy.createNew(
                        UuidV7.randomUuid(), inspectionId, cmdLine.asnLineId(),
                        DiscrepancyType.QUANTITY_MISMATCH,
                        asnLine.getExpectedQty(), total));
            }
        }

        boolean allAcked = discrepancies.stream().allMatch(InspectionDiscrepancy::isAcknowledged);

        Inspection inspection = new Inspection(inspectionId, asn.getId(),
                command.actorId(), allAcked ? now : null,
                command.notes(), 0L, now, command.actorId(), now, command.actorId(),
                inspectionLines, discrepancies);

        Inspection saved = inspectionPersistence.save(inspection);

        if (allAcked) {
            asn.completeInspection(now, command.actorId());
            asnPersistence.save(asn);

            String asnNo = asn.getAsnNo();
            publishInspectionCompleted(saved, asn.getWarehouseId(), asnNo, now, command.actorId());
            log.info("inspection_completed asnId={} inspectionId={} discrepancies=0",
                    asn.getId(), saved.getId());
        } else {
            log.info("inspection_recorded_with_discrepancies asnId={} inspectionId={} unacked={}",
                    asn.getId(), saved.getId(), saved.countUnacknowledgedDiscrepancies());
        }

        return toResult(saved);
    }

    private void validateLinesCoverage(RecordInspectionCommand command, Map<UUID, AsnLine> lineMap) {
        List<UUID> missing = lineMap.keySet().stream()
                .filter(id -> command.lines().stream().noneMatch(l -> l.asnLineId().equals(id)))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Missing inspection lines for asnLineIds: " + missing);
        }
        List<UUID> unknown = command.lines().stream()
                .map(RecordInspectionCommand.Line::asnLineId)
                .filter(id -> !lineMap.containsKey(id))
                .toList();
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown asnLineIds in inspection: " + unknown);
        }
    }

    private void publishInspectionCompleted(Inspection inspection, UUID warehouseId,
                                             String asnNo, Instant now, String actorId) {
        List<InspectionCompletedEvent.Line> lines = inspection.getLines().stream()
                .map(l -> new InspectionCompletedEvent.Line(
                        l.getId(), l.getAsnLineId(), l.getSkuId(), l.getLotId(), l.getLotNo(),
                        0, l.getQtyPassed(), l.getQtyDamaged(), l.getQtyShort()))
                .toList();
        List<InspectionCompletedEvent.DiscrepancySummary> discSummary = inspection.getDiscrepancies().stream()
                .map(d -> new InspectionCompletedEvent.DiscrepancySummary(
                        d.getId(), d.getAsnLineId(),
                        d.getDiscrepancyType().name(), d.getVariance(), d.isAcknowledged()))
                .toList();
        int discCount = (int) inspection.getDiscrepancies().stream()
                .filter(d -> !d.isAcknowledged()).count();
        InspectionCompletedEvent event = new InspectionCompletedEvent(
                inspection.getId(), inspection.getAsnId(), asnNo, warehouseId,
                inspection.getInspectorId(), inspection.getCompletedAt(),
                lines, discCount, discSummary, now, actorId);
        eventPort.publish(event);
    }

    static InspectionResult toResult(Inspection inspection) {
        List<InspectionResult.Line> lines = inspection.getLines().stream()
                .map(l -> new InspectionResult.Line(l.getId(), l.getAsnLineId(),
                        l.getSkuId(), l.getLotId(), l.getLotNo(),
                        l.getQtyPassed(), l.getQtyDamaged(), l.getQtyShort()))
                .toList();
        List<InspectionResult.Discrepancy> discs = inspection.getDiscrepancies().stream()
                .map(d -> new InspectionResult.Discrepancy(d.getId(), d.getAsnLineId(),
                        d.getDiscrepancyType().name(), d.getExpectedQty(), d.getActualTotalQty(),
                        d.getVariance(), d.isAcknowledged(), d.getAcknowledgedBy(),
                        d.getAcknowledgedAt(), d.getNotes()))
                .toList();
        return new InspectionResult(inspection.getId(), inspection.getAsnId(),
                inspection.getInspectorId(), inspection.getCompletedAt(), inspection.getNotes(),
                inspection.getVersion(), inspection.getCreatedAt(), lines, discs);
    }
}
