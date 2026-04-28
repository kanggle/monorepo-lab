package com.wms.inventory.adapter.out.persistence.masterref;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LotSnapshotJpaRepository extends JpaRepository<LotSnapshotJpaEntity, UUID> {
}
