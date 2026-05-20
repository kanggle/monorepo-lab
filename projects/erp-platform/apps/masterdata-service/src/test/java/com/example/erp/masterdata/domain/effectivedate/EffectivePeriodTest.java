package com.example.erp.masterdata.domain.effectivedate;

import com.example.erp.masterdata.domain.error.DomainErrors.MasterdataEffectivePeriodInvalidException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Domain unit — pure {@link EffectivePeriod} invariants (erp E2). */
class EffectivePeriodTest {

    @Test
    @DisplayName("E2: effectiveTo <= effectiveFrom is rejected")
    void invertedPeriodRejected() {
        assertThatThrownBy(() -> new EffectivePeriod(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1)))
                .isInstanceOf(MasterdataEffectivePeriodInvalidException.class);
        assertThatThrownBy(() -> new EffectivePeriod(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 1, 1)))
                .isInstanceOf(MasterdataEffectivePeriodInvalidException.class);
    }

    @Test
    @DisplayName("E2: open-ended period accepts any future asOf")
    void openEndedContains() {
        EffectivePeriod p = EffectivePeriod.openEnded(LocalDate.of(2026, 1, 1));
        assertThat(p.contains(LocalDate.of(2026, 1, 1))).isTrue();
        assertThat(p.contains(LocalDate.of(2099, 12, 31))).isTrue();
        assertThat(p.contains(LocalDate.of(2025, 12, 31))).isFalse();
    }

    @Test
    @DisplayName("E2: closed period contains start, excludes end")
    void closedPeriodHalfOpen() {
        EffectivePeriod p = EffectivePeriod.closed(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertThat(p.contains(LocalDate.of(2026, 1, 1))).isTrue();
        assertThat(p.contains(LocalDate.of(2026, 6, 15))).isTrue();
        assertThat(p.contains(LocalDate.of(2026, 12, 30))).isTrue();
        // end is exclusive
        assertThat(p.contains(LocalDate.of(2026, 12, 31))).isFalse();
    }

    @Test
    @DisplayName("E2: overlap detection — adjacent periods do NOT overlap")
    void adjacentDoesNotOverlap() {
        EffectivePeriod a = EffectivePeriod.closed(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1));
        EffectivePeriod b = EffectivePeriod.closed(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 12, 31));
        assertThat(a.overlapsWith(b)).isFalse();
        assertThat(b.overlapsWith(a)).isFalse();
    }

    @Test
    @DisplayName("E2: overlap detection — overlapping closed periods")
    void overlappingClosed() {
        EffectivePeriod a = EffectivePeriod.closed(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 2));
        EffectivePeriod b = EffectivePeriod.closed(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 12, 31));
        assertThat(a.overlapsWith(b)).isTrue();
    }

    @Test
    @DisplayName("E2: overlap detection — open-ended swallows everything after")
    void openEndedOverlaps() {
        EffectivePeriod open = EffectivePeriod.openEnded(LocalDate.of(2026, 1, 1));
        EffectivePeriod future = EffectivePeriod.closed(
                LocalDate.of(2099, 1, 1), LocalDate.of(2099, 12, 31));
        assertThat(open.overlapsWith(future)).isTrue();
    }
}
