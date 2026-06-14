package com.example.finance.ledger.domain.reconciliation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link FxTolerance} value object (13th increment —
 * TASK-FIN-BE-020): the looser-of band, half-up rounding on the bps term, the
 * inclusive ({@code <=}) edge, and {@code EXACT} ⟺ exact equality.
 */
class FxToleranceTest {

    @Test
    @DisplayName("EXACT — within iff expected == actual (band is max(0,0) == 0)")
    void exactIsExactEquality() {
        FxTolerance exact = FxTolerance.EXACT;
        assertThat(exact.toleranceBps()).isZero();
        assertThat(exact.absoluteFloorMinor()).isZero();

        assertThat(exact.isWithinTolerance(130_000L, 130_000L)).isTrue();
        assertThat(exact.isWithinTolerance(130_000L, 130_001L)).isFalse();
        assertThat(exact.isWithinTolerance(130_000L, 129_999L)).isFalse();
        assertThat(exact.isWithinTolerance(0L, 0L)).isTrue();
        assertThat(exact.isWithinTolerance(0L, 1L)).isFalse();
    }

    @Test
    @DisplayName("bps band — 100 bps of 130000 = 1300; within / at-edge / above")
    void bpsBand() {
        FxTolerance tol = new FxTolerance(100, 0L); // 1% of magnitude

        assertThat(tol.isWithinTolerance(130_000L, 131_200L)).isTrue();   // diff 1200 < 1300
        assertThat(tol.isWithinTolerance(130_000L, 131_300L)).isTrue();   // diff 1300 == band (inclusive)
        assertThat(tol.isWithinTolerance(130_000L, 128_700L)).isTrue();   // diff 1300 (negative side)
        assertThat(tol.isWithinTolerance(130_000L, 131_301L)).isFalse();  // diff 1301 > 1300
    }

    @Test
    @DisplayName("bps term rounds HALF_UP — 5 bps of 12345 = 6.1725 → 6")
    void bpsHalfUpRounding() {
        // 5 bps of 12345 = 12345 * 5 / 10000 = 6.1725 → HALF_UP → 6.
        FxTolerance tol = new FxTolerance(5, 0L);
        assertThat(tol.isWithinTolerance(12_345L, 12_351L)).isTrue();  // diff 6 == band
        assertThat(tol.isWithinTolerance(12_345L, 12_352L)).isFalse(); // diff 7 > 6

        // 5 bps of 13000 = 6.5 → HALF_UP → 7.
        FxTolerance tol2 = new FxTolerance(5, 0L);
        assertThat(tol2.isWithinTolerance(13_000L, 13_007L)).isTrue();  // diff 7 == band
        assertThat(tol2.isWithinTolerance(13_000L, 13_008L)).isFalse(); // diff 8 > 7
    }

    @Test
    @DisplayName("looser-of — floor wins on a small amount, bps wins on a large amount")
    void looserOfBand() {
        FxTolerance tol = new FxTolerance(100, 50L); // 1% OR 50 minor, whichever is larger

        // Small amount: 100 bps of 1000 = 10; floor 50 is looser. diff 50 within, 51 out.
        assertThat(tol.isWithinTolerance(1_000L, 1_050L)).isTrue();
        assertThat(tol.isWithinTolerance(1_000L, 1_051L)).isFalse();

        // Large amount: 100 bps of 1000000 = 10000; bps is looser. diff 10000 within, 10001 out.
        assertThat(tol.isWithinTolerance(1_000_000L, 1_010_000L)).isTrue();
        assertThat(tol.isWithinTolerance(1_000_000L, 1_010_001L)).isFalse();
    }

    @Test
    @DisplayName("floor-only — band is the flat floor regardless of magnitude")
    void floorOnly() {
        FxTolerance tol = new FxTolerance(0, 100L);
        assertThat(tol.isWithinTolerance(5L, 105L)).isTrue();          // diff 100 == floor
        assertThat(tol.isWithinTolerance(5L, 106L)).isFalse();         // diff 101 > 100
        assertThat(tol.isWithinTolerance(9_999_999L, 9_999_999L + 100L)).isTrue();
    }

    @Test
    @DisplayName("band uses |expected| — a zero expected with a floor still bands by the floor")
    void zeroExpectedWithFloor() {
        FxTolerance tol = new FxTolerance(100, 10L);
        // bps of 0 = 0; floor 10 is looser.
        assertThat(tol.isWithinTolerance(0L, 10L)).isTrue();
        assertThat(tol.isWithinTolerance(0L, 11L)).isFalse();
    }

    @Test
    @DisplayName("negative bps / floor → IllegalArgumentException (the use case maps to VALIDATION_ERROR)")
    void negativeRejected() {
        assertThatThrownBy(() -> new FxTolerance(-1, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FxTolerance(0, -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
