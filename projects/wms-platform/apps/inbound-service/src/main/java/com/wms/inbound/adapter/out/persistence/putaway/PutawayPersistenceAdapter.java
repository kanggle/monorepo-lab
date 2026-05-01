package com.wms.inbound.adapter.out.persistence.putaway;

import com.wms.inbound.application.port.out.PutawayPersistencePort;
import com.wms.inbound.domain.exception.PutawayInstructionNotFoundException;
import com.wms.inbound.domain.model.PutawayConfirmation;
import com.wms.inbound.domain.model.PutawayInstruction;
import com.wms.inbound.domain.model.PutawayInstructionStatus;
import com.wms.inbound.domain.model.PutawayLine;
import com.wms.inbound.domain.model.PutawayLineStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PutawayPersistenceAdapter implements PutawayPersistencePort {

    private final PutawayInstructionJpaRepository instructionRepo;
    private final PutawayLineJpaRepository lineRepo;
    private final PutawayConfirmationJpaRepository confirmationRepo;

    public PutawayPersistenceAdapter(PutawayInstructionJpaRepository instructionRepo,
                                      PutawayLineJpaRepository lineRepo,
                                      PutawayConfirmationJpaRepository confirmationRepo) {
        this.instructionRepo = instructionRepo;
        this.lineRepo = lineRepo;
        this.confirmationRepo = confirmationRepo;
    }

    @Override
    @Transactional
    public PutawayInstruction save(PutawayInstruction instruction) {
        PutawayInstructionJpaEntity entity = instructionRepo.findById(instruction.getId()).orElse(null);
        if (entity == null) {
            entity = new PutawayInstructionJpaEntity(instruction.getId(), instruction.getAsnId(),
                    instruction.getWarehouseId(), instruction.getPlannedBy(),
                    instruction.getStatus().name(), instruction.getVersion(),
                    instruction.getCreatedAt(), instruction.getCreatedBy(),
                    instruction.getUpdatedAt(), instruction.getUpdatedBy());
        } else {
            entity.setStatus(instruction.getStatus().name());
            entity.setUpdatedAt(instruction.getUpdatedAt());
            entity.setUpdatedBy(instruction.getUpdatedBy());
        }

        List<PutawayLineJpaEntity> lineEntities = instruction.getLines().stream()
                .map(l -> new PutawayLineJpaEntity(l.getId(), l.getPutawayInstructionId(),
                        l.getAsnLineId(), l.getSkuId(), l.getLotId(), l.getLotNo(),
                        l.getDestinationLocationId(), l.getQtyToPutaway(),
                        l.getStatus().name()))
                .toList();
        entity.setLines(lineEntities);

        PutawayInstructionJpaEntity saved = instructionRepo.save(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PutawayInstruction> findById(UUID id) {
        return instructionRepo.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PutawayInstruction> findByAsnId(UUID asnId) {
        return instructionRepo.findByAsnId(asnId).map(this::toDomain);
    }

    @Override
    public PutawayInstruction findByIdForUpdateOrThrow(UUID id) {
        PutawayInstructionJpaEntity entity = instructionRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new PutawayInstructionNotFoundException(id));
        return toDomain(entity);
    }

    @Override
    @Transactional
    public PutawayConfirmation saveConfirmation(PutawayConfirmation confirmation) {
        // Append-only: persist via direct INSERT (no merge / no update path).
        PutawayConfirmationJpaEntity entity = new PutawayConfirmationJpaEntity(
                confirmation.id(), confirmation.putawayInstructionId(), confirmation.putawayLineId(),
                confirmation.skuId(), confirmation.lotId(),
                confirmation.plannedLocationId(), confirmation.actualLocationId(),
                confirmation.qtyConfirmed(), confirmation.confirmedBy(), confirmation.confirmedAt());
        PutawayConfirmationJpaEntity saved = confirmationRepo.save(entity);
        return toConfirmationDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PutawayConfirmation> findConfirmationByLineId(UUID lineId) {
        return confirmationRepo.findByPutawayLineId(lineId).map(PutawayPersistenceAdapter::toConfirmationDomain);
    }

    private PutawayInstruction toDomain(PutawayInstructionJpaEntity e) {
        // Use eagerly-fetched mapped collection if loaded; fall back to a query.
        List<PutawayLineJpaEntity> lines = e.getLines();
        if (lines == null || lines.isEmpty()) {
            lines = lineRepo.findByPutawayInstructionId(e.getId());
        }
        List<PutawayLine> domainLines = lines.stream().map(PutawayPersistenceAdapter::toLineDomain).toList();
        return new PutawayInstruction(e.getId(), e.getAsnId(), e.getWarehouseId(),
                e.getPlannedBy(), PutawayInstructionStatus.valueOf(e.getStatus()),
                e.getVersion(), e.getCreatedAt(), e.getCreatedBy(),
                e.getUpdatedAt(), e.getUpdatedBy(), domainLines);
    }

    private static PutawayLine toLineDomain(PutawayLineJpaEntity e) {
        return new PutawayLine(e.getId(), e.getPutawayInstructionId(), e.getAsnLineId(),
                e.getSkuId(), e.getLotId(), e.getLotNo(),
                e.getDestinationLocationId(), e.getQtyToPutaway(),
                PutawayLineStatus.valueOf(e.getStatus()));
    }

    private static PutawayConfirmation toConfirmationDomain(PutawayConfirmationJpaEntity e) {
        return new PutawayConfirmation(e.getId(), e.getPutawayInstructionId(), e.getPutawayLineId(),
                e.getSkuId(), e.getLotId(),
                e.getPlannedLocationId(), e.getActualLocationId(),
                e.getQtyConfirmed(), e.getConfirmedBy(), e.getConfirmedAt());
    }
}
