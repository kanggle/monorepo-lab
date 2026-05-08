package com.wms.admin.readmodel.master;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRefRepository extends JpaRepository<LocationRefEntity, UUID> {

    Page<LocationRefEntity> findAllBy(Pageable pageable);

    Page<LocationRefEntity> findByWarehouseId(UUID warehouseId, Pageable pageable);
}
