package com.example.common.time;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

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
}
