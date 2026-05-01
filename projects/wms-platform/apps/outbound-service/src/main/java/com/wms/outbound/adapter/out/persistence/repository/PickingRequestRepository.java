package com.wms.outbound.adapter.out.persistence.repository;

import com.wms.outbound.adapter.out.persistence.entity.PickingRequestEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PickingRequestRepository extends JpaRepository<PickingRequestEntity, UUID> {

    Optional<PickingRequestEntity> findByOrderId(UUID orderId);
}
