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

    PageResult<Order> findByUserId(String userId, PageQuery pageQuery);

    PageResult<Order> findByUserIdAndStatus(String userId, OrderStatus status, PageQuery pageQuery);

    List<Order> findByUserIdAndStatusIn(String userId, Collection<OrderStatus> statuses);

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
}
