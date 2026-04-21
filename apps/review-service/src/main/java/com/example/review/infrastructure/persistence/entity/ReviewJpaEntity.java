package com.example.review.infrastructure.persistence.entity;

import com.example.review.domain.model.Review;
import com.example.review.domain.model.ReviewStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewJpaEntity implements Persistable<UUID> {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "product_id", nullable = false, columnDefinition = "uuid")
    private UUID productId;

    @Column(name = "product_name")
    private String productName;

    @Column(nullable = false)
    private int rating;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean isNew = false;

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    public static ReviewJpaEntity from(Review review) {
        ReviewJpaEntity entity = new ReviewJpaEntity();
        entity.id = review.getId();
        entity.userId = review.getUserId();
        entity.productId = review.getProductId();
        entity.productName = review.getProductName();
        entity.rating = review.getRatingValue();
        entity.title = review.getTitle();
        entity.content = review.getContent();
        entity.status = review.getStatus();
        entity.createdAt = review.getCreatedAt();
        entity.updatedAt = review.getUpdatedAt();
        entity.isNew = true;
        return entity;
    }

    public Review toDomain() {
        return Review.reconstitute(
                id, userId, productId, productName, rating,
                title, content, status, createdAt, updatedAt
        );
    }

    public void update(Review review) {
        this.rating = review.getRatingValue();
        this.title = review.getTitle();
        this.content = review.getContent();
        this.status = review.getStatus();
        this.updatedAt = review.getUpdatedAt();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReviewJpaEntity e)) return false;
        return id != null && id.equals(e.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
