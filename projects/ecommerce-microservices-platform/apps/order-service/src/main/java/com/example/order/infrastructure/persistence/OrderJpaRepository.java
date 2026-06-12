package com.example.order.infrastructure.persistence;

import com.example.order.domain.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, String> {

    // ---- tenant-scoped reads (HTTP request path — M2 layer 3, M3 404) ----------
    // Every user/admin-facing read filters by the request tenant; a cross-tenant
    // id resolves to empty → 404 (existence hidden). The inherited findById(id)
    // (by PK, tenant-agnostic) is reserved for the system/saga path via
    // OrderRepositoryImpl#findByIdAcrossTenants and for the save() merge load.

    Optional<OrderJpaEntity> findByOrderIdAndTenantId(String orderId, String tenantId);

    Page<OrderJpaEntity> findByTenantIdAndUserId(String tenantId, String userId, Pageable pageable);

    Page<OrderJpaEntity> findByTenantIdAndUserIdAndStatus(
            String tenantId, String userId, OrderStatus status, Pageable pageable);

    Page<OrderJpaEntity> findByTenantId(String tenantId, Pageable pageable);

    Page<OrderJpaEntity> findByTenantIdAndStatus(String tenantId, OrderStatus status, Pageable pageable);

    // ---- OPERATOR (admin) reads — tenant filter + nested net-zero seller scope ---
    // The seller axis is attribution, not isolation: an order is restricted to a
    // seller-scoped operator when it has AT LEAST ONE line attributed to that seller
    // (a multi-seller order is visible to each of its sellers). The predicate is
    // net-zero / fail-OPEN (ADR-025 F1): `:sellerRestricted = false` collapses it to
    // the full tenant view. The seller filter is ALWAYS nested inside the tenant
    // filter (isolate-then-attribute, AC-6) — it can never reach another tenant.
    // 404-over-403 (M3): a cross-seller detail read resolves to empty → 404.

    @Query(value = "SELECT DISTINCT o FROM OrderJpaEntity o LEFT JOIN FETCH o.items "
                 + "WHERE o.tenantId = :tenantId "
                 + "AND (:sellerRestricted = false OR EXISTS ("
                 + "  SELECT 1 FROM OrderItemJpaEntity i WHERE i.order = o AND i.sellerId = :sellerScope))",
           countQuery = "SELECT COUNT(o) FROM OrderJpaEntity o "
                 + "WHERE o.tenantId = :tenantId "
                 + "AND (:sellerRestricted = false OR EXISTS ("
                 + "  SELECT 1 FROM OrderItemJpaEntity i WHERE i.order = o AND i.sellerId = :sellerScope))")
    Page<OrderJpaEntity> findAllWithItemsByTenantId(@Param("tenantId") String tenantId,
                                                    @Param("sellerRestricted") boolean sellerRestricted,
                                                    @Param("sellerScope") String sellerScope,
                                                    Pageable pageable);

    @Query(value = "SELECT DISTINCT o FROM OrderJpaEntity o LEFT JOIN FETCH o.items "
                 + "WHERE o.tenantId = :tenantId AND o.status = :status "
                 + "AND (:sellerRestricted = false OR EXISTS ("
                 + "  SELECT 1 FROM OrderItemJpaEntity i WHERE i.order = o AND i.sellerId = :sellerScope))",
           countQuery = "SELECT COUNT(o) FROM OrderJpaEntity o "
                 + "WHERE o.tenantId = :tenantId AND o.status = :status "
                 + "AND (:sellerRestricted = false OR EXISTS ("
                 + "  SELECT 1 FROM OrderItemJpaEntity i WHERE i.order = o AND i.sellerId = :sellerScope))")
    Page<OrderJpaEntity> findByStatusWithItemsAndTenantId(
            @Param("tenantId") String tenantId, @Param("status") OrderStatus status,
            @Param("sellerRestricted") boolean sellerRestricted, @Param("sellerScope") String sellerScope,
            Pageable pageable);

    @Query("SELECT DISTINCT o FROM OrderJpaEntity o LEFT JOIN FETCH o.items "
         + "WHERE o.orderId = :orderId AND o.tenantId = :tenantId "
         + "AND (:sellerRestricted = false OR EXISTS ("
         + "  SELECT 1 FROM OrderItemJpaEntity i WHERE i.order = o AND i.sellerId = :sellerScope))")
    Optional<OrderJpaEntity> findByOrderIdAndTenantIdForAdmin(
            @Param("orderId") String orderId, @Param("tenantId") String tenantId,
            @Param("sellerRestricted") boolean sellerRestricted, @Param("sellerScope") String sellerScope);

    @Query("SELECT COUNT(o) > 0 FROM OrderJpaEntity o JOIN o.items i " +
           "WHERE o.tenantId = :tenantId AND o.userId = :userId AND i.productId = :productId AND o.status = :status")
    boolean existsByUserIdAndProductIdAndStatusAndTenantId(
            @Param("tenantId") String tenantId,
            @Param("userId") String userId,
            @Param("productId") String productId,
            @Param("status") OrderStatus status);

    // ---- tenant-agnostic reads (system / saga / sweep — keyed by globally-unique
    // ---- id or operational scope; can never leak across tenants) ----------------

    List<OrderJpaEntity> findByUserIdAndStatusIn(String userId, Collection<OrderStatus> statuses);

    @Query("SELECT o FROM OrderJpaEntity o " +
           "WHERE o.status = :status AND o.paymentId IS NULL AND o.createdAt < :placedBefore " +
           "ORDER BY o.createdAt ASC")
    List<OrderJpaEntity> findStuckPaymentPending(
            @Param("status") OrderStatus status,
            @Param("placedBefore") Instant placedBefore,
            Pageable pageable);
}
