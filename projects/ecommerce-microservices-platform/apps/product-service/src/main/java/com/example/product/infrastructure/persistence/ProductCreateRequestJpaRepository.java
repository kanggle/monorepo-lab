package com.example.product.infrastructure.persistence;

import com.example.product.infrastructure.persistence.entity.ProductCreateRequestJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Spring Data repository for {@code product_create_request} (TASK-BE-536). */
interface ProductCreateRequestJpaRepository extends JpaRepository<ProductCreateRequestJpaEntity, Long> {

    Optional<ProductCreateRequestJpaEntity> findByTenantIdAndIdempotencyKey(
            String tenantId, String idempotencyKey);
}
