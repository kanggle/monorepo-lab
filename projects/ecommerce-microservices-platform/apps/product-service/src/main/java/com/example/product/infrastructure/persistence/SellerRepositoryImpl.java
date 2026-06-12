package com.example.product.infrastructure.persistence;

import com.example.product.domain.model.Seller;
import com.example.product.domain.repository.SellerRepository;
import com.example.product.domain.tenant.TenantContext;
import com.example.product.infrastructure.persistence.entity.SellerJpaEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
class SellerRepositoryImpl implements SellerRepository {

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
    @Transactional(readOnly = true)
    public Optional<Seller> findById(String sellerId) {
        return jpaRepository.findByTenantIdAndSellerId(TenantContext.currentTenant(), sellerId)
                .map(SellerJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(String sellerId) {
        return jpaRepository.existsByTenantIdAndSellerId(TenantContext.currentTenant(), sellerId);
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
