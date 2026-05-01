package com.wms.outbound.adapter.out.persistence.repository;

import com.wms.outbound.adapter.out.persistence.entity.PickingConfirmationLineEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PickingConfirmationLineRepository
        extends JpaRepository<PickingConfirmationLineEntity, UUID> {

    List<PickingConfirmationLineEntity> findByPickingConfirmationId(UUID pickingConfirmationId);
}
