package com.wms.outbound.adapter.out.persistence.adapter;

import com.wms.outbound.adapter.out.persistence.entity.PickingRequestEntity;
import com.wms.outbound.adapter.out.persistence.entity.PickingRequestLineEntity;
import com.wms.outbound.adapter.out.persistence.repository.PickingRequestLineRepository;
import com.wms.outbound.adapter.out.persistence.repository.PickingRequestRepository;
import com.wms.outbound.application.port.out.PickingPersistencePort;
import com.wms.outbound.domain.model.PickingRequest;
import com.wms.outbound.domain.model.PickingRequestLine;
import com.wms.outbound.domain.model.PickingRequestStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence adapter for the {@link PickingRequest} aggregate.
 */
@Component
public class PickingPersistenceAdapter implements PickingPersistencePort {

    private final PickingRequestRepository requestRepo;
    private final PickingRequestLineRepository lineRepo;

    public PickingPersistenceAdapter(PickingRequestRepository requestRepo,
                                     PickingRequestLineRepository lineRepo) {
        this.requestRepo = requestRepo;
        this.lineRepo = lineRepo;
    }

    @Override
    @Transactional
    public PickingRequest save(PickingRequest request) {
        Optional<PickingRequestEntity> existing = requestRepo.findById(request.getId());
        PickingRequestEntity entity;
        if (existing.isEmpty()) {
            entity = new PickingRequestEntity(
                    request.getId(),
                    request.getOrderId(),
                    request.getSagaId(),
                    request.getWarehouseId(),
                    request.getStatus().name(),
                    request.getCreatedAt(),
                    request.getUpdatedAt());
            entity = requestRepo.save(entity);
            for (PickingRequestLine line : request.getLines()) {
                lineRepo.save(new PickingRequestLineEntity(
                        line.getId(),
                        entity.getId(),
                        line.getOrderLineId(),
                        line.getSkuId(),
                        line.getLotId(),
                        line.getLocationId(),
                        line.getQtyToPick()));
            }
        } else {
            entity = existing.get();
            entity.setStatus(request.getStatus().name());
            entity.setUpdatedAt(request.getUpdatedAt());
            entity.setSagaId(request.getSagaId());
            entity = requestRepo.save(entity);
        }
        List<PickingRequestLineEntity> lineEntities = lineRepo.findByPickingRequestId(entity.getId());
        return toDomain(entity, lineEntities);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PickingRequest> findById(UUID id) {
        return requestRepo.findById(id)
                .map(e -> toDomain(e, lineRepo.findByPickingRequestId(e.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PickingRequest> findByOrderId(UUID orderId) {
        return requestRepo.findByOrderId(orderId)
                .map(e -> toDomain(e, lineRepo.findByPickingRequestId(e.getId())));
    }

    private static PickingRequest toDomain(PickingRequestEntity e,
                                           List<PickingRequestLineEntity> lineEntities) {
        List<PickingRequestLine> lines = new ArrayList<>(lineEntities.size());
        for (PickingRequestLineEntity le : lineEntities) {
            lines.add(new PickingRequestLine(
                    le.getId(),
                    le.getPickingRequestId(),
                    le.getOrderLineId(),
                    le.getSkuId(),
                    le.getLotId(),
                    le.getLocationId(),
                    le.getQtyToPick()));
        }
        return new PickingRequest(
                e.getId(),
                e.getOrderId(),
                e.getSagaId() != null ? e.getSagaId() : e.getId(),
                e.getWarehouseId(),
                PickingRequestStatus.valueOf(e.getStatus()),
                e.getVersion(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                lines);
    }
}
