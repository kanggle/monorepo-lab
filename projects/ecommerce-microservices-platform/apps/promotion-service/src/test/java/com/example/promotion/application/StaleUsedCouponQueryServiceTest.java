package com.example.promotion.application;

import com.example.promotion.application.service.StaleUsedCouponQueryService;
import com.example.promotion.domain.coupon.CouponRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("StaleUsedCouponQueryService — 오래된 USED 쿠폰 목록 (TASK-INT-028)")
class StaleUsedCouponQueryServiceTest {

    @Mock
    private CouponRepository couponRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-16T12:00:00Z"), ZoneOffset.UTC);

    private StaleUsedCouponQueryService service() {
        return new StaleUsedCouponQueryService(couponRepository, clock);
    }

    @Test
    @DisplayName("기준 시각은 지금 − olderThanMinutes 다")
    void cutoffIsNowMinusOlderThanMinutes() {
        given(couponRepository.findStaleUsedCoupons(Instant.parse("2026-09-16T11:00:00Z"), 200)).willReturn(List.of());

        service().find(60, 200);

        verify(couponRepository).findStaleUsedCoupons(Instant.parse("2026-09-16T11:00:00Z"), 200);
    }

    @Test
    @DisplayName("30분 미만은 HTTP 가 아닌 경로로 불려도 거절한다 — 진행 중인 주문의 쿠폰을 내놓지 않는다")
    void belowFloor_isRefusedEvenOffTheHttpPath() {
        assertThatThrownBy(() -> service().find(29, 200)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service().find(60, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service().find(60, 501)).isInstanceOf(IllegalArgumentException.class);
        verify(couponRepository, never()).findStaleUsedCoupons(any(), anyInt());
    }
}
