package com.wms.inbound.adapter.out.persistence.masterref;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkuSnapshotJpaRepository extends JpaRepository<SkuSnapshotJpaEntity, UUID> {

    Optional<SkuSnapshotJpaEntity> findBySkuCode(String skuCode);
}
