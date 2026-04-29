package com.wms.outbound.adapter.out.persistence.repository;

import com.wms.outbound.adapter.out.persistence.entity.MasterWarehouseSnapshot;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MasterWarehouseSnapshotRepository extends JpaRepository<MasterWarehouseSnapshot, UUID> {

    Optional<MasterWarehouseSnapshot> findByWarehouseCode(String warehouseCode);
}
