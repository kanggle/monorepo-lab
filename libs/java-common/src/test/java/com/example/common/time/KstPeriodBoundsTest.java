package com.example.common.time;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Control for TASK-MONO-806 AC-3: the accessors the ecommerce {@code …/summary}
 * endpoints already use return exactly what they returned before the
 * performance-metrics windows were added — pinned values, not re-derived ones.
 */
class KstPeriodBoundsTest {

    /** 2026-07-04 21:00 KST, a Saturday. */
    private static final Instant SAT_EVENING = Instant.parse("2026-07-04T12:00:00Z");

    @Test
    void existing_accessors_are_unchanged() {
        KstPeriodBounds b = KstPeriodBounds.from(Clock.fixed(SAT_EVENING, ZoneId.of("UTC")));

        assertThat(b.nowInstant()).isEqualTo(SAT_EVENING);
        assertThat(b.todayStartInstant()).isEqualTo(Instant.parse("2026-07-03T15:00:00Z"));
        assertThat(b.weekStartInstant()).isEqualTo(Instant.parse("2026-06-28T15:00:00Z"));
        assertThat(b.monthStartInstant()).isEqualTo(Instant.parse("2026-06-30T15:00:00Z"));

        assertThat(b.nowLocal()).isEqualTo(LocalDateTime.parse("2026-07-04T21:00:00"));
        assertThat(b.todayStartLocal()).isEqualTo(LocalDateTime.parse("2026-07-04T00:00:00"));
        assertThat(b.weekStartLocal()).isEqualTo(LocalDateTime.parse("2026-06-29T00:00:00"));
        assertThat(b.monthStartLocal()).isEqualTo(LocalDateTime.parse("2026-07-01T00:00:00"));
    }

    @Test
    void current_window_starts_where_the_existing_accessors_say() {
        KstPeriodBounds b = KstPeriodBounds.from(Clock.fixed(SAT_EVENING, ZoneId.of("UTC")));

        assertThat(b.currentWindow(MetricsPeriod.WEEK).fromInstant()).isEqualTo(b.weekStartInstant());
        assertThat(b.currentWindow(MetricsPeriod.MONTH).fromInstant()).isEqualTo(b.monthStartInstant());
        assertThat(b.currentWindow(MetricsPeriod.WEEK).toInstant()).isEqualTo(b.nowInstant());
        assertThat(b.currentWindow(MetricsPeriod.MONTH).fromLocal()).isEqualTo(b.monthStartLocal());
    }

    /** AC-2 at the JVM level: the host's default zone does not move any boundary. */
    @ParameterizedTest
    @ValueSource(strings = {"UTC", "Asia/Seoul", "America/Los_Angeles", "Pacific/Kiritimati"})
    void jvm_default_zone_does_not_move_the_boundaries(String zone) {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            KstPeriodBounds b = KstPeriodBounds.from(Clock.fixed(SAT_EVENING, ZoneId.systemDefault()));
            assertThat(b.weekStartInstant()).isEqualTo(Instant.parse("2026-06-28T15:00:00Z"));
            assertThat(b.previousWindow(MetricsPeriod.MONTH).toLocal())
                    .isEqualTo(LocalDateTime.parse("2026-06-04T21:00:00"));
            assertThat(b.currentDates(MetricsPeriod.WEEK)).hasSize(6);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void period_parses_case_insensitively_and_rejects_anything_else() {
        assertThat(MetricsPeriod.parse("week")).isEqualTo(MetricsPeriod.WEEK);
        assertThat(MetricsPeriod.parse(" MONTH ")).isEqualTo(MetricsPeriod.MONTH);
        assertThatThrownBy(() -> MetricsPeriod.parse("today")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MetricsPeriod.parse("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MetricsPeriod.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
