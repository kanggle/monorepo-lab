package com.wms.admin.readmodel.master;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LotRefRepository extends JpaRepository<LotRefEntity, UUID> {

    Page<LotRefEntity> findAllBy(Pageable pageable);

    Page<LotRefEntity> findBySkuId(UUID skuId, Pageable pageable);
}
