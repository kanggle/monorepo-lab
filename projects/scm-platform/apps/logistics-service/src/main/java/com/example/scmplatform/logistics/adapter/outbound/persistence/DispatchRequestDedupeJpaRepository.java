package com.example.scmplatform.logistics.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Package-private Spring Data repository for {@link DispatchRequestDedupeJpaEntity}. */
interface DispatchRequestDedupeJpaRepository extends JpaRepository<DispatchRequestDedupeJpaEntity, UUID> {
}
