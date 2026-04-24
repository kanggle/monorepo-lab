package com.example.product.domain.repository;

import com.example.product.domain.model.ProductImage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductImageRepository {

    ProductImage save(ProductImage image);

    Optional<ProductImage> findById(UUID id);

    List<ProductImage> findByProductIdOrderBySortOrder(UUID productId);

    int countByProductId(UUID productId);

    void delete(ProductImage image);

    void saveAll(List<ProductImage> images);
}
