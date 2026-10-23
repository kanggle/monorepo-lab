package com.example.common.time;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * A half-open KST time window {@code [from, to)} — one of the two windows a
 * performance-metrics response compares (ADR-MONO-084 D2). Exposes both
 * {@link Instant} and naive {@link LocalDateTime} accessors, like
 * {@link KstPeriodBounds}, so an entity of either timestamp type can query it.
 */
public record KstWindow(ZonedDateTime from, ZonedDateTime to) {

    public KstWindow {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to must not be before from: " + from + " > " + to);
        }
    }

    public Instant fromInstant() {
        return from.toInstant();
    }

    public Instant toInstant() {
        return to.toInstant();
    }

    public LocalDateTime fromLocal() {
        return from.withZoneSameInstant(KstPeriodBounds.KST).toLocalDateTime();
    }

    public LocalDateTime toLocal() {
        return to.withZoneSameInstant(KstPeriodBounds.KST).toLocalDateTime();
    }
}
