package com.example.scmplatform.logistics.adapter.outbound.persistence;

import com.example.scmplatform.logistics.application.port.outbound.DispatchPersistencePort;
import com.example.scmplatform.logistics.domain.model.Carrier;
import com.example.scmplatform.logistics.domain.model.CarrierCode;
import com.example.scmplatform.logistics.domain.model.Dispatch;
import com.example.scmplatform.logistics.domain.model.ShipmentId;
import com.example.scmplatform.logistics.domain.model.TrackingNo;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed {@link DispatchPersistencePort}. Maps between the framework-free {@link Dispatch}
 * aggregate and the package-private {@link DispatchJpaEntity}.
 */
@Component
public class DispatchPersistenceAdapter implements DispatchPersistencePort {

    private final DispatchJpaRepository repository;

    DispatchPersistenceAdapter(DispatchJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Dispatch> findById(UUID id) {
        return repository.findById(id).map(DispatchPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<Dispatch> findByShipmentId(UUID shipmentId) {
        return repository.findByShipmentId(shipmentId).map(DispatchPersistenceAdapter::toDomain);
    }

    @Override
    public Dispatch save(Dispatch dispatch) {
        DispatchJpaEntity entity = repository.findById(dispatch.getId())
                .orElseGet(DispatchJpaEntity::new);
        entity.setId(dispatch.getId());
        entity.setShipmentId(dispatch.getShipmentId().value());
        entity.setShipmentNo(dispatch.getShipmentNo());
        entity.setOrderId(dispatch.getOrderId());
        entity.setOrderNo(dispatch.getOrderNo());
        entity.setTenantId(dispatch.getTenantId());
        entity.setCarrierCode(dispatch.getCarrierCode() == null ? null : dispatch.getCarrierCode().value());
        entity.setTrackingNo(dispatch.getTrackingNo() == null ? null : dispatch.getTrackingNo().value());
        entity.setStatus(dispatch.getStatus());
        entity.setFailureReason(dispatch.getFailureReason());
        entity.setVendor(dispatch.getVendor() == null ? null : dispatch.getVendor().name());
        entity.setVersion(dispatch.getVersion());
        entity.setCreatedAt(dispatch.getCreatedAt());
        entity.setUpdatedAt(dispatch.getUpdatedAt());
        return toDomain(repository.save(entity));
    }

    private static Dispatch toDomain(DispatchJpaEntity e) {
        return Dispatch.reconstitute(
                e.getId(),
                ShipmentId.of(e.getShipmentId()),
                e.getShipmentNo(),
                e.getOrderId(),
                e.getOrderNo(),
                e.getTenantId(),
                e.getCarrierCode() == null ? null : CarrierCode.of(e.getCarrierCode()),
                e.getTrackingNo() == null ? null : TrackingNo.of(e.getTrackingNo()),
                e.getStatus(),
                e.getFailureReason(),
                e.getVendor() == null ? null : Carrier.valueOf(e.getVendor()),
                e.getVersion(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }
}
