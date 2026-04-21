package com.example.product.infrastructure.persistence;

import com.example.product.infrastructure.persistence.entity.ProductImageJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface ProductImageJpaRepository extends JpaRepository<ProductImageJpaEntity, UUID> {

    List<ProductImageJpaEntity> findByProductIdOrderBySortOrderAscUploadedAtAsc(UUID productId);

    int countByProductId(UUID productId);
}
