package com.example.product.infrastructure.persistence;

import com.example.product.application.dto.ProductListResult;
import com.example.product.application.dto.ProductSummary;
import com.example.product.application.port.ProductQueryPort;
import com.example.product.domain.model.Product;
import com.example.product.domain.model.ProductStatus;
import com.example.product.domain.repository.ProductRepository;
import com.example.product.domain.seller.SellerScopeContext;
import com.example.product.domain.tenant.TenantContext;
import com.example.product.infrastructure.persistence.entity.ProductJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;


@Repository
class ProductRepositoryImpl implements ProductRepository, ProductQueryPort {

    private final ProductJpaRepository jpaRepository;

    ProductRepositoryImpl(ProductJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Product save(Product product) {
        String tenantId = TenantContext.currentTenant();
        if (product.isNew()) {
            jpaRepository.save(ProductJpaEntity.from(product, tenantId));
        } else {
            // Tenant-scoped load for update: an update targeting another tenant's
            // product resolves to empty (cross-tenant write cannot reach the row).
            // The seller scope is NOT applied here — seller-scope narrowing is a
            // READ-path concern only (AC-4); writes stay tenant-scoped.
            ProductJpaEntity entity = jpaRepository
                    .findWithVariantsById(product.getId(), tenantId, false, null)
                    .orElseThrow(() -> new IllegalStateException("Product not found: " + product.getId()));
            entity.update(product);
            jpaRepository.save(entity);
        }
        return product;
    }

    @Override
    public Optional<Product> findById(UUID id) {
        // Read path: tenant filter + (when bound) seller-scope filter, always
        // nested after the tenant filter (isolate-then-attribute, AC-6). Absent /
        // '*' scope = unrestricted full-tenant view (net-zero / fail-OPEN, F1).
        return jpaRepository.findWithVariantsById(
                        id, TenantContext.currentTenant(),
                        SellerScopeContext.isRestricted(), SellerScopeContext.currentSellerScope())
                .map(ProductJpaEntity::toDomain);
    }

    @Override
    public boolean existsById(UUID id) {
        return jpaRepository.existsActiveById(id, TenantContext.currentTenant());
    }

    @Override
    public void softDelete(UUID productId) {
        jpaRepository.softDeleteById(productId, TenantContext.currentTenant(), Instant.now());
    }

    @Override
    public long countByTenant() {
        return jpaRepository.countByTenantId(TenantContext.currentTenant());
    }

    @Override
    public long countByTenantCreatedBetween(Instant from, Instant to) {
        return jpaRepository.countByTenantIdAndCreatedAtBetween(TenantContext.currentTenant(), from, to);
    }

    @Override
    public ProductListResult findSummaries(UUID categoryId, ProductStatus status, String name, int page, int size) {
        // Tenant filter + optional case-insensitive name match + (when bound)
        // net-zero seller-scope filter (AC-6, F1).
        Page<ProductSummary> result = jpaRepository
                .findByFilters(TenantContext.currentTenant(), categoryId, status, name,
                        SellerScopeContext.isRestricted(), SellerScopeContext.currentSellerScope(),
                        PageRequest.of(page, size))
                .map(entity -> new ProductSummary(
                        entity.getId(),
                        entity.getName(),
                        entity.getStatus(),
                        entity.getPrice(),
                        entity.getThumbnailUrl(),
                        entity.getCategoryId(),
                        entity.getSellerId()));

        return new ProductListResult(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements());
    }
}
