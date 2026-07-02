package com.example.product.application.util;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Computes calendar-period-to-date boundaries in KST (Asia/Seoul).
 * Package-private utility shared by product and seller summary services
 * inside product-service. Not exported to {@code libs/}.
 */
public final class KstPeriodBoundary {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private KstPeriodBoundary() {
    }

    /** Returns pre-computed KST boundaries for the current instant. */
    public static Boundaries now() {
        ZonedDateTime now = ZonedDateTime.now(KST);
        Instant nowInstant     = now.toInstant();
        Instant todayStart     = now.toLocalDate().atStartOfDay(KST).toInstant();
        Instant weekStart      = now.toLocalDate().with(DayOfWeek.MONDAY).atStartOfDay(KST).toInstant();
        Instant monthStart     = now.toLocalDate().withDayOfMonth(1).atStartOfDay(KST).toInstant();
        return new Boundaries(nowInstant, todayStart, weekStart, monthStart);
    }

    /**
     * Immutable snapshot of KST period boundaries.
     *
     * @param now        current instant (exclusive upper bound for all counts)
     * @param todayStart start of today in KST
     * @param weekStart  start of the current ISO week (Monday) in KST
     * @param monthStart start of the current calendar month in KST
     */
    public record Boundaries(Instant now, Instant todayStart, Instant weekStart, Instant monthStart) {
    }
}
