package com.wms.outbound.adapter.out.persistence.repository;

import com.wms.outbound.adapter.out.persistence.entity.PackingUnitLineEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PackingUnitLineRepository extends JpaRepository<PackingUnitLineEntity, UUID> {

    List<PackingUnitLineEntity> findByPackingUnitId(UUID packingUnitId);

    List<PackingUnitLineEntity> findByPackingUnitIdIn(Collection<UUID> packingUnitIds);
}
