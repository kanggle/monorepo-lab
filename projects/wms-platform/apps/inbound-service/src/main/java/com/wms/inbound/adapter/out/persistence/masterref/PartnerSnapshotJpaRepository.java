package com.wms.inbound.adapter.out.persistence.masterref;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartnerSnapshotJpaRepository extends JpaRepository<PartnerSnapshotJpaEntity, UUID> {

    Optional<PartnerSnapshotJpaEntity> findByPartnerCode(String partnerCode);
}
