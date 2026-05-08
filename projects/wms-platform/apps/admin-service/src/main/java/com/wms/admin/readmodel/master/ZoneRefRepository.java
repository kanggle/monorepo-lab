package com.wms.admin.readmodel.master;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneRefRepository extends JpaRepository<ZoneRefEntity, UUID> {

    Page<ZoneRefEntity> findAllBy(Pageable pageable);

    Page<ZoneRefEntity> findByWarehouseId(UUID warehouseId, Pageable pageable);
}
