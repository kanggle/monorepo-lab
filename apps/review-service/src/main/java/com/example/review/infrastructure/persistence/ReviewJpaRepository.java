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

    Optional<ReviewJpaEntity> findByIdAndStatus(UUID id, ReviewStatus status);

    boolean existsByUserIdAndProductIdAndStatus(UUID userId, UUID productId, ReviewStatus status);

    Page<ReviewJpaEntity> findByProductIdAndStatus(UUID productId, ReviewStatus status, Pageable pageable);

    Page<ReviewJpaEntity> findByUserIdAndStatus(UUID userId, ReviewStatus status, Pageable pageable);

    @Query("SELECT COALESCE(AVG(r.rating), 0) FROM ReviewJpaEntity r WHERE r.productId = :productId AND r.status = :status")
    double averageRatingByProductIdAndStatus(@Param("productId") UUID productId, @Param("status") ReviewStatus status);

    long countByProductIdAndStatus(UUID productId, ReviewStatus status);

    @Query("SELECT r.rating, COUNT(r) FROM ReviewJpaEntity r WHERE r.productId = :productId AND r.status = :status GROUP BY r.rating")
    java.util.List<Object[]> ratingDistributionByProductIdAndStatus(@Param("productId") UUID productId, @Param("status") ReviewStatus status);
}
