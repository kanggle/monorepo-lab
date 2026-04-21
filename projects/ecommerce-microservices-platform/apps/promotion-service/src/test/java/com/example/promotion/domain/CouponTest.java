package com.example.promotion.domain;

import com.example.promotion.domain.coupon.Coupon;
import com.example.promotion.domain.coupon.CouponAlreadyUsedException;
import com.example.promotion.domain.coupon.CouponExpiredException;
import com.example.promotion.domain.coupon.CouponNotOwnedException;
import com.example.promotion.domain.coupon.CouponRestoreNotAllowedException;
import com.example.promotion.domain.coupon.CouponStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Coupon 도메인 단위 테스트")
class CouponTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-03-28T12:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("쿠폰 발급 시 ISSUED 상태로 생성된다")
    void issue_validInput_createsWithIssuedStatus() {
        Coupon coupon = Coupon.issue("promo-1", "user-1",
                Instant.parse("2026-04-01T00:00:00Z"), clock);

        assertThat(coupon.getCouponId()).isNotBlank();
        assertThat(coupon.getPromotionId()).isEqualTo("promo-1");
        assertThat(coupon.getUserId()).isEqualTo("user-1");
        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.ISSUED);
        assertThat(coupon.getIssuedAt()).isNotNull();
    }

    @Test
    @DisplayName("쿠폰 적용 시 USED 상태로 변경된다")
    void apply_validOwner_changesStatusToUsed() {
        Coupon coupon = Coupon.issue("promo-1", "user-1",
                Instant.parse("2026-04-01T00:00:00Z"), clock);

        coupon.apply("order-1", "user-1", clock);

        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.USED);
        assertThat(coupon.getOrderId()).isEqualTo("order-1");
        assertThat(coupon.getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("다른 사용자가 쿠폰을 사용하려고 하면 예외 발생")
    void apply_differentUser_throws() {
        Coupon coupon = Coupon.issue("promo-1", "user-1",
                Instant.parse("2026-04-01T00:00:00Z"), clock);

        assertThatThrownBy(() -> coupon.apply("order-1", "user-2", clock))
                .isInstanceOf(CouponNotOwnedException.class);
    }

    @Test
    @DisplayName("이미 사용된 쿠폰 재사용 시 예외 발생")
    void apply_alreadyUsed_throws() {
        Coupon coupon = Coupon.issue("promo-1", "user-1",
                Instant.parse("2026-04-01T00:00:00Z"), clock);
        coupon.apply("order-1", "user-1", clock);

        assertThatThrownBy(() -> coupon.apply("order-2", "user-1", clock))
                .isInstanceOf(CouponAlreadyUsedException.class);
    }

    @Test
    @DisplayName("만료된 쿠폰 사용 시 예외 발생")
    void apply_expired_throws() {
        Coupon coupon = Coupon.issue("promo-1", "user-1",
                Instant.parse("2026-03-01T00:00:00Z"), clock);

        assertThatThrownBy(() -> coupon.apply("order-1", "user-1", clock))
                .isInstanceOf(CouponExpiredException.class);
    }

    @Test
    @DisplayName("쿠폰 만료 처리 시 EXPIRED 상태로 변경된다")
    void expire_issuedCoupon_changesStatusToExpired() {
        Coupon coupon = Coupon.issue("promo-1", "user-1",
                Instant.parse("2026-04-01T00:00:00Z"), clock);

        coupon.expire(clock);

        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.EXPIRED);
        assertThat(coupon.getExpiredAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 사용된 쿠폰은 만료 처리해도 상태가 변경되지 않는다")
    void expire_usedCoupon_noChange() {
        Coupon coupon = Coupon.issue("promo-1", "user-1",
                Instant.parse("2026-04-01T00:00:00Z"), clock);
        coupon.apply("order-1", "user-1", clock);

        coupon.expire(clock);

        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.USED);
    }

    @Test
    @DisplayName("명시적으로 EXPIRED 상태인 쿠폰은 적용할 수 없다")
    void apply_explicitlyExpired_throws() {
        Coupon coupon = Coupon.issue("promo-1", "user-1",
                Instant.parse("2026-04-01T00:00:00Z"), clock);
        coupon.expire(clock);

        assertThatThrownBy(() -> coupon.apply("order-1", "user-1", clock))
                .isInstanceOf(CouponExpiredException.class);
    }

    @Test
    @DisplayName("USED 상태의 쿠폰을 복원하면 ISSUED 상태로 변경된다")
    void restore_usedCoupon_changesStatusToIssued() {
        Coupon coupon = Coupon.issue("promo-1", "user-1",
                Instant.parse("2026-04-01T00:00:00Z"), clock);
        coupon.apply("order-1", "user-1", clock);

        coupon.restore();

        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.ISSUED);
        assertThat(coupon.getUsedAt()).isNull();
        assertThat(coupon.getOrderId()).isNull();
    }

    @Test
    @DisplayName("EXPIRED 상태의 쿠폰을 복원하면 예외가 발생한다")
    void restore_expiredCoupon_throwsCouponRestoreNotAllowedException() {
        Coupon coupon = Coupon.issue("promo-1", "user-1",
                Instant.parse("2026-04-01T00:00:00Z"), clock);
        coupon.expire(clock);

        assertThatThrownBy(() -> coupon.restore())
                .isInstanceOf(CouponRestoreNotAllowedException.class);
    }

    @Test
    @DisplayName("이미 ISSUED 상태인 쿠폰에 restore 호출 시 멱등 처리된다")
    void restore_alreadyIssuedCoupon_noOpIdempotent() {
        Coupon coupon = Coupon.issue("promo-1", "user-1",
                Instant.parse("2026-04-01T00:00:00Z"), clock);

        coupon.restore();

        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.ISSUED);
    }
}
