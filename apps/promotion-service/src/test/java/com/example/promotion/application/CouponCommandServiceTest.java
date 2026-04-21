package com.example.promotion.application;

import com.example.promotion.application.command.ApplyCouponCommand;
import com.example.promotion.application.command.IssueCouponsCommand;
import com.example.web.exception.AccessDeniedException;
import com.example.promotion.application.port.PromotionEventPublisher;
import com.example.promotion.application.result.ApplyCouponResult;
import com.example.promotion.application.result.IssueCouponsResult;
import com.example.promotion.application.service.CouponCommandService;
import com.example.promotion.domain.coupon.Coupon;
import com.example.promotion.domain.coupon.CouponAlreadyUsedException;
import com.example.promotion.domain.coupon.CouponNotFoundException;
import com.example.promotion.domain.coupon.CouponRepository;
import com.example.promotion.domain.coupon.CouponStatus;
import com.example.promotion.domain.promotion.DiscountType;
import com.example.promotion.domain.promotion.Promotion;
import com.example.promotion.domain.promotion.PromotionNotFoundException;
import com.example.promotion.domain.promotion.PromotionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponCommandService 단위 테스트")
class CouponCommandServiceTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private PromotionRepository promotionRepository;

    @Mock
    private PromotionEventPublisher eventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-03-28T12:00:00Z"), ZoneOffset.UTC);

    private CouponCommandService createService() {
        return new CouponCommandService(couponRepository, promotionRepository, eventPublisher, clock);
    }

    @Test
    @DisplayName("쿠폰 발급 시 발급 수가 반환된다")
    void issueCoupons_validCommand_returnsCount() {
        CouponCommandService service = createService();

        Promotion promotion = Promotion.create(
                "프로모션", "설명", DiscountType.FIXED, 1000, 0, 100,
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"), clock
        );

        given(promotionRepository.findByIdForUpdate(promotion.getPromotionId()))
                .willReturn(Optional.of(promotion));
        given(couponRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(promotionRepository.save(any(Promotion.class))).willAnswer(inv -> inv.getArgument(0));

        IssueCouponsCommand command = new IssueCouponsCommand(
                promotion.getPromotionId(), List.of("user-1", "user-2"), "ADMIN"
        );

        IssueCouponsResult result = service.issueCoupons(command);

        assertThat(result.issuedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("쿠폰 적용 시 할인 금액이 계산된다")
    void applyCoupon_validCoupon_returnsDiscount() {
        CouponCommandService service = createService();

        Promotion promotion = Promotion.create(
                "프로모션", "설명", DiscountType.FIXED, 5000, 0, 100,
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"), clock
        );

        Coupon coupon = Coupon.issue(promotion.getPromotionId(), "user-1",
                Instant.parse("2026-04-01T00:00:00Z"), clock);

        given(couponRepository.findByIdForUpdate(coupon.getCouponId()))
                .willReturn(Optional.of(coupon));
        given(promotionRepository.findById(promotion.getPromotionId()))
                .willReturn(Optional.of(promotion));
        given(couponRepository.save(any(Coupon.class))).willAnswer(inv -> inv.getArgument(0));

        ApplyCouponCommand command = new ApplyCouponCommand(
                coupon.getCouponId(), "user-1", "order-1", 30000
        );

        ApplyCouponResult result = service.applyCoupon(command);

        assertThat(result.discountAmount()).isEqualTo(5000);
        assertThat(result.finalAmount()).isEqualTo(25000);
        verify(eventPublisher).publishCouponUsed(any());
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰 적용 시 예외 발생")
    void applyCoupon_notFound_throws() {
        CouponCommandService service = createService();

        given(couponRepository.findByIdForUpdate("non-existent")).willReturn(Optional.empty());

        ApplyCouponCommand command = new ApplyCouponCommand(
                "non-existent", "user-1", "order-1", 30000
        );

        assertThatThrownBy(() -> service.applyCoupon(command))
                .isInstanceOf(CouponNotFoundException.class);
    }

    @Test
    @DisplayName("이미 사용된 쿠폰 적용 시 예외 발생")
    void applyCoupon_alreadyUsed_throws() {
        CouponCommandService service = createService();

        Promotion promotion = Promotion.create(
                "프로모션", "설명", DiscountType.FIXED, 5000, 0, 100,
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"), clock
        );

        Coupon coupon = Coupon.issue(promotion.getPromotionId(), "user-1",
                Instant.parse("2026-04-01T00:00:00Z"), clock);
        coupon.apply("order-1", "user-1", clock);

        given(couponRepository.findByIdForUpdate(coupon.getCouponId()))
                .willReturn(Optional.of(coupon));

        ApplyCouponCommand command = new ApplyCouponCommand(
                coupon.getCouponId(), "user-1", "order-2", 30000
        );

        assertThatThrownBy(() -> service.applyCoupon(command))
                .isInstanceOf(CouponAlreadyUsedException.class);
    }

    @Test
    @DisplayName("존재하지 않는 프로모션으로 쿠폰 발급 시 예외 발생")
    void issueCoupons_promotionNotFound_throws() {
        CouponCommandService service = createService();

        given(promotionRepository.findByIdForUpdate("non-existent")).willReturn(Optional.empty());

        IssueCouponsCommand command = new IssueCouponsCommand("non-existent", List.of("user-1"), "ADMIN");

        assertThatThrownBy(() -> service.issueCoupons(command))
                .isInstanceOf(PromotionNotFoundException.class);
    }

    @Test
    @DisplayName("주문 취소 시 USED 쿠폰이 ISSUED 상태로 복원된다")
    void restoreCouponsByOrderId_usedCoupons_restoresToIssued() {
        CouponCommandService service = createService();

        Coupon coupon = Coupon.issue("promo-1", "user-1",
                Instant.parse("2026-04-01T00:00:00Z"), clock);
        coupon.apply("order-1", "user-1", clock);

        given(couponRepository.findByOrderIdAndStatus("order-1", CouponStatus.USED))
                .willReturn(List.of(coupon));
        given(couponRepository.save(any(Coupon.class))).willAnswer(inv -> inv.getArgument(0));

        service.restoreCouponsByOrderId("order-1");

        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.ISSUED);
        verify(couponRepository).save(coupon);
    }

    @Test
    @DisplayName("비관리자 역할로 쿠폰 발급 시 AccessDeniedException")
    void issueCoupons_nonAdminRole_throwsAccessDeniedException() {
        CouponCommandService service = createService();

        IssueCouponsCommand command = new IssueCouponsCommand("promo-1", List.of("user-1"), "USER");

        assertThatThrownBy(() -> service.issueCoupons(command))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("주문에 연결된 USED 쿠폰이 없으면 복원을 건너뛴다")
    void restoreCouponsByOrderId_noCouponsFound_skips() {
        CouponCommandService service = createService();

        given(couponRepository.findByOrderIdAndStatus("order-999", CouponStatus.USED))
                .willReturn(List.of());

        assertThatCode(() -> service.restoreCouponsByOrderId("order-999"))
                .doesNotThrowAnyException();

        verify(couponRepository, never()).save(any(Coupon.class));
    }
}
