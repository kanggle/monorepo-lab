package com.wms.outbound.adapter.out.persistence.repository;

import com.wms.outbound.adapter.out.persistence.entity.OutboundSagaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboundSagaRepository extends JpaRepository<OutboundSagaEntity, UUID> {

    Optional<OutboundSagaEntity> findByOrderId(UUID orderId);

    Optional<OutboundSagaEntity> findByPickingRequestId(UUID pickingRequestId);
}
