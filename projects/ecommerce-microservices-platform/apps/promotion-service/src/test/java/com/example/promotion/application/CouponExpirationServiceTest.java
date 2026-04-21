package com.example.promotion.application;

import com.example.promotion.application.port.PromotionEventPublisher;
import com.example.promotion.application.service.CouponExpirationService;
import com.example.promotion.domain.coupon.Coupon;
import com.example.promotion.domain.coupon.CouponRepository;
import com.example.promotion.domain.coupon.CouponStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponExpirationService 단위 테스트")
class CouponExpirationServiceTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private PromotionEventPublisher eventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-03-28T12:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("만료된 쿠폰을 배치 처리하여 EXPIRED 상태로 변경한다")
    void expireCoupons_expiredCoupons_changesStatusAndPublishesEvent() {
        CouponExpirationService service = new CouponExpirationService(
                couponRepository, eventPublisher, clock);

        Coupon coupon1 = Coupon.issue("promo-1", "user-1",
                Instant.parse("2026-03-27T00:00:00Z"), clock);
        Coupon coupon2 = Coupon.issue("promo-1", "user-2",
                Instant.parse("2026-03-27T00:00:00Z"), clock);

        given(couponRepository.findExpiredIssuedCoupons(any(Instant.class), anyInt()))
                .willReturn(List.of(coupon1, coupon2));
        given(couponRepository.save(any(Coupon.class))).willAnswer(inv -> inv.getArgument(0));

        int count = service.expireCoupons();

        assertThat(count).isEqualTo(2);
        assertThat(coupon1.getStatus()).isEqualTo(CouponStatus.EXPIRED);
        assertThat(coupon2.getStatus()).isEqualTo(CouponStatus.EXPIRED);
        verify(eventPublisher, times(2)).publishCouponExpired(any());
    }

    @Test
    @DisplayName("만료된 쿠폰이 없으면 0을 반환한다")
    void expireCoupons_noCoupons_returnsZero() {
        CouponExpirationService service = new CouponExpirationService(
                couponRepository, eventPublisher, clock);

        given(couponRepository.findExpiredIssuedCoupons(any(Instant.class), anyInt()))
                .willReturn(List.of());

        int count = service.expireCoupons();

        assertThat(count).isZero();
    }
}
