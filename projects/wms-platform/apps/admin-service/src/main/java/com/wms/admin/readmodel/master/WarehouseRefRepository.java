package com.wms.admin.readmodel.master;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WarehouseRefRepository extends JpaRepository<WarehouseRefEntity, UUID> {

    Page<WarehouseRefEntity> findAllBy(Pageable pageable);
}
