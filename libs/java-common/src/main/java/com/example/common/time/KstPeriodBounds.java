package com.example.common.time;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * KST ({@code Asia/Seoul}) calendar-period-to-date boundaries: the start-of-day for
 * today, the current ISO week (Monday), and the current month, plus "now" — computed
 * once against the Korean business calendar regardless of the server's own timezone.
 * <p>
 * Shared by the ecommerce operator-overview {@code .../summary} endpoints
 * (TASK-MONO-322) so the boundary rule lives in one place instead of being duplicated
 * per service. Exposes both {@link Instant} accessors (for entities whose
 * {@code createdAt} is an {@code Instant}) and {@link LocalDateTime} accessors (for
 * entities whose {@code createdAt} is a naive {@code LocalDateTime}, e.g. notification
 * templates). Range is half-open at the period start through {@link #nowInstant()} /
 * {@link #nowLocal()}.
 * <p>
 * Also the single source of the performance-metrics windows (ADR-MONO-084 D3,
 * TASK-MONO-806): {@link #currentWindow}, {@link #previousWindow} and
 * {@link #currentDates}. A service must not cut its own KST (or UTC) days.
 */
public final class KstPeriodBounds {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ZonedDateTime now;
    private final ZonedDateTime todayStart;
    private final ZonedDateTime weekStart;
    private final ZonedDateTime monthStart;

    private KstPeriodBounds(ZonedDateTime nowKst) {
        this.now = nowKst;
        this.todayStart = nowKst.toLocalDate().atStartOfDay(KST);
        this.weekStart = nowKst.toLocalDate().with(DayOfWeek.MONDAY).atStartOfDay(KST);
        this.monthStart = nowKst.toLocalDate().withDayOfMonth(1).atStartOfDay(KST);
    }

    /** Boundaries relative to the current instant, in KST. */
    public static KstPeriodBounds now() {
        return new KstPeriodBounds(ZonedDateTime.now(KST));
    }

    /** Boundaries relative to the given {@link Clock} (for deterministic tests / injected clocks). */
    public static KstPeriodBounds from(Clock clock) {
        return new KstPeriodBounds(ZonedDateTime.now(clock).withZoneSameInstant(KST));
    }

    public Instant nowInstant() {
        return now.toInstant();
    }

    public Instant todayStartInstant() {
        return todayStart.toInstant();
    }

    public Instant weekStartInstant() {
        return weekStart.toInstant();
    }

    public Instant monthStartInstant() {
        return monthStart.toInstant();
    }

    public LocalDateTime nowLocal() {
        return now.toLocalDateTime();
    }

    public LocalDateTime todayStartLocal() {
        return todayStart.toLocalDateTime();
    }

    public LocalDateTime weekStartLocal() {
        return weekStart.toLocalDateTime();
    }

    public LocalDateTime monthStartLocal() {
        return monthStart.toLocalDateTime();
    }

    // ---- performance-metrics windows (ADR-MONO-084 D2 · D3, TASK-MONO-806) ----

    /** The current period-to-date window: the period start through now. */
    public KstWindow currentWindow(MetricsPeriod period) {
        return new KstWindow(periodStart(period), now);
    }

    /**
     * The window the current one is compared against: the previous period's start plus
     * the same elapsed time — this month's 1st–23rd 14:05 is compared with last month's
     * 1st–23rd 14:05, never with the whole previous month (mid-month it would always read
     * as a drop). For {@link MetricsPeriod#MONTH} the window stops at the end of the
     * previous month when that month is shorter (on Mar 30 it is Feb 1 → Mar 1).
     * KST has no daylight saving, so «the same elapsed time» is also the same wall clock.
     */
    public KstWindow previousWindow(MetricsPeriod period) {
        ZonedDateTime start = periodStart(period);
        ZonedDateTime previousStart = switch (period) {
            case WEEK -> start.minusWeeks(1);
            case MONTH -> start.minusMonths(1);
        };
        ZonedDateTime previousTo = previousStart.plus(Duration.between(start, now));
        if (previousTo.isAfter(start)) {
            previousTo = start;
        }
        return new KstWindow(previousStart, previousTo);
    }

    /**
     * The KST calendar dates of the current window, first to last, both inclusive — one
     * entry per {@code series} row of the response. Never empty: at the very first instant
     * of a period it holds that one day.
     */
    public List<LocalDate> currentDates(MetricsPeriod period) {
        LocalDate first = periodStart(period).toLocalDate();
        LocalDate last = now.toLocalDate();
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            dates.add(d);
        }
        return dates;
    }

    private ZonedDateTime periodStart(MetricsPeriod period) {
        return switch (period) {
            case WEEK -> weekStart;
            case MONTH -> monthStart;
        };
    }
}
