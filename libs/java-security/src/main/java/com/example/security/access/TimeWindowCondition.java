package com.example.security.access;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * ADR-MONO-028 — the {@code TIME_WINDOW} member of the ADR-MONO-026 closed
 * access-condition enum: gate an already-authorised action to requests whose
 * time falls within an allowed local time-of-day / day-of-week window.
 *
 * <p><b>The 2nd condition type</b> (sibling to {@link SourceIpCondition}). It is
 * added as a code change — a new evaluator class in this package + tests — never a
 * runtime registration; that closed-enum boundary is what distinguishes 2단계 from
 * a policy engine (ADR-026 § D1). See {@code platform/access-conditions.md}.
 *
 * <p><b>Carrier = domain/endpoint guard-config (ADR-026 § D3-B).</b> The window is
 * configured by the consuming domain (zone + days + start/end), not carried on a
 * JWT claim — hence this lives under {@code com.example.security.access}. This
 * evaluator is framework-agnostic (raw strings + an {@link Instant}) so any
 * consumer can reuse it.
 *
 * <p><b>Semantics (the three invariants every access condition shares):</b>
 * <ul>
 *   <li><b>Restriction-only</b> — a configured window can only GATE (deny when the
 *       request time is outside it) an action that already passed RBAC +
 *       tenant-scope + data-scope; it never grants.</li>
 *   <li><b>Fail-safe</b> — a missing/unparseable zone, malformed {@code start}/
 *       {@code end}, a non-same-day window ({@code start >= end} — midnight-wrap is
 *       deferred, ADR-028 § D3), or a {@code null} request time yields {@code false}
 *       (deny). A declared-but-invalid window fails closed (matches nothing), it
 *       does not fall open.</li>
 *   <li><b>Net-zero / opt-in</b> — when no window is declared ({@link
 *       #isConfigured()} is {@code false}) there is no gate; {@link
 *       #isSatisfiedBy(Instant)} returns {@code true} for every time, so an
 *       un-configured endpoint behaves exactly as before access-conditioning.</li>
 * </ul>
 *
 * <p>The window is a <b>same-day</b> {@code [start, end)} interval (start inclusive,
 * end exclusive) evaluated in the declared <b>IANA zone</b> (e.g. {@code Asia/Seoul})
 * — DST is handled by {@code java.time}, no manual offset math. A cross-midnight
 * window ({@code end <= start}) is out of scope for the pilot (ADR-028 § D3,
 * fast-follow) and is treated as a misconfiguration (fail-closed).
 */
public final class TimeWindowCondition {

    private final boolean configured;
    private final boolean valid;
    private final ZoneId zone;
    private final Set<DayOfWeek> days;
    private final LocalTime start;
    private final LocalTime end;

    private TimeWindowCondition(boolean configured, boolean valid, ZoneId zone,
                               Set<DayOfWeek> days, LocalTime start, LocalTime end) {
        this.configured = configured;
        this.valid = valid;
        this.zone = zone;
        this.days = days;
        this.start = start;
        this.end = end;
    }

    /**
     * Build a {@code TIME_WINDOW} condition from a domain-configured window.
     *
     * @param zone  IANA zone id (e.g. {@code "Asia/Seoul"}); blank ⇒ undeclared.
     * @param days  allowed days-of-week — full names ({@code "MONDAY"}) or
     *              ≥3-letter abbreviations ({@code "MON"}), case-insensitive;
     *              unparseable entries are dropped (fail-safe — a dropped day only
     *              narrows access).
     * @param start inclusive local start {@code HH:mm} (e.g. {@code "09:00"}).
     * @param end   exclusive local end {@code HH:mm} (e.g. {@code "18:00"}); must be
     *              strictly after {@code start} (same-day; midnight-wrap deferred).
     * @return a never-null condition. It is {@link #isConfigured() configured} iff
     *         the domain declared a window (any field non-blank), even if a field
     *         later proves unparseable — that misconfiguration fail-closes, not
     *         net-zero.
     */
    public static TimeWindowCondition fromConfig(String zone, Collection<String> days,
                                                 String start, String end) {
        boolean declared = isNotBlank(zone) || hasAnyNonBlank(days)
                || isNotBlank(start) || isNotBlank(end);
        if (!declared) {
            return new TimeWindowCondition(false, false, null,
                    EnumSet.noneOf(DayOfWeek.class), null, null);
        }
        ZoneId z = parseZoneOrNull(zone);
        Set<DayOfWeek> parsedDays = parseDays(days);
        LocalTime s = parseTimeOrNull(start);
        LocalTime e = parseTimeOrNull(end);
        // Same-day window only: start strictly before end. zone/start/end must parse.
        boolean valid = z != null && s != null && e != null && s.isBefore(e);
        return new TimeWindowCondition(true, valid, z, parsedDays, s, e);
    }

    /**
     * {@code true} iff the domain declared a window — i.e. the gate is active. When
     * {@code false} the condition is net-zero (no gate); callers MUST short-circuit
     * on this before denying.
     */
    public boolean isConfigured() {
        return configured;
    }

    /**
     * Whether {@code requestTime} satisfies the window. {@code true} when the
     * condition is unconfigured (net-zero) or the request's local date-time (in the
     * declared zone) falls on an allowed day AND within {@code [start, end)};
     * {@code false} (fail-safe deny) when configured and the window is invalid, the
     * time is {@code null}, the day is not allowed, or the time is outside the
     * interval.
     */
    public boolean isSatisfiedBy(Instant requestTime) {
        if (!configured) {
            return true;
        }
        if (!valid || requestTime == null) {
            return false;
        }
        ZonedDateTime zdt = requestTime.atZone(zone);
        if (!days.contains(zdt.getDayOfWeek())) {
            return false;
        }
        LocalTime t = zdt.toLocalTime();
        return !t.isBefore(start) && t.isBefore(end);
    }

    // ── parsing helpers (all fail-safe: return null / drop on bad input) ──────────

    private static ZoneId parseZoneOrNull(String zone) {
        if (!isNotBlank(zone)) {
            return null;
        }
        try {
            return ZoneId.of(zone.trim());
        } catch (RuntimeException ex) { // ZoneRulesException / DateTimeException
            return null;
        }
    }

    private static LocalTime parseTimeOrNull(String time) {
        if (!isNotBlank(time)) {
            return null;
        }
        try {
            return LocalTime.parse(time.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    /** Parse the allowed days, dropping blanks/unparseable entries (fail-safe). */
    private static Set<DayOfWeek> parseDays(Collection<String> days) {
        Set<DayOfWeek> out = EnumSet.noneOf(DayOfWeek.class);
        if (days == null) {
            return out;
        }
        for (String raw : days) {
            DayOfWeek d = parseDayOrNull(raw);
            if (d != null) {
                out.add(d);
            }
        }
        return out;
    }

    /**
     * Parse one day token: full name ({@code MONDAY}) or a ≥3-letter prefix
     * ({@code MON} / {@code MOND}), case-insensitive. The 3-letter English
     * abbreviations are unique, so a prefix match is unambiguous. Returns
     * {@code null} on a blank / unrecognised token.
     */
    private static DayOfWeek parseDayOrNull(String raw) {
        if (!isNotBlank(raw)) {
            return null;
        }
        String u = raw.trim().toUpperCase(Locale.ROOT);
        if (u.length() < 3) {
            return null; // too short to disambiguate (e.g. "S" = SAT|SUN)
        }
        for (DayOfWeek d : DayOfWeek.values()) {
            if (d.name().startsWith(u)) {
                return d;
            }
        }
        return null;
    }

    private static boolean hasAnyNonBlank(Collection<String> values) {
        if (values == null) {
            return false;
        }
        for (String v : values) {
            if (isNotBlank(v)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
