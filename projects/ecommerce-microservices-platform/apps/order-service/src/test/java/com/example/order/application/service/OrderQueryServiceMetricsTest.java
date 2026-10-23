package com.example.order.application.service;

import com.example.common.metrics.PerformanceMetrics;
import com.example.common.time.MetricsPeriod;
import com.example.order.domain.repository.OrderRepository;
import com.example.order.domain.repository.PaidOrderMetricRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * Unit test for {@link OrderQueryService#getMetrics} (ADR-MONO-084 P2, TASK-MONO-807):
 * which windows reach the repository, how rows become totals and a KST-dated series.
 * Which orders the rows contain is the query's job — {@code OrderMetricsQueryIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderQueryService.getMetrics() 단위 테스트")
class OrderQueryServiceMetricsTest {

    /** 2026-10-23 01:30 KST (Friday) — the UTC date is still the 22nd. */
    private static final Instant NOW = Instant.parse("2026-10-22T16:30:00Z");

    /** Week: 2026-10-19 00:00 KST → now; previous: 2026-10-12 00:00 KST → 2026-10-16 01:30 KST. */
    private static final Instant WEEK_FROM = Instant.parse("2026-10-18T15:00:00Z");
    private static final Instant PREV_WEEK_FROM = Instant.parse("2026-10-11T15:00:00Z");
    private static final Instant PREV_WEEK_TO = Instant.parse("2026-10-15T16:30:00Z");

    @Mock
    private OrderRepository orderRepository;

    private OrderQueryService service;

    @BeforeEach
    void setUp() {
        service = new OrderQueryService(orderRepository, Clock.fixed(NOW, ZoneId.of("UTC")));
    }

    @Test
    @DisplayName("현재 · 직전 구간의 합계를 내고, 결제 시각의 KST 날짜로 일별을 채운다")
    void totals_and_kst_dated_series() {
        given(orderRepository.findPaidOrderMetrics(WEEK_FROM, NOW)).willReturn(List.of(
                // 2026-10-19 10:00 KST
                new PaidOrderMetricRow(Instant.parse("2026-10-19T01:00:00Z"), 30_000, 2),
                // 2026-10-19 23:59 KST — still the 19th
                new PaidOrderMetricRow(Instant.parse("2026-10-19T14:59:00Z"), 10_000, 1),
                // 2026-10-23 00:10 KST — the UTC date says the 22nd; the series must say the 23rd
                new PaidOrderMetricRow(Instant.parse("2026-10-22T15:10:00Z"), 5_000, 3)));
        given(orderRepository.findPaidOrderMetrics(PREV_WEEK_FROM, PREV_WEEK_TO)).willReturn(List.of(
                new PaidOrderMetricRow(Instant.parse("2026-10-13T03:00:00Z"), 40_000, 4)));

        PerformanceMetrics m = service.getMetrics(MetricsPeriod.WEEK);

        assertThat(m.period()).isEqualTo("week");
        assertThat(m.metrics()).containsExactly(
                new PerformanceMetrics.Metric("revenue", "money", "KRW", "45000", "40000"),
                new PerformanceMetrics.Metric("units", "count", null, "6", "4"),
                new PerformanceMetrics.Metric("orders", "count", null, "3", "1"));

        assertThat(m.series()).extracting(PerformanceMetrics.SeriesPoint::date)
                .containsExactly("2026-10-19", "2026-10-20", "2026-10-21", "2026-10-22", "2026-10-23");
        assertThat(m.series().get(0).values())
                .containsExactly(Map.entry("revenue", "40000"), Map.entry("units", "3"), Map.entry("orders", "2"));
        assertThat(m.series().get(1).values())
                .containsExactly(Map.entry("revenue", "0"), Map.entry("units", "0"), Map.entry("orders", "0"));
        assertThat(m.series().get(3).values().get("orders")).as("nothing on the UTC date").isEqualTo("0");
        assertThat(m.series().get(4).values())
                .containsExactly(Map.entry("revenue", "5000"), Map.entry("units", "3"), Map.entry("orders", "1"));
    }

    @Test
    @DisplayName("아무 주문도 없으면 값은 0 이고 일별은 0 으로 채워진다")
    void empty_is_zero_not_missing() {
        given(orderRepository.findPaidOrderMetrics(WEEK_FROM, NOW)).willReturn(List.of());
        given(orderRepository.findPaidOrderMetrics(PREV_WEEK_FROM, PREV_WEEK_TO)).willReturn(List.of());

        PerformanceMetrics m = service.getMetrics(MetricsPeriod.WEEK);

        assertThat(m.metrics()).extracting(PerformanceMetrics.Metric::current).containsOnly("0");
        assertThat(m.series()).hasSize(5);
        assertThat(m.series()).allSatisfy(p -> assertThat(p.values().values()).containsOnly("0"));
    }

    @Test
    @DisplayName("월 기간은 이번 달 1일부터, 직전은 지난달 같은 길이")
    void month_windows() {
        Instant monthFrom = Instant.parse("2026-09-30T15:00:00Z");      // 2026-10-01 00:00 KST
        Instant prevFrom = Instant.parse("2026-08-31T15:00:00Z");       // 2026-09-01 00:00 KST
        Instant prevTo = Instant.parse("2026-09-22T16:30:00Z");         // 2026-09-23 01:30 KST
        given(orderRepository.findPaidOrderMetrics(monthFrom, NOW)).willReturn(List.of());
        given(orderRepository.findPaidOrderMetrics(prevFrom, prevTo)).willReturn(List.of());

        PerformanceMetrics m = service.getMetrics(MetricsPeriod.MONTH);

        assertThat(m.current().from()).isEqualTo("2026-10-01T00:00:00+09:00");
        assertThat(m.previous().to()).isEqualTo("2026-09-23T01:30:00+09:00");
        assertThat(m.series()).hasSize(23);
    }
}
