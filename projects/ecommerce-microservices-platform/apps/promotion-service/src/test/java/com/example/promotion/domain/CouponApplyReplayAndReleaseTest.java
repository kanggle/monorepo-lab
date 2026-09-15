package com.example.promotion.domain;

import com.example.promotion.domain.coupon.Coupon;
import com.example.promotion.domain.coupon.CouponAlreadyUsedException;
import com.example.promotion.domain.coupon.CouponStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Coupon — 같은 주문 재적용과 주문 단위 해제 (TASK-INT-026)")
class CouponApplyReplayAndReleaseTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-03-28T12:00:00Z"), ZoneOffset.UTC);

    private Coupon issued() {
        return Coupon.issue("promo-1", "user-1", Instant.parse("2026-04-01T00:00:00Z"), clock);
    }

    @Test
    @DisplayName("같은 주문·같은 사용자가 다시 적용하면 false 를 돌려주고 아무것도 바꾸지 않는다")
    void apply_sameOrderAgain_isReplay() {
        Coupon coupon = issued();
        assertThat(coupon.apply("order-1", "user-1", clock)).isTrue();
        Instant usedAt = coupon.getUsedAt();

        assertThat(coupon.apply("order-1", "user-1", clock)).isFalse();

        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.USED);
        assertThat(coupon.getOrderId()).isEqualTo("order-1");
        assertThat(coupon.getUsedAt()).isEqualTo(usedAt);
    }

    @Test
    @DisplayName("다른 주문이 적용하면 여전히 COUPON_ALREADY_USED")
    void apply_otherOrder_stillAlreadyUsed() {
        Coupon coupon = issued();
        coupon.apply("order-1", "user-1", clock);

        assertThatThrownBy(() -> coupon.apply("order-2", "user-1", clock))
                .isInstanceOf(CouponAlreadyUsedException.class);
    }

    @Test
    @DisplayName("그 주문이 쓴 쿠폰은 해제되어 ISSUED 로 돌아간다")
    void releaseFor_sameOrder_restores() {
        Coupon coupon = issued();
        coupon.apply("order-1", "user-1", clock);

        assertThat(coupon.releaseFor("order-1")).isTrue();

        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.ISSUED);
        assertThat(coupon.getOrderId()).isNull();
        assertThat(coupon.getUsedAt()).isNull();
    }

    @Test
    @DisplayName("🔴 다른 주문이 쓴 쿠폰은 해제하지 않는다 — 거절된 apply 뒤의 해제가 남의 주문을 풀면 안 된다")
    void releaseFor_otherOrder_isNoop() {
        Coupon coupon = issued();
        coupon.apply("order-1", "user-1", clock);

        assertThat(coupon.releaseFor("order-2")).isFalse();

        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.USED);
        assertThat(coupon.getOrderId()).isEqualTo("order-1");
    }

    @Test
    @DisplayName("쓰이지 않은 쿠폰의 해제는 아무 일도 하지 않는다")
    void releaseFor_issued_isNoop() {
        Coupon coupon = issued();

        assertThat(coupon.releaseFor("order-1")).isFalse();
        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.ISSUED);
    }
}
