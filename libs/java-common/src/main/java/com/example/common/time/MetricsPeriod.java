package com.example.common.time;

import java.util.Locale;

/**
 * The reporting period of a performance-metrics response
 * ({@code platform/contracts/performance-metrics-contract.md}, ADR-MONO-084 D2).
 * Both are calendar-period-to-date in KST: {@link #WEEK} from Monday 00:00,
 * {@link #MONTH} from the 1st 00:00, each through now.
 */
public enum MetricsPeriod {
    WEEK("week"),
    MONTH("month");

    private final String wire;

    MetricsPeriod(String wire) {
        this.wire = wire;
    }

    /** The value as it appears on the wire ({@code ?period=} and the response {@code period}). */
    public String wire() {
        return wire;
    }

    /**
     * Parses a {@code ?period=} value, case-insensitively.
     *
     * @throws IllegalArgumentException when the value is null, blank or not a known period —
     *         the caller maps this to its own 400 error code.
     */
    public static MetricsPeriod parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("period is required (week|month)");
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        for (MetricsPeriod p : values()) {
            if (p.wire.equals(v)) {
                return p;
            }
        }
        throw new IllegalArgumentException("unknown period: " + value + " (week|month)");
    }
}
