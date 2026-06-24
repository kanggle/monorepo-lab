package com.example.order.domain.repository;

import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderStatus;
import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    Order save(Order order);

    List<Order> saveAll(List<Order> orders);

    /**
     * Tenant-scoped lookup (HTTP read path). Resolves to empty for an order owned
     * by another tenant — the caller surfaces that as 404 (M3, existence hidden).
     */
    Optional<Order> findById(String orderId);

    /**
     * Tenant-scoped lookup of an order by the client-supplied placement idempotency
     * key (TASK-BE-430). Returns the original order for a re-submit/retry of the
     * same checkout so placement is idempotent. Scoped to the request tenant +
     * {@code userId}; empty when no order carries that key.
     */
    Optional<Order> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);

    /**
     * Tenant-scoped admin lookup (OPERATOR detail path) with a nested net-zero
     * seller-scope filter: when a concrete seller scope is bound, the order is
     * visible only if it has at least one line attributed to that seller; absent /
     * {@code '*'} scope returns the full tenant view (fail-OPEN, F1). A cross-seller
     * id resolves to empty → 404 (M3). The seller filter is always nested inside the
     * tenant filter (AC-6).
     */
    Optional<Order> findByIdForAdmin(String orderId);

    /**
     * Tenant-agnostic lookup by globally-unique order id, for the system/saga path
     * (consumed payment/stock/withdrawal/wms events, stuck-detector recovery).
     * Addressing by unique id cannot reach the wrong order, so it cannot leak
     * across tenants; the order's immutable {@code tenant_id} is preserved across
     * any subsequent save (ADR-MONO-030 Step 2, task §C).
     */
    Optional<Order> findByIdAcrossTenants(String orderId);

    /**
     * Tenant-agnostic resolution of the {@code tenant_id} stored on a globally-unique
     * order, for the cross-project wms return-leg fallback (ADR-MONO-022 facet d,
     * TASK-MONO-296): when the wms {@code outbound.order.cancelled} envelope carries
     * no {@code tenantId} (older wms / standalone), the consumer resolves the
     * originating tenant from the local Order row by {@code orderId == orderNo} before
     * binding {@code TenantContext}. Addressing by the unique id cannot leak across
     * tenants. Empty when no such order exists (then the default tenant applies, D8).
     */
    Optional<String> findTenantIdByOrderId(String orderId);

    PageResult<Order> findByUserId(String userId, PageQuery pageQuery);

    PageResult<Order> findByUserIdAndStatus(String userId, OrderStatus status, PageQuery pageQuery);

    List<Order> findByUserIdAndStatusIn(String userId, Collection<OrderStatus> statuses);

    /**
     * Tenant-agnostic, status-agnostic lookup of EVERY order belonging to a subject,
     * for the GDPR PII-anonymization cascade (ADR-MONO-037 P3-B). Masking is
     * retention-wide — it must reach historical orders in any status (DELIVERED,
     * CANCELLED, …) and across every tenant the subject ordered under. Addressing by
     * the globally-unique {@code user_id} (= OIDC {@code sub} = IAM {@code accountId})
     * cannot leak: it only ever returns that subject's own rows, and each order's
     * immutable {@code tenant_id} is preserved across the subsequent save.
     */
    List<Order> findAllByUserIdAcrossTenants(String userId);

    boolean existsByUserIdAndProductIdAndStatus(String userId, String productId, OrderStatus status);

    PageResult<Order> findAll(PageQuery pageQuery);

    PageResult<Order> findByStatus(OrderStatus status, PageQuery pageQuery);

    PageResult<Order> findAllWithItems(PageQuery pageQuery);

    PageResult<Order> findByStatusWithItems(OrderStatus status, PageQuery pageQuery);

    /**
     * Returns orders stuck in {@code PENDING} with no payment recorded whose
     * {@code created_at} predates {@code placedBefore}. Used by the saga
     * stuck-detector (TASK-BE-138) to identify choreographed-saga rows that
     * never received a {@code PaymentCompleted} event.
     */
    List<Order> findStuckPaymentPending(Instant placedBefore, int batchSize);

    /**
     * Returns orders that paid ({@code payment_id IS NOT NULL}) but never received
     * their confirmation event — still {@code PENDING} with {@code created_at} predating
     * {@code cutoff}, oldest first, capped at {@code limit}. Used by the internal
     * stale-paid forward-confirm sweep (TASK-BE-412).
     *
     * <p>Disjoint from {@link #findStuckPaymentPending(Instant, int)} on {@code payment_id}:
     * that bucket is {@code payment_id IS NULL} (payment never completed, BE-138); this
     * bucket is {@code payment_id IS NOT NULL} (paid, confirmation event lost). No order is
     * ever a candidate for both.
     */
    List<Order> findStalePaidUnconfirmed(Instant cutoff, int limit);
}
