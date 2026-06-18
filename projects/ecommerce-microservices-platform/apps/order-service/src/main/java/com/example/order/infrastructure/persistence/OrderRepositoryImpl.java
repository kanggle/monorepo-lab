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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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

    @Override
    public Optional<Order> findById(String orderId) {
        // Tenant-scoped (HTTP read path): a cross-tenant id resolves to empty → 404
        // (M3, existence hidden). The system/saga path uses findByIdAcrossTenants.
        return jpaRepository.findByOrderIdAndTenantId(orderId, TenantContext.currentTenant())
                .map(mapper::toDomain);
    }

    @Override
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
    public List<Order> findByUserIdAndStatusIn(String userId, Collection<OrderStatus> statuses) {
        return jpaRepository.findByUserIdAndStatusIn(userId, statuses).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
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

    @Override
    public List<Order> findStuckPaymentPending(Instant placedBefore, int batchSize) {
        return jpaRepository.findStuckPaymentPending(
                        OrderStatus.PENDING, placedBefore, PageRequest.of(0, batchSize))
                .stream()
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
