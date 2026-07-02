package com.example.shipping.infrastructure.persistence;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.shipping.domain.model.Shipping;
import com.example.shipping.domain.model.ShippingStatus;
import com.example.shipping.domain.model.StatusHistoryEntry;
import com.example.shipping.domain.repository.ShippingRepository;
import com.example.shipping.domain.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class ShippingRepositoryImpl implements ShippingRepository {

    private final ShippingJpaRepository jpaRepository;
    private final ShippingJpaMapper mapper;

    @Override
    public Shipping save(Shipping shipping) {
        Optional<ShippingJpaEntity> existingOpt = jpaRepository.findById(shipping.getShippingId());

        if (existingOpt.isPresent()) {
            ShippingJpaEntity existing = existingOpt.get();
            existing.updateFrom(shipping.getStatus(), shipping.getTrackingNumber(),
                    shipping.getCarrier(), shipping.isWmsRouted(), shipping.getUpdatedAt());

            // Add new status history entries
            int existingHistorySize = existing.getStatusHistory().size();
            List<StatusHistoryEntry> domainHistory = shipping.getStatusHistory();
            for (int i = existingHistorySize; i < domainHistory.size(); i++) {
                StatusHistoryEntry entry = domainHistory.get(i);
                StatusHistoryJpaEntity historyEntity = StatusHistoryJpaEntity.create(entry.status(), entry.changedAt());
                existing.addStatusHistory(historyEntity);
            }
            return mapper.toDomain(existing);
        }

        ShippingJpaEntity entity = mapper.toEntity(shipping);
        ShippingJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Shipping> findById(String shippingId) {
        // Tenant-AGNOSTIC (system carrier-webhook path): globally-unique shippingId, no
        // request tenant context; the located row keeps its own tenant (M3 system-path).
        return jpaRepository.findById(shippingId).map(mapper::toDomain);
    }

    @Override
    public Optional<Shipping> findByIdForTenant(String shippingId) {
        // Tenant-SCOPED (admin/operator mutation lookup): a cross-tenant id → empty → 404
        // (existence hidden, M3 cross-tenant-read-is-not-found).
        return jpaRepository.findByShippingIdAndTenantId(shippingId, TenantContext.currentTenant())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Shipping> findByOrderId(String orderId) {
        // Tenant-AGNOSTIC (consumer read + wms-confirm return leg): globally-unique
        // orderId, system flows; the located row keeps its own tenant (M3 system-path).
        return jpaRepository.findByOrderId(orderId).map(mapper::toDomain);
    }

    @Override
    public boolean existsByOrderId(String orderId) {
        // Tenant-AGNOSTIC (createShipping idempotency dedup): must see a pre-existing row
        // in ANY tenant so a re-delivered OrderConfirmed cannot duplicate (M3 system-path).
        return jpaRepository.existsByOrderId(orderId);
    }

    @Override
    public PageResult<Shipping> findAll(PageQuery pageQuery) {
        // Tenant-SCOPED admin list (current request tenant).
        PageRequest pageable = toPageRequest(pageQuery);
        Page<ShippingJpaEntity> page = jpaRepository.findByTenantId(TenantContext.currentTenant(), pageable);
        return toPageResult(page);
    }

    @Override
    public PageResult<Shipping> findByStatus(ShippingStatus status, PageQuery pageQuery) {
        // Tenant-SCOPED admin list filtered by status (current request tenant).
        PageRequest pageable = toPageRequest(pageQuery);
        Page<ShippingJpaEntity> page =
                jpaRepository.findByTenantIdAndStatus(TenantContext.currentTenant(), status, pageable);
        return toPageResult(page);
    }

    @Override
    public long countAll() {
        // Tenant-SCOPED (current request tenant).
        return jpaRepository.countByTenantId(TenantContext.currentTenant());
    }

    @Override
    public long countCreatedBetween(Instant from, Instant to) {
        // Tenant-SCOPED (current request tenant).
        return jpaRepository.countByTenantIdAndCreatedAtBetween(TenantContext.currentTenant(), from, to);
    }

    private static final Set<ShippingStatus> IN_FLIGHT =
            Set.of(ShippingStatus.SHIPPED, ShippingStatus.IN_TRANSIT);

    @Override
    public List<Shipping> findInFlightWithTracking(int limit) {
        PageRequest pageable = PageRequest.of(0, limit);
        return jpaRepository.findInFlightWithTracking(IN_FLIGHT, pageable).stream()
                .map(mapper::toDomain)
                .toList();
    }

    private PageRequest toPageRequest(PageQuery pageQuery) {
        Sort.Direction direction = "ASC".equalsIgnoreCase(pageQuery.sortDirection())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return PageRequest.of(pageQuery.page(), pageQuery.size(), Sort.by(direction, pageQuery.sortBy()));
    }

    private PageResult<Shipping> toPageResult(Page<ShippingJpaEntity> page) {
        List<Shipping> content = page.getContent().stream()
                .map(mapper::toDomain)
                .toList();
        return new PageResult<>(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
