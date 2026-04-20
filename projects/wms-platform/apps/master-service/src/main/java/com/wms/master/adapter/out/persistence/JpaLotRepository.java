package com.wms.master.adapter.out.persistence;

import com.wms.master.domain.model.LotStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaLotRepository extends JpaRepository<LotJpaEntity, UUID> {

    /**
     * Used by {@code SkuPersistenceAdapter.hasActiveLotsFor} — the reverse
     * guard that blocks SKU deactivation when active Lots still exist.
     */
    boolean existsBySkuIdAndStatus(UUID skuId, LotStatus status);

    /**
     * Scheduler query. Drives the daily {@code ACTIVE → EXPIRED} batch. The
     * matching partial index {@code idx_lots_expiry_active} (WHERE
     * status='ACTIVE') makes this a fast index-only scan in Postgres.
     */
    List<LotJpaEntity> findAllByStatusAndExpiryDateBefore(LotStatus status, LocalDate cutoff);

    /**
     * Filter by optional skuId, optional status, optional expiry window. Null
     * parameters mean "do not filter".
     */
    @Query("""
            SELECT l FROM LotJpaEntity l
            WHERE (:skuId IS NULL OR l.skuId = :skuId)
              AND (:status IS NULL OR l.status = :status)
              AND (:expiryBefore IS NULL OR (l.expiryDate IS NOT NULL AND l.expiryDate < :expiryBefore))
              AND (:expiryAfter  IS NULL OR (l.expiryDate IS NOT NULL AND l.expiryDate > :expiryAfter))
            """)
    Page<LotJpaEntity> search(
            @Param("skuId") UUID skuId,
            @Param("status") LotStatus status,
            @Param("expiryBefore") LocalDate expiryBefore,
            @Param("expiryAfter") LocalDate expiryAfter,
            Pageable pageable);
}
