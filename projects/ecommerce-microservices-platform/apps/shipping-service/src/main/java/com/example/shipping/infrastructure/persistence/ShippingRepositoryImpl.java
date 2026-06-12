package com.example.shipping.infrastructure.persistence;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.shipping.domain.model.Shipping;
import com.example.shipping.domain.model.ShippingStatus;
import com.example.shipping.domain.model.StatusHistoryEntry;
import com.example.shipping.domain.repository.ShippingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

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
                    shipping.getCarrier(), shipping.getUpdatedAt());

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
        return jpaRepository.findById(shippingId).map(mapper::toDomain);
    }

    @Override
    public Optional<Shipping> findByOrderId(String orderId) {
        return jpaRepository.findByOrderId(orderId).map(mapper::toDomain);
    }

    @Override
    public boolean existsByOrderId(String orderId) {
        return jpaRepository.existsByOrderId(orderId);
    }

    @Override
    public PageResult<Shipping> findAll(PageQuery pageQuery) {
        PageRequest pageable = toPageRequest(pageQuery);
        Page<ShippingJpaEntity> page = jpaRepository.findAll(pageable);
        return toPageResult(page);
    }

    @Override
    public PageResult<Shipping> findByStatus(ShippingStatus status, PageQuery pageQuery) {
        PageRequest pageable = toPageRequest(pageQuery);
        Page<ShippingJpaEntity> page = jpaRepository.findByStatus(status, pageable);
        return toPageResult(page);
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
