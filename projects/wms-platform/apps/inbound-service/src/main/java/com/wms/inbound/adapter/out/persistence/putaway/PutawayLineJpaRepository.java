package com.wms.inbound.adapter.out.persistence.putaway;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PutawayLineJpaRepository extends JpaRepository<PutawayLineJpaEntity, UUID> {

    List<PutawayLineJpaEntity> findByPutawayInstructionId(UUID putawayInstructionId);
}
