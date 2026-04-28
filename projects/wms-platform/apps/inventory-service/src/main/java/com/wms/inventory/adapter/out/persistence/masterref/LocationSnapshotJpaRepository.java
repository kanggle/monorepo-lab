package com.wms.inventory.adapter.out.persistence.masterref;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationSnapshotJpaRepository extends JpaRepository<LocationSnapshotJpaEntity, UUID> {
}
