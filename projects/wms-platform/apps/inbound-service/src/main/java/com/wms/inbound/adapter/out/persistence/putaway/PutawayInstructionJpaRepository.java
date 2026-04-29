package com.wms.inbound.adapter.out.persistence.putaway;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PutawayInstructionJpaRepository extends JpaRepository<PutawayInstructionJpaEntity, UUID> {

    Optional<PutawayInstructionJpaEntity> findByAsnId(UUID asnId);

    /**
     * Pessimistic lock variant. Used by confirm / skip use-cases to serialise
     * concurrent last-line attempts at the DB level — supplements the
     * optimistic {@code version} check.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PutawayInstructionJpaEntity p WHERE p.id = :id")
    Optional<PutawayInstructionJpaEntity> findByIdForUpdate(@Param("id") UUID id);
}
