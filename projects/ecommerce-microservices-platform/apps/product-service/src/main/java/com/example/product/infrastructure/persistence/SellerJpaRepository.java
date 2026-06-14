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

    /** Paged sellers within one tenant — the operator list read (M6-scoped). */
    @Query("SELECT s FROM SellerJpaEntity s WHERE s.tenantId = :tenantId")
    Page<SellerJpaEntity> findByTenantId(@Param("tenantId") String tenantId, Pageable pageable);

    @Query("SELECT COUNT(s) > 0 FROM SellerJpaEntity s WHERE s.tenantId = :tenantId AND s.sellerId = :sellerId")
    boolean existsByTenantIdAndSellerId(@Param("tenantId") String tenantId,
                                        @Param("sellerId") String sellerId);
}
