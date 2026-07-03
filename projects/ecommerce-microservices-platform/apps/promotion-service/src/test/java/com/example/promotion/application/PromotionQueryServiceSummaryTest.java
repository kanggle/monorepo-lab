package com.example.promotion.application;

import com.example.common.summary.PeriodSummary;
import com.example.promotion.application.service.PromotionQueryService;
import com.example.promotion.domain.promotion.PromotionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * Unit tests for {@link PromotionQueryService#getPeriodSummary}.
 * Uses a fixed Clock so KST boundary computation is deterministic.
 * No Testcontainers — light service-layer test (TASK-BE-468).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("PromotionQueryService#getPeriodSummary 단위 테스트")
class PromotionQueryServiceSummaryTest {

    @Mock
    private PromotionRepository promotionRepository;

    /**
     * Fixed at 2026-07-03T06:00:00Z = 2026-07-03 15:00 KST (Friday).
     * KST boundaries:
     *   today  start = 2026-07-03T00:00:00+09:00 = 2026-07-02T15:00:00Z
     *   week   start = 2026-06-29T00:00:00+09:00 = 2026-06-28T15:00:00Z  (Monday)
     *   month  start = 2026-07-01T00:00:00+09:00 = 2026-06-30T15:00:00Z
     */
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-07-03T06:00:00Z"),
            ZoneId.of("UTC")
    );

    @Test
    @DisplayName("프로모션이 없으면 모든 카운트가 0이다")
    void getPeriodSummary_empty_returnsAllZeros() {
        PromotionQueryService service = new PromotionQueryService(promotionRepository, clock);

        given(promotionRepository.countAllForTenant()).willReturn(0L);
        given(promotionRepository.countCreatedBetween(any(), any())).willReturn(0L);

        PeriodSummary result = service.getPeriodSummary("ADMIN");

        assertThat(result.total()).isZero();
        assertThat(result.today()).isZero();
        assertThat(result.week()).isZero();
        assertThat(result.month()).isZero();
    }

    @Test
    @DisplayName("오늘 생성된 프로모션이 today 카운트에만 반영된다")
    void getPeriodSummary_todayRow_countedInTodayWeekMonth() {
        PromotionQueryService service = new PromotionQueryService(promotionRepository, clock);

        // total = 5, today boundary counts = 2, week = 4, month = 4
        given(promotionRepository.countAllForTenant()).willReturn(5L);
        // today and month windows both encompass a today-created row
        // Stub: both today and week/month calls return distinct values via argument-based ordering
        // The service calls countCreatedBetween 3 times (today, week, month).
        // Use thenReturn chaining on the mock so each call returns a different value.
        given(promotionRepository.countCreatedBetween(any(), any()))
                .willReturn(2L)   // today call (todayStart, now)
                .willReturn(4L)   // week call  (weekStart, now)
                .willReturn(4L);  // month call (monthStart, now)

        PeriodSummary result = service.getPeriodSummary("ADMIN");

        assertThat(result.total()).isEqualTo(5L);
        assertThat(result.today()).isEqualTo(2L);
        assertThat(result.week()).isEqualTo(4L);
        assertThat(result.month()).isEqualTo(4L);
    }
}
