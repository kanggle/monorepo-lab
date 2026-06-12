package com.example.product.infrastructure.persistence;

import com.example.product.infrastructure.persistence.entity.CategoryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface CategoryJpaRepository extends JpaRepository<CategoryJpaEntity, UUID> {

    Optional<CategoryJpaEntity> findByIdAndTenantId(UUID id, String tenantId);

    List<CategoryJpaEntity> findAllByTenantId(String tenantId);
}
