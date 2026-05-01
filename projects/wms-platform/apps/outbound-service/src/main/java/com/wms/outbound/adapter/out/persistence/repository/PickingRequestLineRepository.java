package com.wms.outbound.adapter.out.persistence.repository;

import com.wms.outbound.adapter.out.persistence.entity.PickingRequestLineEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PickingRequestLineRepository extends JpaRepository<PickingRequestLineEntity, UUID> {

    List<PickingRequestLineEntity> findByPickingRequestId(UUID pickingRequestId);
}
