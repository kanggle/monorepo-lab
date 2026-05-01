package com.wms.inbound.adapter.out.persistence.inspection;

import com.wms.inbound.application.port.out.InspectionPersistencePort;
import com.wms.inbound.domain.model.DiscrepancyType;
import com.wms.inbound.domain.model.Inspection;
import com.wms.inbound.domain.model.InspectionDiscrepancy;
import com.wms.inbound.domain.model.InspectionLine;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InspectionPersistenceAdapter implements InspectionPersistencePort {

    private final InspectionJpaRepository inspectionRepo;
    private final InspectionDiscrepancyJpaRepository discrepancyRepo;

    public InspectionPersistenceAdapter(InspectionJpaRepository inspectionRepo,
                                         InspectionDiscrepancyJpaRepository discrepancyRepo) {
        this.inspectionRepo = inspectionRepo;
        this.discrepancyRepo = discrepancyRepo;
    }

    @Override
    @Transactional
    public Inspection save(Inspection inspection) {
        InspectionJpaEntity entity = inspectionRepo.findById(inspection.getId()).orElse(null);
        if (entity == null) {
            entity = new InspectionJpaEntity(inspection.getId(), inspection.getAsnId(),
                    inspection.getInspectorId(), inspection.getCompletedAt(),
                    inspection.getNotes(), inspection.getVersion(),
                    inspection.getCreatedAt(), inspection.getCreatedBy(),
                    inspection.getUpdatedAt(), inspection.getUpdatedBy());
        } else {
            entity.setCompletedAt(inspection.getCompletedAt());
            entity.setUpdatedAt(inspection.getUpdatedAt());
            entity.setUpdatedBy(inspection.getUpdatedBy());
        }

        List<InspectionLineJpaEntity> lineEntities = inspection.getLines().stream()
                .map(l -> new InspectionLineJpaEntity(l.getId(), l.getInspectionId(),
                        l.getAsnLineId(), l.getSkuId(), l.getLotId(), l.getLotNo(),
                        l.getQtyPassed(), l.getQtyDamaged(), l.getQtyShort()))
                .toList();
        entity.setLines(lineEntities);

        List<InspectionDiscrepancyJpaEntity> discEntities = inspection.getDiscrepancies().stream()
                .map(d -> new InspectionDiscrepancyJpaEntity(d.getId(), d.getInspectionId(),
                        d.getAsnLineId(), d.getDiscrepancyType().name(),
                        d.getExpectedQty(), d.getActualTotalQty(), d.getVariance(),
                        d.isAcknowledged(), d.getAcknowledgedBy(), d.getAcknowledgedAt(),
                        d.getNotes()))
                .toList();
        entity.setDiscrepancies(discEntities);

        InspectionJpaEntity saved = inspectionRepo.save(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Inspection> findById(UUID id) {
        return inspectionRepo.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Inspection> findByAsnId(UUID asnId) {
        return inspectionRepo.findByAsnId(asnId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InspectionDiscrepancy> findDiscrepancyById(UUID discrepancyId) {
        return discrepancyRepo.findById(discrepancyId).map(InspectionPersistenceAdapter::toDiscrepancyDomain);
    }

    @Override
    @Transactional
    public InspectionDiscrepancy saveDiscrepancy(InspectionDiscrepancy discrepancy) {
        InspectionDiscrepancyJpaEntity entity = discrepancyRepo.findById(discrepancy.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Discrepancy entity not found: " + discrepancy.getId()));
        entity.setAcknowledged(discrepancy.isAcknowledged());
        entity.setAcknowledgedBy(discrepancy.getAcknowledgedBy());
        entity.setAcknowledgedAt(discrepancy.getAcknowledgedAt());
        return toDiscrepancyDomain(discrepancyRepo.save(entity));
    }

    private Inspection toDomain(InspectionJpaEntity e) {
        List<InspectionLine> lines = e.getLines().stream()
                .map(l -> new InspectionLine(l.getId(), l.getInspectionId(), l.getAsnLineId(),
                        l.getSkuId(), l.getLotId(), l.getLotNo(),
                        l.getQtyPassed(), l.getQtyDamaged(), l.getQtyShort()))
                .toList();
        List<InspectionDiscrepancy> discs = e.getDiscrepancies().stream()
                .map(InspectionPersistenceAdapter::toDiscrepancyDomain)
                .toList();
        return new Inspection(e.getId(), e.getAsnId(), e.getInspectorId(),
                e.getCompletedAt(), e.getNotes(), e.getVersion(),
                e.getCreatedAt(), e.getCreatedBy(), e.getUpdatedAt(), e.getUpdatedBy(),
                lines, discs);
    }

    private static InspectionDiscrepancy toDiscrepancyDomain(InspectionDiscrepancyJpaEntity e) {
        return new InspectionDiscrepancy(e.getId(), e.getInspectionId(), e.getAsnLineId(),
                DiscrepancyType.valueOf(e.getDiscrepancyType()),
                e.getExpectedQty(), e.getActualTotalQty(), e.getVariance(),
                e.isAcknowledged(), e.getAcknowledgedBy(), e.getAcknowledgedAt(), e.getNotes());
    }
}
