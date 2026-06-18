package com.example.review.infrastructure.persistence;

import com.example.review.domain.model.ReviewStatus;
import com.example.review.infrastructure.persistence.entity.ReviewJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ReviewJpaRepository extends JpaRepository<ReviewJpaEntity, UUID> {

    /** Tenant-scoped lookup by id and status (M2 layer 3, M3 404-over-403). */
    Optional<ReviewJpaEntity> findByIdAndStatusAndTenantId(UUID id, ReviewStatus status, String tenantId);

    /** Non-tenant-scoped lookup used for the update path (load then scope-check in service). */
    Optional<ReviewJpaEntity> findByIdAndStatus(UUID id, ReviewStatus status);

    /** Tenant-scoped existence check (M2 layer 3). */
    boolean existsByUserIdAndProductIdAndStatusAndTenantId(UUID userId, UUID productId, ReviewStatus status, String tenantId);

    boolean existsByUserIdAndProductIdAndStatus(UUID userId, UUID productId, ReviewStatus status);

    /** Tenant-scoped product review listing (M2 layer 3). */
    Page<ReviewJpaEntity> findByProductIdAndStatusAndTenantId(UUID productId, ReviewStatus status, String tenantId, Pageable pageable);

    Page<ReviewJpaEntity> findByProductIdAndStatus(UUID productId, ReviewStatus status, Pageable pageable);

    /** Tenant-scoped user review listing (M2 layer 3). */
    Page<ReviewJpaEntity> findByUserIdAndStatusAndTenantId(UUID userId, ReviewStatus status, String tenantId, Pageable pageable);

    Page<ReviewJpaEntity> findByUserIdAndStatus(UUID userId, ReviewStatus status, Pageable pageable);

    @Query("SELECT COALESCE(AVG(r.rating), 0) FROM ReviewJpaEntity r WHERE r.productId = :productId AND r.status = :status AND r.tenantId = :tenantId")
    double averageRatingByProductIdAndStatusAndTenantId(@Param("productId") UUID productId, @Param("status") ReviewStatus status, @Param("tenantId") String tenantId);

    @Query("SELECT COALESCE(AVG(r.rating), 0) FROM ReviewJpaEntity r WHERE r.productId = :productId AND r.status = :status")
    double averageRatingByProductIdAndStatus(@Param("productId") UUID productId, @Param("status") ReviewStatus status);

    long countByProductIdAndStatusAndTenantId(UUID productId, ReviewStatus status, String tenantId);

    long countByProductIdAndStatus(UUID productId, ReviewStatus status);

    @Query("SELECT r.rating, COUNT(r) FROM ReviewJpaEntity r WHERE r.productId = :productId AND r.status = :status AND r.tenantId = :tenantId GROUP BY r.rating")
    java.util.List<Object[]> ratingDistributionByProductIdAndStatusAndTenantId(@Param("productId") UUID productId, @Param("status") ReviewStatus status, @Param("tenantId") String tenantId);

    @Query("SELECT r.rating, COUNT(r) FROM ReviewJpaEntity r WHERE r.productId = :productId AND r.status = :status GROUP BY r.rating")
    java.util.List<Object[]> ratingDistributionByProductIdAndStatus(@Param("productId") UUID productId, @Param("status") ReviewStatus status);
}
