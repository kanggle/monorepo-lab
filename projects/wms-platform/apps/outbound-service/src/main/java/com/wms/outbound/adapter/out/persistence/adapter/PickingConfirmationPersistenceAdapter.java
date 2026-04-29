package com.wms.outbound.adapter.out.persistence.adapter;

import com.wms.outbound.adapter.out.persistence.entity.PickingConfirmationEntity;
import com.wms.outbound.adapter.out.persistence.entity.PickingConfirmationLineEntity;
import com.wms.outbound.adapter.out.persistence.repository.PickingConfirmationLineRepository;
import com.wms.outbound.adapter.out.persistence.repository.PickingConfirmationRepository;
import com.wms.outbound.application.port.out.PickingConfirmationPersistencePort;
import com.wms.outbound.domain.model.PickingConfirmation;
import com.wms.outbound.domain.model.PickingConfirmationLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence adapter for {@link PickingConfirmation} (append-only).
 */
@Component
public class PickingConfirmationPersistenceAdapter implements PickingConfirmationPersistencePort {

    private final PickingConfirmationRepository confirmRepo;
    private final PickingConfirmationLineRepository lineRepo;

    public PickingConfirmationPersistenceAdapter(PickingConfirmationRepository confirmRepo,
                                                 PickingConfirmationLineRepository lineRepo) {
        this.confirmRepo = confirmRepo;
        this.lineRepo = lineRepo;
    }

    @Override
    @Transactional
    public PickingConfirmation save(PickingConfirmation confirmation) {
        PickingConfirmationEntity entity = new PickingConfirmationEntity(
                confirmation.getId(),
                confirmation.getPickingRequestId(),
                confirmation.getOrderId(),
                confirmation.getConfirmedBy(),
                confirmation.getConfirmedAt(),
                confirmation.getNotes());
        PickingConfirmationEntity saved = confirmRepo.save(entity);
        for (PickingConfirmationLine line : confirmation.getLines()) {
            lineRepo.save(new PickingConfirmationLineEntity(
                    line.getId(),
                    saved.getId(),
                    line.getOrderLineId(),
                    line.getSkuId(),
                    line.getLotId(),
                    line.getActualLocationId(),
                    line.getQtyConfirmed()));
        }
        return toDomain(saved, lineRepo.findByPickingConfirmationId(saved.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PickingConfirmation> findByPickingRequestId(UUID pickingRequestId) {
        return confirmRepo.findByPickingRequestId(pickingRequestId)
                .map(e -> toDomain(e, lineRepo.findByPickingConfirmationId(e.getId())));
    }

    private static PickingConfirmation toDomain(PickingConfirmationEntity e,
                                                List<PickingConfirmationLineEntity> lineEntities) {
        List<PickingConfirmationLine> lines = new ArrayList<>(lineEntities.size());
        for (PickingConfirmationLineEntity le : lineEntities) {
            UUID orderLineId = le.getOrderLineId();
            int qty = le.getQtyConfirmed() != null ? le.getQtyConfirmed() : le.getPickedQty();
            UUID skuId = le.getSkuId();
            UUID actualLocationId = le.getActualLocationId();
            // Defensive nulls on bootstrap rows are unlikely after V11 + spec-aligned writes.
            lines.add(new PickingConfirmationLine(
                    le.getId(),
                    le.getPickingConfirmationId(),
                    orderLineId,
                    skuId,
                    le.getLotId(),
                    actualLocationId,
                    qty));
        }
        return new PickingConfirmation(
                e.getId(),
                e.getPickingRequestId(),
                e.getOrderId(),
                e.getConfirmedBy(),
                e.getConfirmedAt(),
                e.getNotes(),
                lines);
    }
}
