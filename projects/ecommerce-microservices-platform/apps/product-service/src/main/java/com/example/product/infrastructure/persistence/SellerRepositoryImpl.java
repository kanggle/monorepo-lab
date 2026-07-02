package com.example.product.infrastructure.persistence;

import com.example.product.application.dto.SellerListResult;
import com.example.product.application.dto.SellerSummary;
import com.example.product.application.port.SellerQueryPort;
import com.example.product.domain.model.Seller;
import com.example.product.domain.repository.SellerRepository;
import com.example.product.domain.tenant.TenantContext;
import com.example.product.infrastructure.persistence.entity.SellerJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
class SellerRepositoryImpl implements SellerRepository, SellerQueryPort {

    private final SellerJpaRepository jpaRepository;

    SellerRepositoryImpl(SellerJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public Seller save(Seller seller) {
        String tenantId = TenantContext.currentTenant();
        return jpaRepository.findByTenantIdAndSellerId(tenantId, seller.getSellerId())
                .map(SellerJpaEntity::toDomain)
                .orElseGet(() -> jpaRepository.save(SellerJpaEntity.from(seller, tenantId)).toDomain());
    }

    @Override
    @Transactional
    public Seller update(Seller seller) {
        String tenantId = TenantContext.currentTenant();
        SellerJpaEntity entity = jpaRepository.findByTenantIdAndSellerId(tenantId, seller.getSellerId())
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot update a non-existent seller: " + seller.getSellerId()));
        entity.applyLifecycle(seller);
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Seller> findById(String sellerId) {
        return jpaRepository.findByTenantIdAndSellerId(TenantContext.currentTenant(), sellerId)
                .map(SellerJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Seller> findByAccountId(String accountId) {
        return jpaRepository.findByTenantIdAndAccountId(TenantContext.currentTenant(), accountId)
                .map(SellerJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(String sellerId) {
        return jpaRepository.existsByTenantIdAndSellerId(TenantContext.currentTenant(), sellerId);
    }

    @Override
    @Transactional(readOnly = true)
    public SellerListResult findAll(int page, int size) {
        Page<SellerJpaEntity> result = jpaRepository.findByTenantId(
                TenantContext.currentTenant(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new SellerListResult(
                result.getContent().stream()
                        .map(SellerJpaEntity::toDomain)
                        .map(SellerSummary::from)
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public long countByTenant() {
        return jpaRepository.countByTenantId(TenantContext.currentTenant());
    }

    @Override
    @Transactional(readOnly = true)
    public long countByTenantCreatedBetween(Instant from, Instant to) {
        return jpaRepository.countByTenantIdAndCreatedAtBetween(TenantContext.currentTenant(), from, to);
    }

    @Override
    @Transactional
    public Seller ensureDefaultSeller() {
        String tenantId = TenantContext.currentTenant();
        return jpaRepository.findByTenantIdAndSellerId(tenantId, Seller.DEFAULT_SELLER_ID)
                .map(SellerJpaEntity::toDomain)
                .orElseGet(() -> jpaRepository.save(
                        SellerJpaEntity.from(Seller.defaultSeller(), tenantId)).toDomain());
    }
}
