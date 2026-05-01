package com.wms.inbound.adapter.out.persistence.inspection;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface InspectionJpaRepository extends JpaRepository<InspectionJpaEntity, UUID> {

    Optional<InspectionJpaEntity> findByAsnId(UUID asnId);
}
