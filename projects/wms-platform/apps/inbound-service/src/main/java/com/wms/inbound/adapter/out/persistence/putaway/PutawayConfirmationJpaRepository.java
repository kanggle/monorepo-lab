package com.wms.inbound.adapter.out.persistence.putaway;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PutawayConfirmationJpaRepository extends JpaRepository<PutawayConfirmationJpaEntity, UUID> {

    Optional<PutawayConfirmationJpaEntity> findByPutawayLineId(UUID putawayLineId);
}
