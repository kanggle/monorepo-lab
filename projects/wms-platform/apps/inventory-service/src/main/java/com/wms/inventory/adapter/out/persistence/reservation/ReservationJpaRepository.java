package com.wms.inventory.adapter.out.persistence.reservation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationJpaRepository extends JpaRepository<ReservationJpaEntity, UUID> {

    Optional<ReservationJpaEntity> findByPickingRequestId(UUID pickingRequestId);

    @Query("""
            SELECT r FROM ReservationJpaEntity r
             WHERE r.status = 'RESERVED' AND r.expiresAt <= :asOf
             ORDER BY r.expiresAt ASC
            """)
    List<ReservationJpaEntity> findExpired(@Param("asOf") Instant asOf, Pageable pageable);

    long countByStatus(String status);
}
