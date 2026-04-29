package com.wms.outbound.adapter.out.persistence.adapter;

import com.wms.outbound.adapter.out.persistence.entity.PackingUnitEntity;
import com.wms.outbound.adapter.out.persistence.entity.PackingUnitLineEntity;
import com.wms.outbound.adapter.out.persistence.repository.PackingUnitLineRepository;
import com.wms.outbound.adapter.out.persistence.repository.PackingUnitRepository;
import com.wms.outbound.application.port.out.PackingPersistencePort;
import com.wms.outbound.domain.model.PackingType;
import com.wms.outbound.domain.model.PackingUnit;
import com.wms.outbound.domain.model.PackingUnitLine;
import com.wms.outbound.domain.model.PackingUnitStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence adapter for the {@link PackingUnit} aggregate.
 */
@Component
public class PackingPersistenceAdapter implements PackingPersistencePort {

    private final PackingUnitRepository unitRepo;
    private final PackingUnitLineRepository lineRepo;

    public PackingPersistenceAdapter(PackingUnitRepository unitRepo,
                                     PackingUnitLineRepository lineRepo) {
        this.unitRepo = unitRepo;
        this.lineRepo = lineRepo;
    }

    @Override
    @Transactional
    public PackingUnit save(PackingUnit unit) {
        Optional<PackingUnitEntity> existing = unitRepo.findById(unit.getId());
        PackingUnitEntity entity;
        if (existing.isEmpty()) {
            entity = new PackingUnitEntity(
                    unit.getId(),
                    unit.getOrderId(),
                    unit.getCartonNo(),
                    unit.getPackingType().name(),
                    unit.getWeightGrams(),
                    unit.getLengthMm(),
                    unit.getWidthMm(),
                    unit.getHeightMm(),
                    unit.getNotes(),
                    unit.getStatus().name(),
                    unit.getCreatedAt(),
                    unit.getUpdatedAt());
            entity = unitRepo.save(entity);
            for (PackingUnitLine line : unit.getLines()) {
                lineRepo.save(new PackingUnitLineEntity(
                        line.getId(),
                        entity.getId(),
                        line.getOrderLineId(),
                        line.getSkuId(),
                        line.getLotId(),
                        line.getQty()));
            }
        } else {
            entity = existing.get();
            entity.setStatus(unit.getStatus().name());
            entity.setUpdatedAt(unit.getUpdatedAt());
            if (unit.getStatus() == PackingUnitStatus.SEALED) {
                entity.setPackedAt(unit.getUpdatedAt());
            }
            entity = unitRepo.save(entity);
        }
        return toDomain(entity, lineRepo.findByPackingUnitId(entity.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PackingUnit> findById(UUID id) {
        return unitRepo.findById(id)
                .map(e -> toDomain(e, lineRepo.findByPackingUnitId(e.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PackingUnit> findByOrderId(UUID orderId) {
        return loadUnits(unitRepo.findByOrderId(orderId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PackingUnit> findUnsealedByOrderId(UUID orderId) {
        return loadUnits(unitRepo.findByOrderIdAndStatus(orderId, PackingUnitStatus.OPEN.name()));
    }

    private List<PackingUnit> loadUnits(List<PackingUnitEntity> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = new ArrayList<>(entities.size());
        for (PackingUnitEntity e : entities) {
            ids.add(e.getId());
        }
        Map<UUID, List<PackingUnitLineEntity>> linesByUnit = new HashMap<>();
        for (PackingUnitLineEntity le : lineRepo.findByPackingUnitIdIn(ids)) {
            linesByUnit.computeIfAbsent(le.getPackingUnitId(), k -> new ArrayList<>()).add(le);
        }
        List<PackingUnit> out = new ArrayList<>(entities.size());
        for (PackingUnitEntity e : entities) {
            out.add(toDomain(e, linesByUnit.getOrDefault(e.getId(), List.of())));
        }
        return out;
    }

    private static PackingUnit toDomain(PackingUnitEntity e, List<PackingUnitLineEntity> lineEntities) {
        List<PackingUnitLine> lines = new ArrayList<>(lineEntities.size());
        for (PackingUnitLineEntity le : lineEntities) {
            lines.add(new PackingUnitLine(
                    le.getId(),
                    le.getPackingUnitId(),
                    le.getOrderLineId(),
                    le.getSkuId(),
                    le.getLotId(),
                    le.getQty()));
        }
        return new PackingUnit(
                e.getId(),
                e.getOrderId(),
                e.getCartonNo() != null ? e.getCartonNo() : "",
                e.getPackingType() != null ? PackingType.valueOf(e.getPackingType()) : PackingType.BOX,
                e.getWeightGrams(),
                e.getLengthMm(),
                e.getWidthMm(),
                e.getHeightMm(),
                e.getNotes(),
                PackingUnitStatus.valueOf(mapStatus(e.getStatus())),
                e.getVersion(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                lines);
    }

    private static String mapStatus(String dbStatus) {
        // V4 default 'PACKING' is treated as OPEN.
        if (dbStatus == null || "PACKING".equals(dbStatus)) {
            return PackingUnitStatus.OPEN.name();
        }
        return dbStatus;
    }
}
