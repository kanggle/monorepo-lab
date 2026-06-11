package com.example.security.access;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-MONO-028 — {@code TIME_WINDOW} access condition (closed-enum, fail-safe, opt-in).
 *
 * <p>Weekday anchors used below (UTC): 2026-06-08 = Monday, 2026-06-10 = Wednesday,
 * 2026-06-13 = Saturday. Asia/Seoul is UTC+09:00 with no DST.
 */
@DisplayName("TimeWindowCondition — ADR-MONO-028 TIME_WINDOW access condition")
class TimeWindowConditionTest {

    private static final List<String> WEEKDAYS = List.of("MON", "TUE", "WED", "THU", "FRI");

    @Test
    @DisplayName("net-zero: no window declared ⟹ not configured, every time satisfies (no gate)")
    void unconfiguredIsNetZero() {
        TimeWindowCondition none = TimeWindowCondition.fromConfig(null, null, null, null);
        assertThat(none.isConfigured()).isFalse();
        assertThat(none.isSatisfiedBy(Instant.parse("2026-06-13T03:00:00Z"))).isTrue(); // Sat 03:00

        TimeWindowCondition blanks = TimeWindowCondition.fromConfig("  ", Arrays.asList("", null, "  "), "", "");
        assertThat(blanks.isConfigured()).isFalse();
        assertThat(blanks.isSatisfiedBy(Instant.parse("2026-06-13T03:00:00Z"))).isTrue();
    }

    @Test
    @DisplayName("in-window weekday + time ⟹ satisfied; outside the day or interval ⟹ denied (UTC)")
    void windowMatchUtc() {
        TimeWindowCondition c = TimeWindowCondition.fromConfig("UTC", WEEKDAYS, "09:00", "18:00");
        assertThat(c.isConfigured()).isTrue();
        // Wed 2026-06-10
        assertThat(c.isSatisfiedBy(Instant.parse("2026-06-10T12:00:00Z"))).isTrue();  // mid-window
        assertThat(c.isSatisfiedBy(Instant.parse("2026-06-10T09:00:00Z"))).isTrue();  // start inclusive
        assertThat(c.isSatisfiedBy(Instant.parse("2026-06-10T17:59:59Z"))).isTrue();  // just inside end
        assertThat(c.isSatisfiedBy(Instant.parse("2026-06-10T08:59:59Z"))).isFalse(); // before start
        assertThat(c.isSatisfiedBy(Instant.parse("2026-06-10T18:00:00Z"))).isFalse(); // end exclusive
        // Sat 2026-06-13 — in-window time but not an allowed day
        assertThat(c.isSatisfiedBy(Instant.parse("2026-06-13T12:00:00Z"))).isFalse();
    }

    @Test
    @DisplayName("the window is evaluated in the declared IANA zone (Asia/Seoul = UTC+9)")
    void windowEvaluatedInZone() {
        TimeWindowCondition c = TimeWindowCondition.fromConfig("Asia/Seoul", WEEKDAYS, "09:00", "18:00");
        // 00:30Z = Seoul 09:30 Wed → in window
        assertThat(c.isSatisfiedBy(Instant.parse("2026-06-10T00:30:00Z"))).isTrue();
        // 23:30Z (Tue) = Seoul 08:30 Wed → before start (also proves the date rolls in-zone)
        assertThat(c.isSatisfiedBy(Instant.parse("2026-06-09T23:30:00Z"))).isFalse();
        // 09:00Z = Seoul 18:00 Wed → end exclusive
        assertThat(c.isSatisfiedBy(Instant.parse("2026-06-10T09:00:00Z"))).isFalse();
    }

    @Test
    @DisplayName("day tokens: full names + ≥3-letter abbreviations, case-insensitive")
    void dayTokenParsing() {
        TimeWindowCondition c = TimeWindowCondition.fromConfig(
                "UTC", Arrays.asList("mon", "Tue", "WEDNESDAY", " fri "), "00:00", "23:00");
        assertThat(c.isSatisfiedBy(Instant.parse("2026-06-08T12:00:00Z"))).isTrue();  // Mon
        assertThat(c.isSatisfiedBy(Instant.parse("2026-06-10T12:00:00Z"))).isTrue();  // Wed
        assertThat(c.isSatisfiedBy(Instant.parse("2026-06-11T12:00:00Z"))).isFalse(); // Thu (not listed)
        assertThat(c.isSatisfiedBy(Instant.parse("2026-06-13T12:00:00Z"))).isFalse(); // Sat
    }

    @Test
    @DisplayName("partially-valid days: unparseable tokens dropped, valid ones still gate")
    void partiallyValidDays() {
        TimeWindowCondition c = TimeWindowCondition.fromConfig(
                "UTC", Arrays.asList("MON", "garbage", "S", "FRI"), "00:00", "23:00");
        assertThat(c.isConfigured()).isTrue();
        assertThat(c.isSatisfiedBy(Instant.parse("2026-06-08T12:00:00Z"))).isTrue();  // Mon
        assertThat(c.isSatisfiedBy(Instant.parse("2026-06-10T12:00:00Z"))).isFalse(); // Wed (dropped tokens never added it)
    }

    @Test
    @DisplayName("fail-safe: configured but null/invalid time ⟹ denied")
    void failSafeOnNullTime() {
        TimeWindowCondition c = TimeWindowCondition.fromConfig("UTC", WEEKDAYS, "09:00", "18:00");
        assertThat(c.isSatisfiedBy(null)).isFalse();
    }

    @Test
    @DisplayName("fail-closed: configured but invalid window (bad zone / bad time / non-same-day) matches nothing")
    void failClosedOnInvalidWindow() {
        // unparseable zone
        TimeWindowCondition badZone = TimeWindowCondition.fromConfig("Not/AZone", WEEKDAYS, "09:00", "18:00");
        assertThat(badZone.isConfigured()).isTrue();
        assertThat(badZone.isSatisfiedBy(Instant.parse("2026-06-10T12:00:00Z"))).isFalse();

        // unparseable start time
        TimeWindowCondition badTime = TimeWindowCondition.fromConfig("UTC", WEEKDAYS, "9am", "18:00");
        assertThat(badTime.isConfigured()).isTrue();
        assertThat(badTime.isSatisfiedBy(Instant.parse("2026-06-10T12:00:00Z"))).isFalse();

        // non-same-day (start >= end) — midnight-wrap is deferred (ADR-028 § D3)
        TimeWindowCondition wrap = TimeWindowCondition.fromConfig("UTC", WEEKDAYS, "22:00", "06:00");
        assertThat(wrap.isConfigured()).isTrue();
        assertThat(wrap.isSatisfiedBy(Instant.parse("2026-06-10T23:00:00Z"))).isFalse();
    }

    @Test
    @DisplayName("fail-closed: configured window with no valid days never matches")
    void failClosedOnNoDays() {
        TimeWindowCondition c = TimeWindowCondition.fromConfig("UTC", List.of(), "09:00", "18:00");
        assertThat(c.isConfigured()).isTrue(); // declared via zone/start/end
        assertThat(c.isSatisfiedBy(Instant.parse("2026-06-10T12:00:00Z"))).isFalse();
    }
}
