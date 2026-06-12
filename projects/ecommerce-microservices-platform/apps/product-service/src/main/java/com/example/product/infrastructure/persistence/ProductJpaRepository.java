package com.example.product.infrastructure.persistence;

import com.example.product.domain.model.ProductStatus;
import com.example.product.infrastructure.persistence.entity.ProductJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

interface ProductJpaRepository extends JpaRepository<ProductJpaEntity, UUID> {

    @Query("SELECT p FROM ProductJpaEntity p LEFT JOIN FETCH p.variants WHERE p.id = :id AND p.tenantId = :tenantId AND p.deletedAt IS NULL")
    Optional<ProductJpaEntity> findWithVariantsById(@Param("id") UUID id, @Param("tenantId") String tenantId);

    @Query("SELECT p FROM ProductJpaEntity p WHERE p.tenantId = :tenantId AND (:categoryId IS NULL OR p.categoryId = :categoryId) AND (:status IS NULL OR p.status = :status) AND p.deletedAt IS NULL")
    Page<ProductJpaEntity> findByFilters(
            @Param("tenantId") String tenantId,
            @Param("categoryId") UUID categoryId,
            @Param("status") ProductStatus status,
            Pageable pageable);

    @Query("SELECT COUNT(p) > 0 FROM ProductJpaEntity p WHERE p.id = :id AND p.tenantId = :tenantId AND p.deletedAt IS NULL")
    boolean existsActiveById(@Param("id") UUID id, @Param("tenantId") String tenantId);

    @Modifying
    @Query("UPDATE ProductJpaEntity p SET p.deletedAt = :deletedAt WHERE p.id = :id AND p.tenantId = :tenantId")
    void softDeleteById(@Param("id") UUID id, @Param("tenantId") String tenantId, @Param("deletedAt") Instant deletedAt);
}
