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

    @Query(value = "SELECT o FROM OrderJpaEntity o LEFT JOIN FETCH o.items WHERE o.tenantId = :tenantId",
           countQuery = "SELECT COUNT(o) FROM OrderJpaEntity o WHERE o.tenantId = :tenantId")
    Page<OrderJpaEntity> findAllWithItemsByTenantId(@Param("tenantId") String tenantId, Pageable pageable);

    @Query(value = "SELECT o FROM OrderJpaEntity o LEFT JOIN FETCH o.items "
                 + "WHERE o.tenantId = :tenantId AND o.status = :status",
           countQuery = "SELECT COUNT(o) FROM OrderJpaEntity o WHERE o.tenantId = :tenantId AND o.status = :status")
    Page<OrderJpaEntity> findByStatusWithItemsAndTenantId(
            @Param("tenantId") String tenantId, @Param("status") OrderStatus status, Pageable pageable);

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
