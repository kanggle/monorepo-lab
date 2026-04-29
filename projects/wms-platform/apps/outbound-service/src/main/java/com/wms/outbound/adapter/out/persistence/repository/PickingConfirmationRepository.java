package com.wms.outbound.adapter.out.persistence.repository;

import com.wms.outbound.adapter.out.persistence.entity.PickingConfirmationEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PickingConfirmationRepository extends JpaRepository<PickingConfirmationEntity, UUID> {

    Optional<PickingConfirmationEntity> findByPickingRequestId(UUID pickingRequestId);
}
