package com.example.order.infrastructure.persistence;

import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderStatus;
import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.order.domain.repository.OrderRepository;
import com.example.order.domain.seller.SellerScopeContext;
import com.example.order.domain.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository jpaRepository;
    private final OrderJpaMapper mapper;

    @Override
    public Order save(Order order) {
        if (order.getVersion() == null) {
            OrderJpaEntity entity = mapper.toEntity(order);
            OrderJpaEntity saved = jpaRepository.save(entity);
            return mapper.toDomain(saved);
        }

        OrderJpaEntity existing = jpaRepository.findById(order.getOrderId())
                .orElseThrow(() -> new IllegalStateException(
                        "Order not found for update: " + order.getOrderId()));
        existing.updateFrom(order);
        return mapper.toDomain(existing);
    }

    @Override
    public List<Order> saveAll(List<Order> orders) {
        Map<String, OrderJpaEntity> existingMap = loadExistingEntities(orders);
        List<OrderJpaEntity> entities = toEntities(orders, existingMap);
        List<OrderJpaEntity> savedEntities = jpaRepository.saveAll(entities);
        return savedEntities.stream()
                .map(mapper::toDomain)
                .toList();
    }

    private Map<String, OrderJpaEntity> loadExistingEntities(List<Order> orders) {
        List<String> existingIds = orders.stream()
                .filter(o -> o.getVersion() != null)
                .map(Order::getOrderId)
                .toList();
        if (existingIds.isEmpty()) {
            return Map.of();
        }
        return jpaRepository.findAllById(existingIds).stream()
                .collect(Collectors.toMap(OrderJpaEntity::getOrderId, e -> e));
    }

    private List<OrderJpaEntity> toEntities(List<Order> orders, Map<String, OrderJpaEntity> existingMap) {
        List<OrderJpaEntity> entities = new ArrayList<>(orders.size());
        for (Order order : orders) {
            if (order.getVersion() == null) {
                entities.add(mapper.toEntity(order));
            } else {
                OrderJpaEntity existing = existingMap.get(order.getOrderId());
                if (existing == null) {
                    throw new IllegalStateException(
                            "Order not found for update: " + order.getOrderId());
                }
                existing.updateFrom(order);
                entities.add(existing);
            }
        }
        return entities;
    }

    // TASK-BE-456 — the by-id reads below each `.map(mapper::toDomain)`, and
    // OrderJpaMapper.toDomain eagerly reads the LAZY OrderJpaEntity.items. In a
    // request these run inside the service-layer @Transactional so the session
    // stays open; but called outside a tx (the order-service integration tests
    // invoke orderRepository.findById directly — OrderPlacementIT /
    // OrderOptimisticLockIT / OrderEventPublishIT) the entity is detached and
    // items access throws LazyInitializationException (the same TASK-BE-439
    // detached-mapping hazard the operational sweeps hit). A single-row by-id
    // read has no Pageable, so the simple, uniform fix is
    // @Transactional(readOnly = true) spanning the query + toDomain (propagation
    // REQUIRED joins the request tx when present; opens a read session when
    // called standalone) — no fetch-join / HHH90003004 in-memory-pagination
    // concern. Defense-in-depth: the production request paths were already safe;
    // this makes the reads detach-safe regardless of the caller's session state.

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(String orderId) {
        // Tenant-scoped (HTTP read path): a cross-tenant id resolves to empty → 404
        // (M3, existence hidden). The system/saga path uses findByIdAcrossTenants.
        return jpaRepository.findByOrderIdAndTenantId(orderId, TenantContext.currentTenant())
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey) {
        // Placement-idempotency replay lookup (TASK-BE-430): tenant-scoped to the
        // request tenant, matching the (tenant_id, user_id, idempotency_key) unique
        // index. A null/blank key never reaches here (the service guards it).
        return jpaRepository.findByTenantIdAndUserIdAndIdempotencyKey(
                        TenantContext.currentTenant(), userId, idempotencyKey)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findByIdForAdmin(String orderId) {
        // OPERATOR detail read: tenant filter + (when bound) net-zero seller-scope
        // filter, always nested after the tenant filter (AC-6). Absent / '*' scope =
        // full tenant view (F1). A cross-seller id resolves to empty → 404 (M3).
        return jpaRepository.findByOrderIdAndTenantIdForAdmin(
                        orderId, TenantContext.currentTenant(),
                        SellerScopeContext.isRestricted(), SellerScopeContext.currentSellerScope())
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findByIdAcrossTenants(String orderId) {
        // System/saga/sweep path: the order is addressed by its globally-unique id
        // (a consumed payment/stock/withdrawal/wms event, or the stuck-detector
        // recovery), so the lookup is tenant-agnostic — it can never reach the
        // wrong order (the id is unique) and must find the order regardless of the
        // ambient context. Downstream state stays on the loaded order's tenant
        // because tenant_id is immutable after insert (preserved on update).
        return jpaRepository.findById(orderId).map(mapper::toDomain);
    }

    @Override
    public Optional<String> findTenantIdByOrderId(String orderId) {
        // wms return-leg fallback (ADR-MONO-022 facet d): tenant-agnostic — addressing
        // by the unique order id cannot leak across tenants; returns the row's stored
        // tenant so the consumer can bind it before mutating + emitting order.cancelled.
        return jpaRepository.findById(orderId).map(OrderJpaEntity::getTenantId);
    }

    @Override
    public PageResult<Order> findByUserId(String userId, PageQuery pageQuery) {
        PageRequest pageable = toPageRequest(pageQuery);
        Page<OrderJpaEntity> page = jpaRepository.findByTenantIdAndUserId(
                TenantContext.currentTenant(), userId, pageable);
        return toPageResult(page);
    }

    @Override
    public PageResult<Order> findByUserIdAndStatus(String userId, OrderStatus status, PageQuery pageQuery) {
        PageRequest pageable = toPageRequest(pageQuery);
        Page<OrderJpaEntity> page = jpaRepository.findByTenantIdAndUserIdAndStatus(
                TenantContext.currentTenant(), userId, status, pageable);
        return toPageResult(page);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByUserIdAndStatusIn(String userId, Collection<OrderStatus> statuses) {
        return jpaRepository.findByUserIdAndStatusIn(userId, statuses).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findAllByUserIdAcrossTenants(String userId) {
        // GDPR PII-anonymization cascade (ADR-MONO-037 P3-B): every order for the
        // subject, any status, any tenant. Keyed by the globally-unique user_id, so
        // it can never reach another subject and never leaks across tenants — it only
        // ever returns the subject's own rows, and tenant_id is preserved on save.
        return jpaRepository.findByUserId(userId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public PageResult<Order> findAll(PageQuery pageQuery) {
        PageRequest pageable = toPageRequest(pageQuery);
        Page<OrderJpaEntity> page = jpaRepository.findByTenantId(TenantContext.currentTenant(), pageable);
        return toPageResult(page);
    }

    @Override
    public PageResult<Order> findByStatus(OrderStatus status, PageQuery pageQuery) {
        PageRequest pageable = toPageRequest(pageQuery);
        Page<OrderJpaEntity> page = jpaRepository.findByTenantIdAndStatus(
                TenantContext.currentTenant(), status, pageable);
        return toPageResult(page);
    }

    @Override
    public PageResult<Order> findAllWithItems(PageQuery pageQuery) {
        // OPERATOR list read: tenant filter + nested net-zero seller-scope filter (AC-6, F1).
        PageRequest pageable = toPageRequest(pageQuery);
        Page<OrderJpaEntity> page = jpaRepository.findAllWithItemsByTenantId(
                TenantContext.currentTenant(),
                SellerScopeContext.isRestricted(), SellerScopeContext.currentSellerScope(),
                pageable);
        return toPageResult(page);
    }

    @Override
    public PageResult<Order> findByStatusWithItems(OrderStatus status, PageQuery pageQuery) {
        PageRequest pageable = toPageRequest(pageQuery);
        Page<OrderJpaEntity> page = jpaRepository.findByStatusWithItemsAndTenantId(
                TenantContext.currentTenant(), status,
                SellerScopeContext.isRestricted(), SellerScopeContext.currentSellerScope(),
                pageable);
        return toPageResult(page);
    }

    @Override
    public boolean existsByUserIdAndProductIdAndStatus(String userId, String productId, OrderStatus status) {
        return jpaRepository.existsByUserIdAndProductIdAndStatusAndTenantId(
                TenantContext.currentTenant(), userId, productId, status);
    }

    /**
     * Reads stuck PENDING orders (payment_id IS NULL) for the saga sweeper
     * (TASK-BE-138). TASK-BE-439: the mapping to the domain {@code Order} runs
     * outside the request thread (the {@code @Scheduled OrderStuckDetector}), where
     * no session is open, so {@code OrderJpaMapper.toDomain}'s access of the LAZY
     * {@code OrderJpaEntity.items} threw {@code LazyInitializationException} and the
     * sweeper's {@code catch} silently swallowed it (recovering nothing).
     *
     * <p>Fixed with a two-step id-then-fetch: step 1 selects the matching order ids
     * (paged/ordered scalar projection — no collection-fetch + {@code Pageable}
     * in-memory-pagination warning, HHH90003004); step 2 reloads the full entities
     * with their {@code items} via {@code LEFT JOIN FETCH} (no {@code Pageable}). The
     * fetch join initialises {@code items} IN the query, so the subsequent
     * {@code toDomain} never touches a lazy proxy — correct regardless of the
     * caller's session/tx state. {@code @Transactional(readOnly = true)} spans the two
     * queries in one read session. The per-order recovery runs in its own separate
     * {@code REQUIRES_NEW} TX afterward — no contention.
     */
    @Override
    @Transactional(readOnly = true)
    public List<Order> findStuckPaymentPending(Instant placedBefore, int batchSize) {
        List<String> ids = jpaRepository.findStuckPaymentPendingIds(
                OrderStatus.PENDING, placedBefore, PageRequest.of(0, batchSize));
        return loadOrderedWithItems(ids);
    }

    /**
     * Reads paid-but-unconfirmed PENDING orders for the stale-paid confirm sweep
     * (TASK-BE-412). Same TASK-BE-439 detached-lazy hazard and same two-step
     * id-then-fetch + {@code LEFT JOIN FETCH} fix as {@link #findStuckPaymentPending}:
     * the mapping runs in the internal endpoint's non-transactional
     * {@code StalePaidOrderConfirmService.sweep}, so {@code items} must be initialised
     * inside the query. Each order's per-order confirm runs in its own
     * {@code REQUIRES_NEW} TX ({@code StalePaidOrderConfirmHandler.confirmIfStillPending})
     * afterward.
     */
    @Override
    @Transactional(readOnly = true)
    public List<Order> findStalePaidUnconfirmed(Instant cutoff, int limit) {
        List<String> ids = jpaRepository.findStalePaidUnconfirmedIds(
                OrderStatus.PENDING, cutoff, PageRequest.of(0, limit));
        return loadOrderedWithItems(ids);
    }

    /**
     * Step 2 of the operational sweeps (TASK-BE-439): reload the full entities +
     * items for the swept ids via {@code LEFT JOIN FETCH}, then map to the domain
     * preserving the step-1 {@code created_at ASC} order. The {@code IN}-fetch may
     * return rows in any order, so we re-order by the id list. {@code items} is
     * eagerly initialised by the fetch join, so {@code toDomain} is detach-safe.
     */
    private List<Order> loadOrderedWithItems(List<String> orderedIds) {
        if (orderedIds.isEmpty()) {
            return List.of();
        }
        Map<String, OrderJpaEntity> byId = jpaRepository.findAllWithItemsByOrderIdIn(orderedIds).stream()
                .collect(Collectors.toMap(OrderJpaEntity::getOrderId, e -> e));
        return orderedIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(mapper::toDomain)
                .toList();
    }

    private PageRequest toPageRequest(PageQuery pageQuery) {
        Sort.Direction direction = "ASC".equalsIgnoreCase(pageQuery.sortDirection())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return PageRequest.of(pageQuery.page(), pageQuery.size(), Sort.by(direction, pageQuery.sortBy()));
    }

    private PageResult<Order> toPageResult(Page<OrderJpaEntity> page) {
        List<Order> content = page.getContent().stream()
                .map(mapper::toDomain)
                .toList();
        return new PageResult<>(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
