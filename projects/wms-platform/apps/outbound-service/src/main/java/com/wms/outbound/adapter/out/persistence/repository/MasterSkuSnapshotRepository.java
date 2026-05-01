package com.wms.outbound.adapter.out.persistence.repository;

import com.wms.outbound.adapter.out.persistence.entity.MasterSkuSnapshot;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MasterSkuSnapshotRepository extends JpaRepository<MasterSkuSnapshot, UUID> {

    Optional<MasterSkuSnapshot> findBySkuCode(String skuCode);
}
