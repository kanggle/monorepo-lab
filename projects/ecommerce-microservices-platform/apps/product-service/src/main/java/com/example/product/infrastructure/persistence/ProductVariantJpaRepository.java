package com.example.product.infrastructure.persistence;

import com.example.product.infrastructure.persistence.entity.ProductVariantJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ProductVariantJpaRepository extends JpaRepository<ProductVariantJpaEntity, UUID> {

    Optional<ProductVariantJpaEntity> findBySku(String sku);
}
