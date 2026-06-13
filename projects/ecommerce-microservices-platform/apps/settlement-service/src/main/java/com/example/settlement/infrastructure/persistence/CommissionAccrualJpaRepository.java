package com.example.settlement.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommissionAccrualJpaRepository extends JpaRepository<CommissionAccrualJpaEntity, String> {

    boolean existsByOrderIdAndPaymentIdAndType(String orderId, String paymentId,
                                               com.example.settlement.domain.model.AccrualType type);

    /** ACCRUAL rows of an order (tenant-agnostic — addressed by globally-unique orderId, the consume path). */
    List<CommissionAccrualJpaEntity> findByOrderIdAndType(String orderId,
                                                          com.example.settlement.domain.model.AccrualType type);

    /**
     * Operator-plane accrual listing. Tenant filter is always applied; the seller
     * filter is a single net-zero branch ({@code :sellerRestricted = false OR
     * seller_id = :sellerScope}) — isolate-then-attribute (AC-8). Optional
     * {@code sellerIdFilter} / {@code orderIdFilter} narrow further (null = ignore).
     */
    @Query("""
            SELECT a FROM CommissionAccrualJpaEntity a
            WHERE a.tenantId = :tenantId
              AND (:sellerRestricted = false OR a.sellerId = :sellerScope)
              AND (:sellerIdFilter IS NULL OR a.sellerId = :sellerIdFilter)
              AND (:orderIdFilter IS NULL OR a.orderId = :orderIdFilter)
            """)
    Page<CommissionAccrualJpaEntity> findAccruals(@Param("tenantId") String tenantId,
                                                  @Param("sellerRestricted") boolean sellerRestricted,
                                                  @Param("sellerScope") String sellerScope,
                                                  @Param("sellerIdFilter") String sellerIdFilter,
                                                  @Param("orderIdFilter") String orderIdFilter,
                                                  Pageable pageable);

    /** Aggregated balance for one seller within a tenant + seller scope. */
    @Query("""
            SELECT new com.example.settlement.infrastructure.persistence.SellerBalanceProjection(
                COALESCE(SUM(a.sellerNetMinor), 0L),
                COALESCE(SUM(a.commissionMinor), 0L),
                COALESCE(SUM(a.grossMinor), 0L),
                COUNT(a))
            FROM CommissionAccrualJpaEntity a
            WHERE a.tenantId = :tenantId
              AND a.sellerId = :sellerId
              AND (:sellerRestricted = false OR a.sellerId = :sellerScope)
            """)
    SellerBalanceProjection aggregateSellerBalance(@Param("tenantId") String tenantId,
                                                   @Param("sellerId") String sellerId,
                                                   @Param("sellerRestricted") boolean sellerRestricted,
                                                   @Param("sellerScope") String sellerScope);
}
