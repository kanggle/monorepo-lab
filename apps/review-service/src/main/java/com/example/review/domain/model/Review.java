package com.example.review.domain.model;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public class Review {

    private UUID id;
    private UUID userId;
    private UUID productId;
    private String productName;
    private Rating rating;
    private String title;
    private String content;
    private ReviewStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    private Review() {}

    public static Review create(UUID userId, UUID productId, String productName,
                                int rating, String title, String content, Clock clock) {
        if (clock == null) throw new IllegalArgumentException("clock must not be null");
        validateTitle(title);
        validateContent(content);

        Review review = new Review();
        review.id = UUID.randomUUID();
        review.userId = userId;
        review.productId = productId;
        review.productName = productName;
        review.rating = new Rating(rating);
        review.title = title.trim();
        review.content = content.trim();
        review.status = ReviewStatus.ACTIVE;
        Instant now = Instant.now(clock);
        review.createdAt = now;
        review.updatedAt = now;
        return review;
    }

    public static Review reconstitute(UUID id, UUID userId, UUID productId, String productName,
                                       int rating, String title, String content, ReviewStatus status,
                                       Instant createdAt, Instant updatedAt) {
        if (id == null) throw new IllegalArgumentException("id must not be null");
        if (userId == null) throw new IllegalArgumentException("userId must not be null");
        if (productId == null) throw new IllegalArgumentException("productId must not be null");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title must not be blank");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content must not be blank");
        if (status == null) throw new IllegalArgumentException("status must not be null");

        Review review = new Review();
        review.id = id;
        review.userId = userId;
        review.productId = productId;
        review.productName = productName;
        review.rating = new Rating(rating);
        review.title = title;
        review.content = content;
        review.status = status;
        review.createdAt = createdAt;
        review.updatedAt = updatedAt;
        return review;
    }

    public void update(int newRating, String newTitle, String newContent, Clock clock) {
        if (clock == null) throw new IllegalArgumentException("clock must not be null");
        validateTitle(newTitle);
        validateContent(newContent);

        this.rating = new Rating(newRating);
        this.title = newTitle.trim();
        this.content = newContent.trim();
        this.updatedAt = Instant.now(clock);
    }

    public void softDelete(Clock clock) {
        if (clock == null) throw new IllegalArgumentException("clock must not be null");
        this.status = ReviewStatus.DELETED;
        this.updatedAt = Instant.now(clock);
    }

    public boolean isOwnedBy(UUID requestUserId) {
        return this.userId.equals(requestUserId);
    }

    public boolean isActive() {
        return this.status == ReviewStatus.ACTIVE;
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getProductId() { return productId; }
    public String getProductName() { return productName; }
    public Rating getRating() { return rating; }
    public int getRatingValue() { return rating.value(); }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public ReviewStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    private static void validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Review title must not be blank");
        }
        if (title.trim().length() > 255) {
            throw new IllegalArgumentException("Review title must not exceed 255 characters");
        }
    }

    private static void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Review content must not be blank");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Review r)) return false;
        return id != null && id.equals(r.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
