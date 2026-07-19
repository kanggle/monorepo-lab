package com.example.review.infrastructure.persistence;

import com.example.review.application.ReviewSortFields;
import com.example.review.application.port.ReviewQueryPort;
import com.example.review.application.result.MyReviewListResult;
import com.example.review.application.result.ReviewListResult;
import com.example.review.application.result.ReviewSummaryResult;
import com.example.review.domain.model.Review;
import com.example.review.domain.model.ReviewStatus;
import com.example.review.domain.repository.ReviewRepository;
import com.example.review.domain.tenant.TenantContext;
import com.example.review.infrastructure.persistence.entity.ReviewJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
class ReviewRepositoryImpl implements ReviewRepository, ReviewQueryPort {

    private final ReviewJpaRepository jpaRepository;

    ReviewRepositoryImpl(ReviewJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Review save(Review review) {
        Optional<ReviewJpaEntity> existing = jpaRepository.findById(review.getId());
        if (existing.isPresent()) {
            existing.get().update(review);
            jpaRepository.save(existing.get());
        } else {
            jpaRepository.save(ReviewJpaEntity.from(review));
        }
        return review;
    }

    /**
     * Tenant-scoped lookup: a review in another tenant resolves to empty → 404
     * (M2 layer 3, M3 404-over-403; TASK-BE-403).
     */
    @Override
    public Optional<Review> findActiveById(UUID id) {
        return jpaRepository.findByIdAndStatusAndTenantId(id, ReviewStatus.ACTIVE, TenantContext.currentTenant())
                .map(ReviewJpaEntity::toDomain);
    }

    /**
     * Tenant-scoped existence check (M2 layer 3; TASK-BE-403).
     */
    @Override
    public boolean existsByUserIdAndProductId(UUID userId, UUID productId) {
        return jpaRepository.existsByUserIdAndProductIdAndStatusAndTenantId(
                userId, productId, ReviewStatus.ACTIVE, TenantContext.currentTenant());
    }

    @Override
    public ReviewListResult findByProductId(UUID productId, int page, int size, String sort) {
        String tenantId = TenantContext.currentTenant();
        Sort jpaSort = parseSort(sort);
        Page<ReviewJpaEntity> result = jpaRepository.findByProductIdAndStatusAndTenantId(
                productId, ReviewStatus.ACTIVE, tenantId, PageRequest.of(page, size, jpaSort));

        double averageRating = fetchAverageRating(productId, tenantId);
        long totalReviews = fetchTotalReviews(productId, tenantId);

        List<ReviewListResult.ReviewItem> items = result.getContent().stream()
                .map(entity -> new ReviewListResult.ReviewItem(
                        entity.getId(),
                        entity.getUserId(),
                        entity.getRating(),
                        entity.getTitle(),
                        entity.getContent(),
                        entity.getCreatedAt(),
                        entity.getUpdatedAt()
                ))
                .toList();

        return new ReviewListResult(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                roundToOneDecimal(averageRating),
                totalReviews
        );
    }

    @Override
    public ReviewSummaryResult getSummaryByProductId(UUID productId) {
        String tenantId = TenantContext.currentTenant();
        double averageRating = fetchAverageRating(productId, tenantId);
        long totalReviews = fetchTotalReviews(productId, tenantId);

        List<Object[]> distribution = jpaRepository.ratingDistributionByProductIdAndStatusAndTenantId(
                productId, ReviewStatus.ACTIVE, tenantId);

        Map<Integer, Long> ratingDistribution = new LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) {
            ratingDistribution.put(i, 0L);
        }
        for (Object[] row : distribution) {
            int rating = (int) row[0];
            long count = (long) row[1];
            ratingDistribution.put(rating, count);
        }

        return new ReviewSummaryResult(
                productId,
                roundToOneDecimal(averageRating),
                totalReviews,
                ratingDistribution
        );
    }

    @Override
    public MyReviewListResult findByUserId(UUID userId, int page, int size) {
        Page<ReviewJpaEntity> result = jpaRepository.findByUserIdAndStatusAndTenantId(
                userId, ReviewStatus.ACTIVE, TenantContext.currentTenant(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        List<MyReviewListResult.MyReviewItem> items = result.getContent().stream()
                .map(entity -> new MyReviewListResult.MyReviewItem(
                        entity.getId(),
                        entity.getProductId(),
                        entity.getProductName(),
                        entity.getRating(),
                        entity.getTitle(),
                        entity.getContent(),
                        entity.getCreatedAt()
                ))
                .toList();

        return new MyReviewListResult(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements()
        );
    }

    private double fetchAverageRating(UUID productId, String tenantId) {
        return jpaRepository.averageRatingByProductIdAndStatusAndTenantId(productId, ReviewStatus.ACTIVE, tenantId);
    }

    private long fetchTotalReviews(UUID productId, String tenantId) {
        return jpaRepository.countByProductIdAndStatusAndTenantId(productId, ReviewStatus.ACTIVE, tenantId);
    }

    private double roundToOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        String field = ReviewSortFields.requireValid(sort);
        String[] parts = sort.split(",");
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }
}
