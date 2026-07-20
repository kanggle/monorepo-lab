package com.example.product.infrastructure.persistence;

import com.example.product.domain.model.ProductCreateRequest;
import com.example.product.domain.repository.ProductCreateRequestRepository;
import com.example.product.infrastructure.persistence.entity.ProductCreateRequestJpaEntity;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
class ProductCreateRequestRepositoryImpl implements ProductCreateRequestRepository {

    private final ProductCreateRequestJpaRepository jpaRepository;

    ProductCreateRequestRepositoryImpl(ProductCreateRequestJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<ProductCreateRequest> find(String tenantId, String idempotencyKey) {
        return jpaRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey)
                .map(ProductCreateRequestJpaEntity::toDomain);
    }

    @Override
    public ProductCreateRequest insert(ProductCreateRequest request) {
        // saveAndFlush: the INSERT must reach Postgres inside the caller's try-block
        // so a UNIQUE (tenant_id, idempotency_key) violation arrives as a
        // DataIntegrityViolationException it can translate to 409, before the
        // product itself is persisted.
        return jpaRepository.saveAndFlush(ProductCreateRequestJpaEntity.fromDomain(request))
                .toDomain();
    }
}
