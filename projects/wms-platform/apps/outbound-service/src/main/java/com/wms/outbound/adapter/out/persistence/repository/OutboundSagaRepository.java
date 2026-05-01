package com.wms.outbound.adapter.out.persistence.repository;

import com.wms.outbound.adapter.out.persistence.entity.OutboundSagaEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboundSagaRepository extends JpaRepository<OutboundSagaEntity, UUID> {

    Optional<OutboundSagaEntity> findByOrderId(UUID orderId);

    Optional<OutboundSagaEntity> findByPickingRequestId(UUID pickingRequestId);

    /**
     * Bulk lookup used by {@code OrderQueryService.list} to avoid N+1.
     */
    List<OutboundSagaEntity> findByOrderIdIn(Collection<UUID> orderIds);
}
