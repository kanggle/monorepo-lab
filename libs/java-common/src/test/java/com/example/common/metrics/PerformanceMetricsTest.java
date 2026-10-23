package com.example.common.metrics;

import com.example.common.time.KstPeriodBounds;
import com.example.common.time.MetricsPeriod;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PerformanceMetricsTest {

    /** 2026-07-04 21:00:00.789 KST — the fraction must not reach the wire. */
    private static final KstPeriodBounds BOUNDS = KstPeriodBounds.from(
            Clock.fixed(Instant.parse("2026-07-04T12:00:00.789Z"), ZoneId.of("UTC")));

    private static final List<PerformanceMetrics.Metric> METRICS = List.of(
            PerformanceMetrics.Metric.money("amount", "KRW", 12_340_000L, 11_020_000L),
            PerformanceMetrics.Metric.count("items", 812, 790));

    @Test
    void series_is_zero_filled_for_every_date_and_every_key_in_metric_order() {
        PerformanceMetrics m = PerformanceMetrics.of(BOUNDS, MetricsPeriod.MONTH, METRICS, Map.of(
                LocalDate.parse("2026-07-02"), Map.of("items", 5L)));

        assertThat(m.series()).extracting(PerformanceMetrics.SeriesPoint::date)
                .containsExactly("2026-07-01", "2026-07-02", "2026-07-03", "2026-07-04");
        assertThat(m.series().get(0).values()).containsExactly(Map.entry("amount", "0"), Map.entry("items", "0"));
        assertThat(m.series().get(1).values()).containsExactly(Map.entry("amount", "0"), Map.entry("items", "5"));
    }

    @Test
    void header_fields_and_windows_use_the_wire_format() {
        PerformanceMetrics m = PerformanceMetrics.of(BOUNDS, MetricsPeriod.WEEK, METRICS, Map.of());

        assertThat(m.period()).isEqualTo("week");
        assertThat(m.timezone()).isEqualTo("Asia/Seoul");
        assertThat(m.current().from()).isEqualTo("2026-06-29T00:00:00+09:00");
        assertThat(m.current().to()).as("truncated to whole seconds").isEqualTo("2026-07-04T21:00:00+09:00");
        assertThat(m.metrics()).extracting(PerformanceMetrics.Metric::key).containsExactly("amount", "items");
    }

    @Test
    void values_are_integer_strings_and_currency_is_only_on_money() {
        assertThat(METRICS.get(0)).isEqualTo(
                new PerformanceMetrics.Metric("amount", "money", "KRW", "12340000", "11020000"));
        assertThat(METRICS.get(1)).isEqualTo(
                new PerformanceMetrics.Metric("items", "count", null, "812", "790"));
        assertThat(PerformanceMetrics.Metric.money("refunds", "USD", -150, 0).current()).isEqualTo("-150");
    }

    @Test
    void rejects_what_would_otherwise_be_dropped_silently() {
        assertThatThrownBy(() -> PerformanceMetrics.of(BOUNDS, MetricsPeriod.WEEK,
                List.of(PerformanceMetrics.Metric.count("a", 1, 1), PerformanceMetrics.Metric.count("a", 2, 2)),
                Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate metric key");

        assertThatThrownBy(() -> PerformanceMetrics.of(BOUNDS, MetricsPeriod.WEEK, METRICS,
                Map.of(LocalDate.parse("2026-06-28"), Map.of("items", 1L))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("outside the current window");

        assertThatThrownBy(() -> PerformanceMetrics.of(BOUNDS, MetricsPeriod.WEEK, METRICS,
                Map.of(LocalDate.parse("2026-07-01"), Map.of("other", 1L))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not among the metrics");

        assertThatThrownBy(() -> PerformanceMetrics.Metric.money("x", "WON!", 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PerformanceMetrics.Metric.count(" ", 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** The record serialises to the contract shape with a default ObjectMapper — no annotations needed. */
    @Test
    void serialises_to_the_contract_shape() throws Exception {
        PerformanceMetrics m = PerformanceMetrics.of(BOUNDS, MetricsPeriod.WEEK, METRICS, Map.of());
        JsonNode json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(m));

        assertThat(json.fieldNames()).toIterable()
                .containsExactly("period", "timezone", "current", "previous", "metrics", "series");
        assertThat(json.at("/metrics/0/current").isTextual()).as("money is a string, never a JSON number").isTrue();
        assertThat(json.at("/metrics/1/currency").isNull()).isTrue();
        assertThat(json.at("/series/0/date").asText()).isEqualTo("2026-06-29");
        assertThat(json.at("/series/0/values/amount").asText()).isEqualTo("0");
    }
}
