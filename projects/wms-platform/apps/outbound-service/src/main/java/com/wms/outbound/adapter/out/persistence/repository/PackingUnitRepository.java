package com.wms.outbound.adapter.out.persistence.repository;

import com.wms.outbound.adapter.out.persistence.entity.PackingUnitEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PackingUnitRepository extends JpaRepository<PackingUnitEntity, UUID> {

    List<PackingUnitEntity> findByOrderId(UUID orderId);

    List<PackingUnitEntity> findByOrderIdAndStatus(UUID orderId, String status);
}
