package com.wms.outbound.adapter.out.persistence.adapter;

import com.wms.outbound.adapter.out.persistence.entity.ShipmentEntity;
import com.wms.outbound.adapter.out.persistence.repository.ShipmentRepository;
import com.wms.outbound.application.port.out.ShipmentPersistencePort;
import com.wms.outbound.domain.model.Shipment;
import com.wms.outbound.domain.model.TmsStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence adapter for the {@link Shipment} aggregate.
 */
@Component
public class ShipmentPersistenceAdapter implements ShipmentPersistencePort {

    private final ShipmentRepository repo;

    public ShipmentPersistenceAdapter(ShipmentRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public Shipment save(Shipment shipment) {
        Optional<ShipmentEntity> existing = repo.findById(shipment.getId());
        ShipmentEntity entity;
        if (existing.isEmpty()) {
            entity = new ShipmentEntity(
                    shipment.getId(),
                    shipment.getOrderId(),
                    shipment.getShipmentNo(),
                    shipment.getCarrierCode(),
                    shipment.getTrackingNo(),
                    shipment.getShippedAt(),
                    shipment.getTmsStatus().name(),
                    shipment.getTmsNotifiedAt(),
                    shipment.getTmsRequestId(),
                    shipment.getCreatedAt(),
                    shipment.getUpdatedAt());
        } else {
            entity = existing.get();
            entity.setCarrierCode(shipment.getCarrierCode());
            entity.setTrackingNo(shipment.getTrackingNo());
            entity.setTmsStatus(shipment.getTmsStatus().name());
            entity.setTmsNotifiedAt(shipment.getTmsNotifiedAt());
            entity.setTmsRequestId(shipment.getTmsRequestId());
            entity.setUpdatedAt(shipment.getUpdatedAt());
        }
        ShipmentEntity saved = repo.save(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Shipment> findById(UUID id) {
        return repo.findById(id).map(ShipmentPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Shipment> findByOrderId(UUID orderId) {
        return repo.findByOrderId(orderId).map(ShipmentPersistenceAdapter::toDomain);
    }

    private static Shipment toDomain(ShipmentEntity e) {
        return new Shipment(
                e.getId(),
                e.getOrderId(),
                e.getShipmentNo() != null ? e.getShipmentNo() : "",
                e.getCarrierCode(),
                e.getTrackingNo(),
                e.getShippedAt(),
                TmsStatus.valueOf(e.getTmsStatus() != null ? e.getTmsStatus() : TmsStatus.PENDING.name()),
                e.getTmsNotifiedAt(),
                e.getTmsRequestId(),
                e.getVersion(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }
}
