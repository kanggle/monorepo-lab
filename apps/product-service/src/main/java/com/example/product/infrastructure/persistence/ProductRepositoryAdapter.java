package com.example.product.infrastructure.persistence;

import com.example.product.application.dto.ProductListResult;
import com.example.product.application.dto.ProductSummary;
import com.example.product.application.port.ProductQueryPort;
import com.example.product.domain.model.Product;
import com.example.product.domain.model.ProductStatus;
import com.example.product.domain.repository.ProductRepository;
import com.example.product.infrastructure.persistence.entity.ProductJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
class ProductRepositoryAdapter implements ProductRepository, ProductQueryPort {

    private final ProductJpaRepository jpaRepository;

    ProductRepositoryAdapter(ProductJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Product save(Product product) {
        if (product.isNew()) {
            jpaRepository.save(ProductJpaEntity.from(product));
        } else {
            ProductJpaEntity entity = jpaRepository.findWithVariantsById(product.getId())
                    .orElseThrow(() -> new IllegalStateException("Product not found: " + product.getId()));
            entity.update(product);
            jpaRepository.save(entity);
        }
        return product;
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return jpaRepository.findWithVariantsById(id).map(ProductJpaEntity::toDomain);
    }

    @Override
    public boolean existsById(UUID id) {
        return jpaRepository.existsActiveById(id);
    }

    @Override
    public void softDelete(UUID productId) {
        jpaRepository.softDeleteById(productId, Instant.now());
    }

    @Override
    public ProductListResult findSummaries(UUID categoryId, ProductStatus status, int page, int size) {
        Page<ProductSummary> result = jpaRepository.findByFilters(categoryId, status, PageRequest.of(page, size))
                .map(entity -> new ProductSummary(
                        entity.getId(),
                        entity.getName(),
                        entity.getStatus(),
                        entity.getPrice(),
                        entity.getThumbnailUrl(),
                        entity.getCategoryId()));

        return new ProductListResult(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements());
    }
}
