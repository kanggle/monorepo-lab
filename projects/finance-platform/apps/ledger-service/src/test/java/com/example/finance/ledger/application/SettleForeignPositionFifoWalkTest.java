package com.example.finance.ledger.application;

import com.example.finance.ledger.application.SettleForeignPositionUseCase.FifoWalk;
import com.example.finance.ledger.domain.account.LedgerAccountCodes;
import com.example.finance.ledger.domain.journal.FxPositionLot;
import com.example.finance.ledger.domain.money.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for the FIFO lot-walk arithmetic ({@link SettleForeignPositionUseCase#walkFifo}
 * — 17th increment, TASK-FIN-BE-025, ADR-001 D3). Pure (no Spring / no repository): proves
 * oldest-first consumption, per-lot HALF_UP slice rounding, the drift-free full-lot slice,
 * partial last-lot consumption, exact-cover, and the shortfall ({@code null}) signal that
 * drives the safe weighted-average fallback.
 */
class SettleForeignPositionFifoWalkTest {

    private static final String TENANT = "finance";
    private static final String ACCT = LedgerAccountCodes.CASH_CLEARING;

    /** A fully-open lot acquired at {@code seq} (acquired_at ordering is the repository's). */
    private static FxPositionLot lot(long seq, long foreignMinor, long baseMinor) {
        return FxPositionLot.acquire(TENANT, ACCT, Currency.USD,
                Instant.parse("2026-06-01T00:00:00Z").plusSeconds(seq), seq,
                foreignMinor, baseMinor, "entry-" + seq, Instant.now());
    }

    @Test
    @DisplayName("two lots, settle within the first → only the oldest is consumed, slice = round(carrying×c/r)")
    void partialFirstLotOnly() {
        // Lot1: 1000 USD @ 1,300,000 carrying; Lot2: 1000 USD @ 1,400,000.
        FxPositionLot l1 = lot(1, 1_000L, 1_300_000L);
        FxPositionLot l2 = lot(2, 1_000L, 1_400_000L);

        // Settle 400 USD: consumes 400 of lot1. slice = round(1,300,000 × 400/1000) = 520,000.
        FifoWalk walk = SettleForeignPositionUseCase.walkFifo(List.of(l1, l2), 400L);

        assertThat(walk).isNotNull();
        assertThat(walk.carryingSettledMinor()).isEqualTo(520_000L); // FIFO (lot1), NOT the pool avg
        assertThat(walk.consumedLots()).containsExactly(l1);
        assertThat(l1.remainingForeignMinor()).isEqualTo(600L);
        assertThat(l1.carryingBaseMinor()).isEqualTo(780_000L);       // 1,300,000 − 520,000
        assertThat(l2.remainingForeignMinor()).isEqualTo(1_000L);     // untouched
        assertThat(l2.carryingBaseMinor()).isEqualTo(1_400_000L);
    }

    @Test
    @DisplayName("settle spanning two lots → oldest fully consumed (exact carrying), then partial next")
    void spanTwoLots() {
        FxPositionLot l1 = lot(1, 1_000L, 1_300_000L);
        FxPositionLot l2 = lot(2, 1_000L, 1_400_000L);

        // Settle 1500 USD: lot1 fully (slice = 1,300,000 exact), lot2 500 (round(1,400,000×500/1000)=700,000).
        FifoWalk walk = SettleForeignPositionUseCase.walkFifo(List.of(l1, l2), 1_500L);

        assertThat(walk).isNotNull();
        assertThat(walk.carryingSettledMinor()).isEqualTo(2_000_000L); // 1,300,000 + 700,000
        assertThat(walk.consumedLots()).containsExactly(l1, l2);
        assertThat(l1.remainingForeignMinor()).isZero();
        assertThat(l1.carryingBaseMinor()).isZero();                   // fully consumed, exact (no drift)
        assertThat(l2.remainingForeignMinor()).isEqualTo(500L);
        assertThat(l2.carryingBaseMinor()).isEqualTo(700_000L);
    }

    @Test
    @DisplayName("full settle of all lots → every lot consumed, C_settle = Σ lot carrying (drift 0)")
    void fullSettleAllLots() {
        FxPositionLot l1 = lot(1, 1_000L, 1_300_000L);
        FxPositionLot l2 = lot(2, 1_000L, 1_400_000L);

        FifoWalk walk = SettleForeignPositionUseCase.walkFifo(List.of(l1, l2), 2_000L);

        assertThat(walk).isNotNull();
        assertThat(walk.carryingSettledMinor()).isEqualTo(2_700_000L); // exact Σ carrying
        assertThat(walk.consumedLots()).containsExactly(l1, l2);
        assertThat(l1.remainingForeignMinor()).isZero();
        assertThat(l2.remainingForeignMinor()).isZero();
        assertThat(l1.carryingBaseMinor()).isZero();
        assertThat(l2.carryingBaseMinor()).isZero();
    }

    @Test
    @DisplayName("no open lots → shortfall (null), drives the weighted-average fallback")
    void noLotsShortfall() {
        assertThat(SettleForeignPositionUseCase.walkFifo(List.of(), 1_000L)).isNull();
    }

    @Test
    @DisplayName("Σ open-lot remaining < |F_settle| → shortfall (null), no lot fully covers")
    void shortLotsShortfall() {
        FxPositionLot l1 = lot(1, 1_000L, 1_300_000L);
        FxPositionLot l2 = lot(2, 500L, 700_000L);
        // Need 2000 but only 1500 open.
        assertThat(SettleForeignPositionUseCase.walkFifo(List.of(l1, l2), 2_000L)).isNull();
    }

    @Test
    @DisplayName("exact-cover (Σremaining == |F_settle|) → all consumed, not a shortfall")
    void exactCover() {
        FxPositionLot l1 = lot(1, 1_000L, 1_300_000L);
        FxPositionLot l2 = lot(2, 500L, 700_000L);

        FifoWalk walk = SettleForeignPositionUseCase.walkFifo(List.of(l1, l2), 1_500L);

        assertThat(walk).isNotNull();
        assertThat(walk.carryingSettledMinor()).isEqualTo(2_000_000L); // 1,300,000 + 700,000
        assertThat(l1.remainingForeignMinor()).isZero();
        assertThat(l2.remainingForeignMinor()).isZero();
    }

    @Test
    @DisplayName("single lot partial settle == weighted-average pool (lot-exact == pool for one lot)")
    void singleLotEqualsPool() {
        FxPositionLot only = lot(1, 10_000L, 130_000L);
        // Settle 4000: round(130,000 × 4000/10000) = 52,000 — identical to the weighted-average share.
        FifoWalk walk = SettleForeignPositionUseCase.walkFifo(List.of(only), 4_000L);

        assertThat(walk).isNotNull();
        assertThat(walk.carryingSettledMinor()).isEqualTo(52_000L);
        assertThat(only.remainingForeignMinor()).isEqualTo(6_000L);
        assertThat(only.carryingBaseMinor()).isEqualTo(78_000L);
    }
}
