package com.example.review.domain.model;

import com.example.review.domain.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Review 도메인 모델 — tenantId 단위 테스트")
class ReviewTenantTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final Instant FIXED_TIME = Instant.parse("2026-01-01T00:00:00Z");
    private final Clock clock = Clock.fixed(FIXED_TIME, ZoneOffset.UTC);

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("TenantContext 미설정 시 create() 는 기본 테넌트 'ecommerce' 를 사용한다 (D8 net-zero)")
    void create_noTenantContext_usesDefaultTenant() {
        TenantContext.clear();

        Review review = Review.create(USER_ID, PRODUCT_ID, "테스트상품", 5, "좋은 상품", "매우 만족합니다", clock);

        assertThat(review.getTenantId()).isEqualTo("ecommerce");
    }

    @Test
    @DisplayName("TenantContext 설정 시 create() 는 설정된 테넌트를 사용한다")
    void create_tenantContextSet_usesContextTenant() {
        TenantContext.set("tenant-a");

        Review review = Review.create(USER_ID, PRODUCT_ID, "테스트상품", 5, "좋은 상품", "매우 만족합니다", clock);

        assertThat(review.getTenantId()).isEqualTo("tenant-a");
    }

    @Test
    @DisplayName("reconstitute() 는 제공된 tenantId 를 그대로 복원한다")
    void reconstitute_withTenantId_preservesTenantId() {
        Review review = Review.reconstitute(
                UUID.randomUUID(), USER_ID, PRODUCT_ID, "테스트상품", "tenant-b",
                5, "좋은 상품", "매우 만족합니다",
                ReviewStatus.ACTIVE, FIXED_TIME, FIXED_TIME
        );

        assertThat(review.getTenantId()).isEqualTo("tenant-b");
    }

    @Test
    @DisplayName("reconstitute() 에서 tenantId 가 null 이면 기본 테넌트로 대체된다 (하위 호환, D8)")
    void reconstitute_nullTenantId_fallsBackToDefault() {
        Review review = Review.reconstitute(
                UUID.randomUUID(), USER_ID, PRODUCT_ID, "테스트상품", null,
                5, "좋은 상품", "매우 만족합니다",
                ReviewStatus.ACTIVE, FIXED_TIME, FIXED_TIME
        );

        assertThat(review.getTenantId()).isEqualTo("ecommerce");
    }

    @Test
    @DisplayName("update() 는 tenantId 를 변경하지 않는다")
    void update_doesNotChangeTenantId() {
        TenantContext.set("tenant-a");
        Review review = Review.create(USER_ID, PRODUCT_ID, "테스트상품", 5, "좋은 상품", "매우 만족합니다", clock);

        TenantContext.set("tenant-b");
        review.update(3, "수정 제목", "수정 내용", clock);

        assertThat(review.getTenantId()).isEqualTo("tenant-a");
    }
}
