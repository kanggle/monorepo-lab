package com.example.promotion.application;

import com.example.promotion.application.command.ApplyCouponCommand;
import com.example.promotion.application.port.PromotionEventPublisher;
import com.example.promotion.application.result.ApplyCouponResult;
import com.example.promotion.application.service.CouponCommandService;
import com.example.promotion.domain.coupon.Coupon;
import com.example.promotion.domain.coupon.CouponIssueRequestRepository;
import com.example.promotion.domain.coupon.CouponReleaseRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponCommandService — 같은 주문 재적용과 해제 (TASK-INT-026)")
class CouponCommandServiceReplayReleaseTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private PromotionRepository promotionRepository;

    @Mock
    private PromotionEventPublisher eventPublisher;

    @Mock
    private CouponIssueRequestRepository couponIssueRequestRepository;

    @Mock
    private CouponReleaseRepository couponReleaseRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-03-28T12:00:00Z"), ZoneOffset.UTC);

    private CouponCommandService service() {
        return new CouponCommandService(couponRepository, promotionRepository, eventPublisher,
                couponIssueRequestRepository, couponReleaseRepository, clock);
    }

    private Promotion fixed5000() {
        return Promotion.create("프로모션", "설명", DiscountType.FIXED, 5000, 0, 100,
                Instant.parse("2026-03-01T00:00:00Z"), Instant.parse("2026-04-01T00:00:00Z"), clock);
    }

    @Test
    @DisplayName("🔴 같은 주문의 재적용은 같은 할인을 돌려주되, 다시 저장하거나 CouponUsed 를 또 발행하지 않는다")
    void applyCoupon_sameOrderReplay_sameDiscount_noSecondSaveOrEvent() {
        Promotion promotion = fixed5000();
        Coupon coupon = Coupon.issue(promotion.getPromotionId(), "user-1",
                Instant.parse("2026-04-01T00:00:00Z"), clock);
        coupon.apply("order-1", "user-1", clock);
        given(couponRepository.findByIdForUpdate(coupon.getCouponId())).willReturn(Optional.of(coupon));
        given(promotionRepository.findById(promotion.getPromotionId())).willReturn(Optional.of(promotion));

        ApplyCouponResult result = service().applyCoupon(
                new ApplyCouponCommand(coupon.getCouponId(), "user-1", "order-1", 30000));

        assertThat(result.discountAmount()).isEqualTo(5000);
        assertThat(result.finalAmount()).isEqualTo(25000);
        verify(couponRepository, never()).save(any());
        verify(eventPublisher, never()).publishCouponUsed(any());
    }

    @Test
    @DisplayName("그 주문이 쓴 쿠폰을 해제하면 ISSUED 로 저장한다")
    void releaseCoupon_usedBySameOrder_restoresAndSaves() {
        Coupon coupon = Coupon.issue("promo-1", "user-1", Instant.parse("2026-04-01T00:00:00Z"), clock);
        coupon.apply("order-1", "user-1", clock);
        given(couponRepository.findByIdForUpdate(coupon.getCouponId())).willReturn(Optional.of(coupon));
        given(couponRepository.save(any(Coupon.class))).willAnswer(inv -> inv.getArgument(0));

        service().releaseCoupon(coupon.getCouponId(), "order-1");

        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.ISSUED);
        verify(couponRepository).save(coupon);
    }

    @Test
    @DisplayName("다른 주문이 쓴 쿠폰은 해제하지 않고 저장도 하지 않는다")
    void releaseCoupon_usedByOtherOrder_noop() {
        Coupon coupon = Coupon.issue("promo-1", "user-1", Instant.parse("2026-04-01T00:00:00Z"), clock);
        coupon.apply("order-1", "user-1", clock);
        given(couponRepository.findByIdForUpdate(coupon.getCouponId())).willReturn(Optional.of(coupon));

        service().releaseCoupon(coupon.getCouponId(), "order-2");

        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.USED);
        verify(couponRepository, never()).save(any());
    }

    @Test
    @DisplayName("없는 쿠폰의 해제는 조용히 끝난다 — 재시도가 해롭지 않다")
    void releaseCoupon_notFound_noop() {
        given(couponRepository.findByIdForUpdate("missing")).willReturn(Optional.empty());

        service().releaseCoupon("missing", "order-1");

        verify(couponRepository, never()).save(any());
    }
}
