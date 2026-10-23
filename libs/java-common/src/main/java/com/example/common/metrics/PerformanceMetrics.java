package com.example.common.metrics;

import com.example.common.time.KstPeriodBounds;
import com.example.common.time.KstWindow;
import com.example.common.time.MetricsPeriod;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The shared performance-metrics response — one shape every domain overview's
 * {@code …/metrics?period=week|month} endpoint returns
 * ({@code platform/contracts/performance-metrics-contract.md}, ADR-MONO-084 D2,
 * TASK-MONO-806). A technical DTO only: which metric keys exist, and what each one
 * counts, is the owning domain's contract, never this library's.
 * <p>
 * Every value is an <b>integer string</b> ({@code "812"}, {@code "12340000"}) — counts
 * and money minor units alike — so a client can render money from the string and never
 * has to turn it into a floating-point number (see the contract's «Values are integer
 * strings»). Serialises as-is with any Jackson setup: strings, records and a {@code null}
 * currency only.
 *
 * @param period   {@code "week"} or {@code "month"}
 * @param timezone the IANA zone the windows and dates are cut in ({@code "Asia/Seoul"})
 * @param current  the period-to-date window
 * @param previous the same-length window it is compared against
 * @param metrics  one entry per metric, keys unique, in display order
 * @param series   one row per KST date of {@code current}, first to last, zero-filled
 */
public record PerformanceMetrics(
        String period,
        String timezone,
        Window current,
        Window previous,
        List<Metric> metrics,
        List<SeriesPoint> series) {

    public static final String UNIT_COUNT = "count";
    public static final String UNIT_MONEY = "money";

    /**
     * A window as it is shown: ISO-8601 with the KST offset, truncated to whole seconds.
     * The query itself runs on the exact {@link KstWindow} — this is display precision.
     */
    public record Window(String from, String to) {
        static Window of(KstWindow w) {
            return new Window(format(w.from()), format(w.to()));
        }

        private static String format(ZonedDateTime t) {
            return t.truncatedTo(ChronoUnit.SECONDS)
                    .toOffsetDateTime()
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
    }

    /**
     * One metric's value over each window.
     *
     * @param key      unique within the response; a domain that reports one metric per
     *                 currency suffixes the key with {@code ":" + currency} ({@code amount:KRW})
     * @param unit     {@link #UNIT_COUNT} or {@link #UNIT_MONEY}
     * @param currency ISO-4217 for money, {@code null} for a count
     * @param current  integer string — the value over {@code current}
     * @param previous integer string — the value over {@code previous}
     */
    public record Metric(String key, String unit, String currency, String current, String previous) {

        public static Metric count(String key, long current, long previous) {
            return new Metric(requireKey(key), UNIT_COUNT, null,
                    Long.toString(current), Long.toString(previous));
        }

        /** Money in integer minor units (KRW: won, USD: cents). */
        public static Metric money(String key, String currency, long currentMinor, long previousMinor) {
            Objects.requireNonNull(currency, "currency");
            if (currency.length() != 3) {
                throw new IllegalArgumentException("currency must be ISO-4217: " + currency);
            }
            return new Metric(requireKey(key), UNIT_MONEY, currency,
                    Long.toString(currentMinor), Long.toString(previousMinor));
        }

        private static String requireKey(String key) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("metric key is required");
            }
            return key;
        }
    }

    /**
     * One KST date's values.
     *
     * @param date   {@code yyyy-MM-dd}
     * @param values metric key → integer string, every key of {@code metrics}, same order
     */
    public record SeriesPoint(String date, Map<String, String> values) {
    }

    /**
     * Builds the response for one period.
     *
     * @param bounds  the boundaries every window and date is derived from
     * @param metrics the metrics, in display order — keys must be unique
     * @param daily   KST date → metric key → value for the days that had any; a missing
     *                date or key becomes {@code "0"}. A date outside the current window or
     *                a key not in {@code metrics} is rejected — it means the query and the
     *                windows disagree, which a silent drop would hide.
     */
    public static PerformanceMetrics of(
            KstPeriodBounds bounds,
            MetricsPeriod period,
            List<Metric> metrics,
            Map<LocalDate, Map<String, Long>> daily) {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(daily, "daily");

        Set<String> keys = new HashSet<>();
        for (Metric m : metrics) {
            if (!keys.add(m.key())) {
                throw new IllegalArgumentException("duplicate metric key: " + m.key());
            }
        }

        List<LocalDate> dates = bounds.currentDates(period);
        Set<LocalDate> dateSet = new HashSet<>(dates);
        daily.forEach((date, values) -> {
            if (!dateSet.contains(date)) {
                throw new IllegalArgumentException("series date outside the current window: " + date);
            }
            for (String k : values.keySet()) {
                if (!keys.contains(k)) {
                    throw new IllegalArgumentException("series key not among the metrics: " + k);
                }
            }
        });

        List<SeriesPoint> series = new ArrayList<>(dates.size());
        for (LocalDate date : dates) {
            Map<String, Long> day = daily.getOrDefault(date, Map.of());
            Map<String, String> values = new LinkedHashMap<>();
            for (Metric m : metrics) {
                values.put(m.key(), Long.toString(day.getOrDefault(m.key(), 0L)));
            }
            series.add(new SeriesPoint(date.toString(), values));
        }

        return new PerformanceMetrics(
                period.wire(),
                KstPeriodBounds.KST.getId(),
                Window.of(bounds.currentWindow(period)),
                Window.of(bounds.previousWindow(period)),
                List.copyOf(metrics),
                List.copyOf(series));
    }
}
