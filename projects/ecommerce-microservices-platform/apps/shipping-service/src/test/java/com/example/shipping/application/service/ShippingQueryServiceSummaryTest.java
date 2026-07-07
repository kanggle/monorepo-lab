package com.example.shipping.application.service;

import com.example.web.exception.AccessDeniedException;
import com.example.common.summary.PeriodSummary;
import com.example.shipping.domain.repository.ShippingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("ShippingQueryService.getPeriodSummary 단위 테스트")
class ShippingQueryServiceSummaryTest {

    @InjectMocks
    private ShippingQueryService shippingQueryService;

    @Mock
    private ShippingRepository shippingRepository;

    @Test
    @DisplayName("배송 없을 때 모든 카운트가 0")
    void getPeriodSummary_empty_returnsAllZeros() {
        given(shippingRepository.countAllForTenant()).willReturn(0L);
        given(shippingRepository.countCreatedBetween(any(Instant.class), any(Instant.class))).willReturn(0L);

        PeriodSummary result = shippingQueryService.getPeriodSummary("ECOMMERCE_OPERATOR");

        assertThat(result.today()).isZero();
        assertThat(result.week()).isZero();
        assertThat(result.month()).isZero();
        assertThat(result.total()).isZero();
    }

    @Test
    @DisplayName("오늘 생성된 배송 1건이 today에 반영됨")
    void getPeriodSummary_oneTodayRow_countedInToday() {
        // total = 1, today = 1 (any between call for today window), week/month vary
        given(shippingRepository.countAllForTenant()).willReturn(1L);
        // First call = today window, second = week window, third = month window
        given(shippingRepository.countCreatedBetween(any(Instant.class), any(Instant.class)))
                .willReturn(1L, 1L, 1L);

        PeriodSummary result = shippingQueryService.getPeriodSummary("ECOMMERCE_OPERATOR");

        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.today()).isEqualTo(1L);
        assertThat(result.week()).isEqualTo(1L);
        assertThat(result.month()).isEqualTo(1L);
    }

    @Test
    @DisplayName("비관리자 역할로 summary 조회 시 AccessDeniedException")
    void getPeriodSummary_nonAdminRole_throwsAccessDeniedException() {
        assertThatThrownBy(() -> shippingQueryService.getPeriodSummary("USER"))
                .isInstanceOf(AccessDeniedException.class);
    }
}
