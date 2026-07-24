package com.example.scmplatform.logistics.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Package-private Spring Data repository for {@link DispatchJpaEntity}. */
interface DispatchJpaRepository extends JpaRepository<DispatchJpaEntity, UUID> {

    Optional<DispatchJpaEntity> findByShipmentId(UUID shipmentId);
}
