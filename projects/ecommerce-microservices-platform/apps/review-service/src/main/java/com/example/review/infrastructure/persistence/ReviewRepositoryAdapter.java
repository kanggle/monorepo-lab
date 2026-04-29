package com.example.review.infrastructure.persistence;

import com.example.review.application.port.ReviewQueryPort;
import com.example.review.application.result.MyReviewListResult;
import com.example.review.application.result.ReviewListResult;
import com.example.review.application.result.ReviewSummaryResult;
import com.example.review.domain.model.Review;
import com.example.review.domain.model.ReviewStatus;
import com.example.review.domain.repository.ReviewRepository;
import com.example.review.infrastructure.persistence.entity.ReviewJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
class ReviewRepositoryAdapter implements ReviewRepository, ReviewQueryPort {

    private final ReviewJpaRepository jpaRepository;

    ReviewRepositoryAdapter(ReviewJpaRepository jpaRepository) {
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

    @Override
    public Optional<Review> findActiveById(UUID id) {
        return jpaRepository.findByIdAndStatus(id, ReviewStatus.ACTIVE)
                .map(ReviewJpaEntity::toDomain);
    }

    @Override
    public boolean existsByUserIdAndProductId(UUID userId, UUID productId) {
        return jpaRepository.existsByUserIdAndProductIdAndStatus(userId, productId, ReviewStatus.ACTIVE);
    }

    @Override
    public ReviewListResult findByProductId(UUID productId, int page, int size, String sort) {
        Sort jpaSort = parseSort(sort);
        Page<ReviewJpaEntity> result = jpaRepository.findByProductIdAndStatus(
                productId, ReviewStatus.ACTIVE, PageRequest.of(page, size, jpaSort));

        double averageRating = fetchAverageRating(productId);
        long totalReviews = fetchTotalReviews(productId);

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
        double averageRating = fetchAverageRating(productId);
        long totalReviews = fetchTotalReviews(productId);

        List<Object[]> distribution = jpaRepository.ratingDistributionByProductIdAndStatus(
                productId, ReviewStatus.ACTIVE);

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
        Page<ReviewJpaEntity> result = jpaRepository.findByUserIdAndStatus(
                userId, ReviewStatus.ACTIVE, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

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

    private double fetchAverageRating(UUID productId) {
        return jpaRepository.averageRatingByProductIdAndStatus(productId, ReviewStatus.ACTIVE);
    }

    private long fetchTotalReviews(UUID productId) {
        return jpaRepository.countByProductIdAndStatus(productId, ReviewStatus.ACTIVE);
    }

    private double roundToOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt", "rating");

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        if (!ALLOWED_SORT_FIELDS.contains(field)) {
            throw new IllegalArgumentException("Invalid sort field: " + field);
        }
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }
}
