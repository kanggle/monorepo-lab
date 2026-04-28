package com.wms.inbound.adapter.out.persistence.masterref;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneSnapshotJpaRepository extends JpaRepository<ZoneSnapshotJpaEntity, UUID> {
}
