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

    // Placement-idempotency lookup (TASK-BE-430): the original order for a
    // re-submit/retry, keyed by the (tenant, user, client Idempotency-Key) the
    // unique index enforces.
    Optional<OrderJpaEntity> findByTenantIdAndUserIdAndIdempotencyKey(
            String tenantId, String userId, String idempotencyKey);

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

    // GDPR PII-anonymization cascade (ADR-037 P3-B): every order for the subject,
    // any status, any tenant. Keyed by the globally-unique user_id (= OIDC sub =
    // IAM accountId) → cannot reach another subject's rows, cannot leak across
    // tenants (only mutates the subject's own orders; tenant_id stays immutable).
    List<OrderJpaEntity> findByUserId(String userId);

    // ---- operational sweeps — two-step id-then-fetch (TASK-BE-439) ----------------
    // Both sweeps map the loaded rows to the domain Order OUTSIDE the request thread
    // (the @Scheduled detector / the internal endpoint's non-tx service), and
    // OrderJpaMapper.toDomain reads the LAZY OrderJpaEntity.items. To guarantee items
    // is materialised before the session closes — independent of any caller tx boundary —
    // each sweep runs as TWO queries:
    //   (1) a paged/ordered SELECT of the matching order ids (a scalar projection, so no
    //       collection-fetch + Pageable in-memory-pagination warning, HHH90003004), then
    //   (2) a LEFT JOIN FETCH of the full entities + items for those ids (no Pageable, so
    //       the join fetch is applied in SQL with no warning).
    // The impl re-orders the step-2 result to the step-1 (createdAt ASC) order. Both are
    // tenant-agnostic operational sweeps keyed by globally-unique ids; the per-order TX
    // re-loads + re-checks before any mutation, preserving each row's immutable tenant_id.

    @Query("SELECT o.orderId FROM OrderJpaEntity o " +
           "WHERE o.status = :status AND o.paymentId IS NULL AND o.createdAt < :placedBefore " +
           "ORDER BY o.createdAt ASC")
    List<String> findStuckPaymentPendingIds(
            @Param("status") OrderStatus status,
            @Param("placedBefore") Instant placedBefore,
            Pageable pageable);

    // Paid-but-unconfirmed bucket (TASK-BE-412): the order paid (payment_id IS NOT NULL)
    // but its confirmation event was lost, so it sits in PENDING past the cutoff. Disjoint
    // from findStuckPaymentPending (payment_id IS NULL) — mutually exclusive on payment_id.
    @Query("SELECT o.orderId FROM OrderJpaEntity o " +
           "WHERE o.status = :status AND o.paymentId IS NOT NULL AND o.createdAt < :cutoff " +
           "ORDER BY o.createdAt ASC")
    List<String> findStalePaidUnconfirmedIds(
            @Param("status") OrderStatus status,
            @Param("cutoff") Instant cutoff,
            Pageable pageable);

    // Step 2 (shared): load the full entities + their items for the swept ids. The
    // LEFT JOIN FETCH initialises items IN the query, so OrderJpaMapper.toDomain never
    // touches a lazy proxy regardless of the caller's session/tx state. No Pageable here
    // (the id set is already capped by step 1), so no in-memory-pagination warning.
    @Query("SELECT DISTINCT o FROM OrderJpaEntity o LEFT JOIN FETCH o.items "
         + "WHERE o.orderId IN :orderIds")
    List<OrderJpaEntity> findAllWithItemsByOrderIdIn(@Param("orderIds") List<String> orderIds);
}
