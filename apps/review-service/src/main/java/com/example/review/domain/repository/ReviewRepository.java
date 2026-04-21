package com.example.review.domain.repository;

import com.example.review.domain.model.Review;

import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository {

    Review save(Review review);

    Optional<Review> findActiveById(UUID id);

    boolean existsByUserIdAndProductId(UUID userId, UUID productId);
}
