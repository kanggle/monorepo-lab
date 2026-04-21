package com.example.promotion.domain;

import com.example.promotion.domain.promotion.CouponLimitExceededException;
import com.example.promotion.domain.promotion.DiscountType;
import com.example.promotion.domain.promotion.Promotion;
import com.example.promotion.domain.promotion.PromotionAlreadyEndedException;
import com.example.promotion.domain.promotion.PromotionNotActiveException;
import com.example.promotion.domain.promotion.PromotionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Promotion 도메인 단위 테스트")
class PromotionTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-03-28T12:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("프로모션 생성 시 기본 값이 올바르게 설정된다")
    void create_validInput_setsDefaults() {
        Promotion promotion = Promotion.create(
                "봄맞이 할인", "봄 시즌 프로모션",
                DiscountType.FIXED, 5000, 10000, 100,
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"),
                clock
        );

        assertThat(promotion.getPromotionId()).isNotBlank();
        assertThat(promotion.getName()).isEqualTo("봄맞이 할인");
        assertThat(promotion.getIssuedCount()).isZero();
        assertThat(promotion.getStatus(clock)).isEqualTo(PromotionStatus.ACTIVE);
    }

    @Test
    @DisplayName("시작일 이전이면 SCHEDULED 상태이다")
    void getStatus_beforeStartDate_returnsScheduled() {
        Promotion promotion = Promotion.create(
                "미래 프로모션", "설명",
                DiscountType.PERCENTAGE, 10, 0, 100,
                Instant.parse("2026-04-01T00:00:00Z"),
                Instant.parse("2026-05-01T00:00:00Z"),
                clock
        );

        assertThat(promotion.getStatus(clock)).isEqualTo(PromotionStatus.SCHEDULED);
    }

    @Test
    @DisplayName("종료일 이후면 ENDED 상태이다")
    void getStatus_afterEndDate_returnsEnded() {
        Promotion promotion = Promotion.create(
                "과거 프로모션", "설명",
                DiscountType.FIXED, 1000, 0, 100,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-02-01T00:00:00Z"),
                clock
        );

        assertThat(promotion.getStatus(clock)).isEqualTo(PromotionStatus.ENDED);
    }

    @Test
    @DisplayName("종료된 프로모션은 수정할 수 없다")
    void update_endedPromotion_throws() {
        Promotion promotion = Promotion.create(
                "과거 프로모션", "설명",
                DiscountType.FIXED, 1000, 0, 100,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-02-01T00:00:00Z"),
                clock
        );

        assertThatThrownBy(() -> promotion.update(
                "수정", "설명", DiscountType.FIXED, 2000, 0, 100,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-02-01T00:00:00Z"),
                clock
        )).isInstanceOf(PromotionAlreadyEndedException.class);
    }

    @Test
    @DisplayName("쿠폰 발급 검증 시 비활성 프로모션이면 예외 발생")
    void validateCanIssue_notActive_throws() {
        Promotion promotion = Promotion.create(
                "미래 프로모션", "설명",
                DiscountType.FIXED, 1000, 0, 100,
                Instant.parse("2026-04-01T00:00:00Z"),
                Instant.parse("2026-05-01T00:00:00Z"),
                clock
        );

        assertThatThrownBy(() -> promotion.validateCanIssue(1, clock))
                .isInstanceOf(PromotionNotActiveException.class);
    }

    @Test
    @DisplayName("쿠폰 발급 시 수량 초과하면 예외 발생")
    void validateCanIssue_exceedsLimit_throws() {
        Promotion promotion = Promotion.create(
                "한정 프로모션", "설명",
                DiscountType.FIXED, 1000, 0, 2,
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"),
                clock
        );

        assertThatThrownBy(() -> promotion.validateCanIssue(3, clock))
                .isInstanceOf(CouponLimitExceededException.class);
    }

    @Test
    @DisplayName("FIXED 할인 계산: 할인 금액이 주문 금액을 초과하지 않는다")
    void calculateDiscount_fixedExceedsOrder_capsAtOrder() {
        Promotion promotion = Promotion.create(
                "프로모션", "설명",
                DiscountType.FIXED, 50000, 0, 100,
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"),
                clock
        );

        assertThat(promotion.calculateDiscount(30000)).isEqualTo(30000);
    }

    @Test
    @DisplayName("PERCENTAGE 할인 계산: maxDiscountAmount 적용")
    void calculateDiscount_percentageWithMaxCap() {
        Promotion promotion = Promotion.create(
                "퍼센트 프로모션", "설명",
                DiscountType.PERCENTAGE, 20, 5000, 100,
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"),
                clock
        );

        // 20% of 50000 = 10000, but max cap is 5000
        assertThat(promotion.calculateDiscount(50000)).isEqualTo(5000);
    }

    @Test
    @DisplayName("PERCENTAGE 할인 계산: maxDiscountAmount가 0이면 제한 없음")
    void calculateDiscount_percentageNoMaxCap() {
        Promotion promotion = Promotion.create(
                "퍼센트 프로모션", "설명",
                DiscountType.PERCENTAGE, 20, 0, 100,
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"),
                clock
        );

        assertThat(promotion.calculateDiscount(50000)).isEqualTo(10000);
    }

    @Test
    @DisplayName("종료일이 시작일보다 앞이면 생성 실패")
    void create_endBeforeStart_throws() {
        assertThatThrownBy(() -> Promotion.create(
                "잘못된 프로모션", "설명",
                DiscountType.FIXED, 1000, 0, 100,
                Instant.parse("2026-04-01T00:00:00Z"),
                Instant.parse("2026-03-01T00:00:00Z"),
                clock
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("할인 값이 0 이하이면 생성 실패")
    void create_negativeDiscount_throws() {
        assertThatThrownBy(() -> Promotion.create(
                "잘못된 프로모션", "설명",
                DiscountType.FIXED, 0, 0, 100,
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"),
                clock
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("퍼센트 할인이 100을 초과하면 생성 실패")
    void create_percentageOver100_throws() {
        assertThatThrownBy(() -> Promotion.create(
                "잘못된 프로모션", "설명",
                DiscountType.PERCENTAGE, 150, 0, 100,
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"),
                clock
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("발급 수 증가 후 hasIssuedCoupons가 true를 반환한다")
    void incrementIssuedCount_afterIncrement_hasCoupons() {
        Promotion promotion = Promotion.create(
                "프로모션", "설명",
                DiscountType.FIXED, 1000, 0, 100,
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"),
                clock
        );

        assertThat(promotion.hasIssuedCoupons()).isFalse();
        promotion.incrementIssuedCount(5);
        assertThat(promotion.hasIssuedCoupons()).isTrue();
        assertThat(promotion.getIssuedCount()).isEqualTo(5);
    }
}
