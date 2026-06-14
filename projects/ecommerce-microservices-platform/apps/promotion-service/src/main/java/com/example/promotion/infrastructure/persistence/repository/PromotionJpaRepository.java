package com.example.promotion.infrastructure.persistence.repository;

import com.example.promotion.infrastructure.persistence.entity.PromotionJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface PromotionJpaRepository extends JpaRepository<PromotionJpaEntity, String> {

    // ---- Tenant-scoped admin read paths (ADR-MONO-030 M3; TASK-BE-368) ----------
    // Every HTTP read filters WHERE tenant_id = :tenantId so a cross-tenant id
    // resolves to empty → 404 (existence hidden), and a list excludes other tenants.

    Optional<PromotionJpaEntity> findByPromotionIdAndTenantId(String promotionId, String tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PromotionJpaEntity p WHERE p.promotionId = :promotionId AND p.tenantId = :tenantId")
    Optional<PromotionJpaEntity> findByIdForUpdate(String promotionId, String tenantId);

    Page<PromotionJpaEntity> findByTenantId(String tenantId, Pageable pageable);

    @Query("SELECT p FROM PromotionJpaEntity p WHERE p.tenantId = :tenantId AND p.startDate <= :now AND p.endDate >= :now")
    Page<PromotionJpaEntity> findActive(String tenantId, Instant now, Pageable pageable);

    @Query("SELECT p FROM PromotionJpaEntity p WHERE p.tenantId = :tenantId AND p.startDate > :now")
    Page<PromotionJpaEntity> findScheduled(String tenantId, Instant now, Pageable pageable);

    @Query("SELECT p FROM PromotionJpaEntity p WHERE p.tenantId = :tenantId AND p.endDate < :now")
    Page<PromotionJpaEntity> findEnded(String tenantId, Instant now, Pageable pageable);
}
