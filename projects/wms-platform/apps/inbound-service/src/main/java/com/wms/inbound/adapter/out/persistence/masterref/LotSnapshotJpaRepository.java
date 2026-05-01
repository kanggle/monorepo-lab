package com.wms.inbound.adapter.out.persistence.masterref;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LotSnapshotJpaRepository extends JpaRepository<LotSnapshotJpaEntity, UUID> {

    Optional<LotSnapshotJpaEntity> findBySkuIdAndLotNo(UUID skuId, String lotNo);
}
