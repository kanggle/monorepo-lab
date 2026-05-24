package com.example.product.infrastructure.persistence;

import com.example.product.domain.model.ProductImage;
import com.example.product.domain.repository.ProductImageRepository;
import com.example.product.infrastructure.persistence.entity.ProductImageJpaEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class ProductImageRepositoryImpl implements ProductImageRepository {

    private final ProductImageJpaRepository jpaRepository;

    ProductImageRepositoryImpl(ProductImageJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ProductImage save(ProductImage image) {
        jpaRepository.findById(image.getId())
                .ifPresentOrElse(
                        existing -> {
                            existing.update(image);
                            jpaRepository.save(existing);
                        },
                        () -> jpaRepository.save(ProductImageJpaEntity.from(image))
                );
        return image;
    }

    @Override
    public Optional<ProductImage> findById(UUID id) {
        return jpaRepository.findById(id).map(ProductImageJpaEntity::toDomain);
    }

    @Override
    public List<ProductImage> findByProductIdOrderBySortOrder(UUID productId) {
        return jpaRepository.findByProductIdOrderBySortOrderAscUploadedAtAsc(productId)
                .stream()
                .map(ProductImageJpaEntity::toDomain)
                .toList();
    }

    @Override
    public int countByProductId(UUID productId) {
        return jpaRepository.countByProductId(productId);
    }

    @Override
    public void delete(ProductImage image) {
        jpaRepository.deleteById(image.getId());
    }
}
