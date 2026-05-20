package com.example.erp.masterdata.domain.effectivedate;

import com.example.erp.masterdata.domain.error.DomainErrors.MasterdataEffectivePeriodInvalidException;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Effective-dating value object (erp E2). The interval is
 * {@code [effectiveFrom, effectiveTo)} — start inclusive, end exclusive. An
 * open-ended period has {@code effectiveTo == null}.
 *
 * <p>Pure Java — no framework imports. Validates {@code start <= end} on
 * construction; pairwise overlap detection lives in {@link #overlapsWith}.
 *
 * <p>architecture.md § Effective-dating model — overlap of revisions for the
 * same natural key is rejected with {@code MASTERDATA_EFFECTIVE_PERIOD_INVALID}.
 */
public record EffectivePeriod(LocalDate effectiveFrom, LocalDate effectiveTo) {

    public EffectivePeriod {
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
            throw new MasterdataEffectivePeriodInvalidException(
                    "effectiveTo (" + effectiveTo + ") must be strictly after effectiveFrom ("
                            + effectiveFrom + ")");
        }
    }

    public static EffectivePeriod openEnded(LocalDate from) {
        return new EffectivePeriod(from, null);
    }

    public static EffectivePeriod closed(LocalDate from, LocalDate to) {
        return new EffectivePeriod(from, to);
    }

    /** True iff {@code asOf} falls within {@code [effectiveFrom, effectiveTo)}. */
    public boolean contains(LocalDate asOf) {
        Objects.requireNonNull(asOf, "asOf");
        if (asOf.isBefore(effectiveFrom)) {
            return false;
        }
        return effectiveTo == null || asOf.isBefore(effectiveTo);
    }

    /** True iff this period and {@code other} share any day. */
    public boolean overlapsWith(EffectivePeriod other) {
        Objects.requireNonNull(other, "other");
        // [a,b) overlaps [c,d) iff a < d AND c < b
        boolean thisStartsBeforeOtherEnds = other.effectiveTo == null
                || effectiveFrom.isBefore(other.effectiveTo);
        boolean otherStartsBeforeThisEnds = effectiveTo == null
                || other.effectiveFrom.isBefore(effectiveTo);
        return thisStartsBeforeOtherEnds && otherStartsBeforeThisEnds;
    }

    public EffectivePeriod closeAt(LocalDate boundary) {
        Objects.requireNonNull(boundary, "boundary");
        if (!boundary.isAfter(effectiveFrom)) {
            throw new MasterdataEffectivePeriodInvalidException(
                    "Close boundary (" + boundary + ") must be strictly after effectiveFrom ("
                            + effectiveFrom + ")");
        }
        return new EffectivePeriod(effectiveFrom, boundary);
    }
}
