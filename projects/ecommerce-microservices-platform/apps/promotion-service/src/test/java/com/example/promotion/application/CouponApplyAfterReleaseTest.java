package com.example.promotion.application;

import com.example.promotion.application.command.ApplyCouponCommand;
import com.example.promotion.application.port.PromotionEventPublisher;
import com.example.promotion.application.service.CouponCommandService;
import com.example.promotion.domain.coupon.Coupon;
import com.example.promotion.domain.coupon.CouponIssueRequestRepository;
import com.example.promotion.domain.coupon.CouponRepository;
import com.example.promotion.domain.coupon.CouponStatus;
import com.example.promotion.domain.promotion.DiscountType;
import com.example.promotion.domain.promotion.Promotion;
import com.example.promotion.domain.promotion.PromotionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * TASK-INT-027 AC-0 — the reproduction.
 *
 * <p>TASK-INT-026 gave the placement a compensation (release) but not an ORDER between the two
 * calls. order-service registers the release BEFORE calling apply, so when the apply times out on
 * the client while promotion-service is still processing it, the release arrives first, finds
 * nothing to give back, and leaves no trace — and the late apply then commits against an order
 * that was never saved.
 *
 * <p>This test asserts the behaviour that must hold, so it stays as the regression once the fence
 * lands: it is RED on the tree that has no fence, and GREEN after.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CouponCommandService — 되돌릴 것이 없던 release 뒤에 도착한 apply (TASK-INT-027)")
class CouponApplyAfterReleaseTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private PromotionRepository promotionRepository;

    @Mock
    private PromotionEventPublisher eventPublisher;

    @Mock
    private CouponIssueRequestRepository couponIssueRequestRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-03-28T12:00:00Z"), ZoneOffset.UTC);

    private CouponCommandService service() {
        return new CouponCommandService(
                couponRepository, promotionRepository, eventPublisher, couponIssueRequestRepository, clock);
    }

    private Promotion fixed5000() {
        return Promotion.create("프로모션", "설명", DiscountType.FIXED, 5000, 0, 100,
                Instant.parse("2026-03-01T00:00:00Z"), Instant.parse("2026-04-01T00:00:00Z"), clock);
    }

    @Test
    @DisplayName("🔴 AC-0 재현 — 주문이 커밋되지 않아 release 가 먼저 갔는데, 늦게 커밋된 apply 가 없는 주문에 쿠폰을 묶는다")
    void applyArrivingAfterTheReleaseOfAnUncommittedPlacement_mustNotUseTheCoupon() {
        Promotion promotion = fixed5000();
        Coupon coupon = Coupon.issue(promotion.getPromotionId(), "user-1",
                Instant.parse("2026-04-01T00:00:00Z"), clock);
        given(couponRepository.findByIdForUpdate(coupon.getCouponId())).willReturn(Optional.of(coupon));
        // lenient: once the fence lands, apply refuses before it ever needs the promotion.
        lenient().when(promotionRepository.findById(promotion.getPromotionId()))
                .thenReturn(Optional.of(promotion));

        // ① The placement transaction did not commit, so its release arrives first. The coupon is
        //    still ISSUED — there is nothing to give back, and today that fact is recorded nowhere.
        service().releaseCoupon(coupon.getCouponId(), "order-1");
        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.ISSUED);

        // ② The apply that timed out on the caller now commits here. "order-1" was never saved.
        catchThrowable(() -> service().applyCoupon(
                new ApplyCouponCommand(coupon.getCouponId(), "user-1", "order-1", 30000)));

        // A dead order must not hold a coupon: nothing can release it later — the cancellation
        // restore path needs an order to fire, and there is none. It would sit USED until expiry.
        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.ISSUED);
        assertThat(coupon.getOrderId()).isNull();
        verify(eventPublisher, never()).publishCouponUsed(any());
    }
}
