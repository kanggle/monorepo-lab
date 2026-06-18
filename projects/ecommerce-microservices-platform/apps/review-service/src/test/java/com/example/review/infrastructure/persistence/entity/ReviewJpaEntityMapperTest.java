package com.example.review.infrastructure.persistence.entity;

import com.example.review.domain.model.Review;
import com.example.review.domain.model.ReviewStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewJpaEntity 매핑 단위 테스트 (tenantId 포함)")
class ReviewJpaEntityMapperTest {

    private static final UUID ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final Instant FIXED_TIME = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    @DisplayName("도메인 -> JpaEntity 변환 시 tenantId 가 올바르게 매핑된다")
    void from_mapsAllFields_includingTenantId() {
        Review review = Review.reconstitute(
                ID, USER_ID, PRODUCT_ID, "테스트상품", "tenant-a",
                5, "좋은 상품", "매우 만족합니다",
                ReviewStatus.ACTIVE, FIXED_TIME, FIXED_TIME
        );

        ReviewJpaEntity entity = ReviewJpaEntity.from(review);

        assertThat(entity.getId()).isEqualTo(ID);
        assertThat(entity.getUserId()).isEqualTo(USER_ID);
        assertThat(entity.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(entity.getProductName()).isEqualTo("테스트상품");
        assertThat(entity.getTenantId()).isEqualTo("tenant-a");
        assertThat(entity.getRating()).isEqualTo(5);
        assertThat(entity.getTitle()).isEqualTo("좋은 상품");
        assertThat(entity.getContent()).isEqualTo("매우 만족합니다");
        assertThat(entity.getStatus()).isEqualTo(ReviewStatus.ACTIVE);
        assertThat(entity.getCreatedAt()).isEqualTo(FIXED_TIME);
        assertThat(entity.getUpdatedAt()).isEqualTo(FIXED_TIME);
    }

    @Test
    @DisplayName("JpaEntity -> 도메인 변환 시 tenantId 가 올바르게 복원된다")
    void toDomain_mapsAllFields_includingTenantId() {
        Review review = Review.reconstitute(
                ID, USER_ID, PRODUCT_ID, "테스트상품", "ecommerce",
                4, "괜찮은 상품", "그저 그래요",
                ReviewStatus.ACTIVE, FIXED_TIME, FIXED_TIME
        );
        ReviewJpaEntity entity = ReviewJpaEntity.from(review);

        Review restored = entity.toDomain();

        assertThat(restored.getId()).isEqualTo(ID);
        assertThat(restored.getUserId()).isEqualTo(USER_ID);
        assertThat(restored.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(restored.getProductName()).isEqualTo("테스트상품");
        assertThat(restored.getTenantId()).isEqualTo("ecommerce");
        assertThat(restored.getRatingValue()).isEqualTo(4);
        assertThat(restored.getTitle()).isEqualTo("괜찮은 상품");
        assertThat(restored.getContent()).isEqualTo("그저 그래요");
        assertThat(restored.getStatus()).isEqualTo(ReviewStatus.ACTIVE);
        assertThat(restored.getCreatedAt()).isEqualTo(FIXED_TIME);
        assertThat(restored.getUpdatedAt()).isEqualTo(FIXED_TIME);
    }

    @Test
    @DisplayName("도메인 -> JpaEntity -> 도메인 왕복 변환 시 tenantId 손실이 없다")
    void roundTrip_noDataLoss_tenantId() {
        Review original = Review.reconstitute(
                ID, USER_ID, PRODUCT_ID, "테스트상품", "tenant-x",
                3, "보통 상품", "평범합니다",
                ReviewStatus.ACTIVE, FIXED_TIME, FIXED_TIME
        );

        ReviewJpaEntity entity = ReviewJpaEntity.from(original);
        Review restored = entity.toDomain();

        assertThat(restored.getTenantId()).isEqualTo("tenant-x");
        assertThat(restored.getId()).isEqualTo(original.getId());
    }

    @Test
    @DisplayName("기본 테넌트 'ecommerce' 로 reconstitute 된 Review 는 tenantId 가 'ecommerce' 다 (D8 net-zero)")
    void reconstitute_nullTenantId_defaultsToEcommerce() {
        Review review = Review.reconstitute(
                ID, USER_ID, PRODUCT_ID, "테스트상품", null,
                5, "좋은 상품", "매우 만족합니다",
                ReviewStatus.ACTIVE, FIXED_TIME, FIXED_TIME
        );

        assertThat(review.getTenantId()).isEqualTo("ecommerce");
    }
}
