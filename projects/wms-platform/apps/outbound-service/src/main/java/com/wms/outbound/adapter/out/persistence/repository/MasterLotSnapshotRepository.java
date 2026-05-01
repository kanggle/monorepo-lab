package com.wms.outbound.adapter.out.persistence.repository;

import com.wms.outbound.adapter.out.persistence.entity.MasterLotSnapshot;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MasterLotSnapshotRepository extends JpaRepository<MasterLotSnapshot, UUID> {

    Optional<MasterLotSnapshot> findBySkuIdAndLotNo(UUID skuId, String lotNo);
}
