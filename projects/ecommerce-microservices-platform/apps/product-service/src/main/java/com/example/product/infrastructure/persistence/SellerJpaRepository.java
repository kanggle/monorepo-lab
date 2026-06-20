package com.example.product.infrastructure.persistence;

import com.example.product.infrastructure.persistence.entity.SellerJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface SellerJpaRepository extends JpaRepository<SellerJpaEntity, SellerJpaEntity.SellerId> {

    @Query("SELECT s FROM SellerJpaEntity s WHERE s.tenantId = :tenantId AND s.sellerId = :sellerId")
    Optional<SellerJpaEntity> findByTenantIdAndSellerId(@Param("tenantId") String tenantId,
                                                        @Param("sellerId") String sellerId);

    /**
     * Looks up a seller by its backing IAM {@code account_id} within one tenant
     * (ADR-MONO-042 D4-C, TASK-BE-421 — reverse {@code account.status.changed} projection).
     */
    @Query("SELECT s FROM SellerJpaEntity s WHERE s.tenantId = :tenantId AND s.accountId = :accountId")
    Optional<SellerJpaEntity> findByTenantIdAndAccountId(@Param("tenantId") String tenantId,
                                                        @Param("accountId") String accountId);

    /** Paged sellers within one tenant — the operator list read (M6-scoped). */
    @Query("SELECT s FROM SellerJpaEntity s WHERE s.tenantId = :tenantId")
    Page<SellerJpaEntity> findByTenantId(@Param("tenantId") String tenantId, Pageable pageable);

    @Query("SELECT COUNT(s) > 0 FROM SellerJpaEntity s WHERE s.tenantId = :tenantId AND s.sellerId = :sellerId")
    boolean existsByTenantIdAndSellerId(@Param("tenantId") String tenantId,
                                        @Param("sellerId") String sellerId);
}
