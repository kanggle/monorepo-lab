package com.example.finance.ledger.domain.journal;

import com.example.finance.ledger.domain.account.LedgerAccountCodes;
import com.example.finance.ledger.domain.error.LedgerErrors.SettlementRateInvalidException;
import com.example.finance.ledger.domain.journal.FxSettlementPolicy.Outcome;
import com.example.finance.ledger.domain.journal.FxSettlementPolicy.SettlementResult;
import com.example.finance.ledger.domain.money.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test for {@link FxSettlementPolicy} (10th increment, TASK-FIN-BE-016 AC-1).
 * Proves the signed-polarity for all four quadrants (asset gain/loss, liability
 * gain/loss) <b>without account-type branching</b>, that the 3-line entry balances in
 * base, the removal line zeroes the position ({@code money=|F|}, {@code baseAmount=|C|}),
 * HALF_UP {@code proceedsBase} rounding, the {@code F == 0} no-op, the
 * {@code settlementRate ≤ 0} rejection, and the {@code realized == 0} 2-line entry.
 * Money stays integer minor units (F5) — only the rate is a {@link BigDecimal}.
 */
class FxSettlementPolicyTest {

    private static final String TENANT = "finance";
    private static final String CASH = LedgerAccountCodes.CASH_CLEARING;
    private static final String PROCEEDS = LedgerAccountCodes.SETTLEMENT_SUSPENSE;
    private static final Currency USD = Currency.USD;
    private static final Currency KRW = Currency.KRW;

    private static SettlementResult settleOrThrow(long foreign, long carrying, String rate) {
        return FxSettlementPolicy.settle(TENANT, CASH, USD, foreign, carrying,
                new BigDecimal(rate), PROCEEDS).orElseThrow();
    }

    private static SettlementResult settlePartialOrThrow(long foreign, long carrying,
                                                         long settleForeign, String rate) {
        return FxSettlementPolicy.settle(TENANT, CASH, USD, foreign, carrying, settleForeign,
                new BigDecimal(rate), PROCEEDS).orElseThrow();
    }

    /** Build a JournalEntry from the result's lines; asserts the base balance by construction. */
    private static JournalEntry entryOf(SettlementResult r) {
        return JournalEntry.post("e-1", TENANT, Instant.parse("2026-06-30T23:59:59Z"),
                SourceRef.ofSettlement("FX-SETTLE", "settle:k-1"), r.lines());
    }

    @Test
    @DisplayName("asset gain (F>0, rate above carrying): removal CR USD/base, proceeds DR KRW, FX CR FX_GAIN")
    void assetGain() {
        // $100 = 10000 USD-minor debit, carried 130000 KRW; settle @ 13.7 → proceeds 137000, realized +7000.
        SettlementResult r = settleOrThrow(10_000L, 130_000L, "13.7");

        assertThat(r.proceedsBase()).isEqualTo(137_000L);
        assertThat(r.realized()).isEqualTo(7_000L);
        assertThat(r.outcome()).isEqualTo(Outcome.FX_GAIN);
        assertThat(r.lines()).hasSize(3);

        JournalLine removal = r.lines().get(0);
        assertThat(removal.ledgerAccountCode()).isEqualTo(CASH);
        assertThat(removal.isCredit()).isTrue();                         // asset removed by CREDIT
        assertThat(removal.money().minorUnits()).isEqualTo(10_000L);     // |F| foreign
        assertThat(removal.money().currency()).isEqualTo(USD);
        assertThat(removal.baseAmountMinor()).isEqualTo(130_000L);       // |C| base carrying
        assertThat(removal.baseCurrency()).isEqualTo(KRW);

        JournalLine proceeds = r.lines().get(1);
        assertThat(proceeds.ledgerAccountCode()).isEqualTo(PROCEEDS);
        assertThat(proceeds.isDebit()).isTrue();                         // asset brings base IN → DR
        assertThat(proceeds.money().minorUnits()).isEqualTo(137_000L);
        assertThat(proceeds.money().currency()).isEqualTo(KRW);

        JournalLine fx = r.lines().get(2);
        assertThat(fx.ledgerAccountCode()).isEqualTo(LedgerAccountCodes.FX_GAIN);
        assertThat(fx.isCredit()).isTrue();
        assertThat(fx.money().minorUnits()).isEqualTo(7_000L);

        // The 3-line entry balances in base (Σ baseDebit == Σ baseCredit).
        JournalEntry entry = entryOf(r);
        assertThat(entry.isBalanced()).isTrue();
        assertThat(entry.baseDebitTotal().minorUnits()).isEqualTo(137_000L);
        assertThat(entry.baseCreditTotal().minorUnits()).isEqualTo(137_000L); // 130000 + 7000
    }

    @Test
    @DisplayName("asset loss (F>0, rate below carrying): FX DR FX_LOSS, entry balances")
    void assetLoss() {
        // carried 130000; settle @ 12.5 → proceeds 125000, realized -5000.
        SettlementResult r = settleOrThrow(10_000L, 130_000L, "12.5");

        assertThat(r.proceedsBase()).isEqualTo(125_000L);
        assertThat(r.realized()).isEqualTo(-5_000L);
        assertThat(r.outcome()).isEqualTo(Outcome.FX_LOSS);
        assertThat(r.lines()).hasSize(3);

        JournalLine removal = r.lines().get(0);
        assertThat(removal.isCredit()).isTrue();
        JournalLine proceeds = r.lines().get(1);
        assertThat(proceeds.isDebit()).isTrue();
        assertThat(proceeds.money().minorUnits()).isEqualTo(125_000L);
        JournalLine fx = r.lines().get(2);
        assertThat(fx.ledgerAccountCode()).isEqualTo(LedgerAccountCodes.FX_LOSS);
        assertThat(fx.isDebit()).isTrue();
        assertThat(fx.money().minorUnits()).isEqualTo(5_000L);

        JournalEntry entry = entryOf(r);
        assertThat(entry.isBalanced()).isTrue();
        // DR side = proceeds 125000 + FX_LOSS 5000 = 130000 == removal CR 130000.
        assertThat(entry.baseDebitTotal().minorUnits()).isEqualTo(130_000L);
        assertThat(entry.baseCreditTotal().minorUnits()).isEqualTo(130_000L);
    }

    @Test
    @DisplayName("liability gain (F<0, settled below carrying): polarity automatic, no account branching")
    void liabilityGain() {
        // A credit-balance liability: F = -10000, carried C = -130000 (debit-positive).
        // settle @ 12.5 → proceedsBase = round(-10000 × 12.5) = -125000;
        // realized = -125000 - (-130000) = +5000 → gain.
        SettlementResult r = settleOrThrow(-10_000L, -130_000L, "12.5");

        assertThat(r.proceedsBase()).isEqualTo(-125_000L);
        assertThat(r.realized()).isEqualTo(5_000L);
        assertThat(r.outcome()).isEqualTo(Outcome.FX_GAIN);

        JournalLine removal = r.lines().get(0);
        assertThat(removal.isDebit()).isTrue();                          // liability removed by DEBIT
        assertThat(removal.money().minorUnits()).isEqualTo(10_000L);     // |F|
        assertThat(removal.baseAmountMinor()).isEqualTo(130_000L);       // |C|
        JournalLine proceeds = r.lines().get(1);
        assertThat(proceeds.isCredit()).isTrue();                        // liability pays base OUT → CR
        assertThat(proceeds.money().minorUnits()).isEqualTo(125_000L);   // |proceedsBase|
        JournalLine fx = r.lines().get(2);
        assertThat(fx.ledgerAccountCode()).isEqualTo(LedgerAccountCodes.FX_GAIN);
        assertThat(fx.isCredit()).isTrue();

        assertThat(entryOf(r).isBalanced()).isTrue();
    }

    @Test
    @DisplayName("liability loss (F<0, settled above carrying): polarity automatic, no account branching")
    void liabilityLoss() {
        // F = -10000, carried C = -130000. settle @ 13.7 → proceedsBase = -137000;
        // realized = -137000 - (-130000) = -7000 → loss.
        SettlementResult r = settleOrThrow(-10_000L, -130_000L, "13.7");

        assertThat(r.proceedsBase()).isEqualTo(-137_000L);
        assertThat(r.realized()).isEqualTo(-7_000L);
        assertThat(r.outcome()).isEqualTo(Outcome.FX_LOSS);

        JournalLine removal = r.lines().get(0);
        assertThat(removal.isDebit()).isTrue();
        JournalLine proceeds = r.lines().get(1);
        assertThat(proceeds.isCredit()).isTrue();
        assertThat(proceeds.money().minorUnits()).isEqualTo(137_000L);
        JournalLine fx = r.lines().get(2);
        assertThat(fx.ledgerAccountCode()).isEqualTo(LedgerAccountCodes.FX_LOSS);
        assertThat(fx.isDebit()).isTrue();

        assertThat(entryOf(r).isBalanced()).isTrue();
    }

    @Test
    @DisplayName("settling at the carrying rate → realized 0, a 2-line removal+proceeds entry that balances")
    void atCarryingRateRealizesZero() {
        // carried 130000; settle @ 13.0 → proceeds 130000, realized 0 → no FX line.
        SettlementResult r = settleOrThrow(10_000L, 130_000L, "13.0");

        assertThat(r.proceedsBase()).isEqualTo(130_000L);
        assertThat(r.realized()).isZero();
        assertThat(r.outcome()).isEqualTo(Outcome.NONE);
        assertThat(r.lines()).hasSize(2);

        JournalEntry entry = entryOf(r);
        assertThat(entry.isBalanced()).isTrue();
        assertThat(entry.baseDebitTotal().minorUnits()).isEqualTo(130_000L);  // |proceedsBase|
        assertThat(entry.baseCreditTotal().minorUnits()).isEqualTo(130_000L); // |C|
    }

    @Test
    @DisplayName("proceedsBase = round(F × settlementRate) HALF_UP")
    void proceedsHalfUp() {
        // 10000 × 13.70005 = 137000.5 → HALF_UP → 137001.
        SettlementResult up = settleOrThrow(10_000L, 130_000L, "13.70005");
        assertThat(up.proceedsBase()).isEqualTo(137_001L);
        assertThat(up.realized()).isEqualTo(7_001L);

        // 10000 × 13.700049 = 137000.49 → HALF_UP → 137000.
        SettlementResult down = settleOrThrow(10_000L, 130_000L, "13.700049");
        assertThat(down.proceedsBase()).isEqualTo(137_000L);
        assertThat(down.realized()).isEqualTo(7_000L);
    }

    @Test
    @DisplayName("removal line zeroes the position — money = |F| {currency}, baseAmount = |C| KRW")
    void removalLineZeroesPosition() {
        SettlementResult r = settleOrThrow(10_000L, 130_000L, "13.7");
        JournalLine removal = r.lines().get(0);
        assertThat(removal.ledgerAccountCode()).isEqualTo(CASH);
        assertThat(removal.money()).isEqualTo(com.example.finance.ledger.domain.money.Money.of(10_000L, USD));
        assertThat(removal.baseMoney())
                .isEqualTo(com.example.finance.ledger.domain.money.Money.of(130_000L, KRW));
    }

    @Test
    @DisplayName("F == 0 (no position) → Optional.empty")
    void noPositionIsEmpty() {
        Optional<SettlementResult> r = FxSettlementPolicy.settle(
                TENANT, CASH, USD, 0L, 0L, new BigDecimal("13.7"), PROCEEDS);
        assertThat(r).isEmpty();
    }

    @Test
    @DisplayName("settlementRate == 0 → SettlementRateInvalidException")
    void zeroRateRejected() {
        assertThatThrownBy(() -> FxSettlementPolicy.settle(
                TENANT, CASH, USD, 10_000L, 130_000L, new BigDecimal("0"), PROCEEDS))
                .isInstanceOf(SettlementRateInvalidException.class);
    }

    @Test
    @DisplayName("settlementRate < 0 → SettlementRateInvalidException")
    void negativeRateRejected() {
        assertThatThrownBy(() -> FxSettlementPolicy.settle(
                TENANT, CASH, USD, 10_000L, 130_000L, new BigDecimal("-13.7"), PROCEEDS))
                .isInstanceOf(SettlementRateInvalidException.class);
    }

    @Test
    @DisplayName("the gain entry's lines are exactly [removal, proceeds, FX_GAIN]")
    void lineOrdering() {
        SettlementResult r = settleOrThrow(10_000L, 130_000L, "13.7");
        List<JournalLine> lines = r.lines();
        assertThat(lines.get(0).ledgerAccountCode()).isEqualTo(CASH);
        assertThat(lines.get(1).ledgerAccountCode()).isEqualTo(PROCEEDS);
        assertThat(lines.get(2).ledgerAccountCode()).isEqualTo(LedgerAccountCodes.FX_GAIN);
    }

    // ---- Partial / weighted-average settlement (12th increment — TASK-FIN-BE-018) ----

    @Test
    @DisplayName("partial settle (F_settle < F): C_settle = round(C × |F_settle|/|F|), residual stays OPEN")
    void partialWeightedAverage() {
        // F = $100 (10000 USD-minor), carried 130000 KRW; settle 4000 (= $40) @ 13.7.
        // C_settle = round(130000 × 4000/10000) = 52000; proceeds = round(4000 × 13.7) = 54800;
        // realized = 54800 − 52000 = +2800 → gain.
        SettlementResult r = settlePartialOrThrow(10_000L, 130_000L, 4_000L, "13.7");

        assertThat(r.settledForeignMinor()).isEqualTo(4_000L);
        assertThat(r.carryingSettledMinor()).isEqualTo(52_000L);
        assertThat(r.proceedsBase()).isEqualTo(54_800L);
        assertThat(r.realized()).isEqualTo(2_800L);
        assertThat(r.outcome()).isEqualTo(Outcome.FX_GAIN);
        assertThat(r.lines()).hasSize(3);

        JournalLine removal = r.lines().get(0);
        assertThat(removal.isCredit()).isTrue();                       // asset removed by CREDIT
        assertThat(removal.money().minorUnits()).isEqualTo(4_000L);    // |F_settle| foreign
        assertThat(removal.baseAmountMinor()).isEqualTo(52_000L);      // |C_settle| base
        JournalLine proceeds = r.lines().get(1);
        assertThat(proceeds.isDebit()).isTrue();
        assertThat(proceeds.money().minorUnits()).isEqualTo(54_800L);

        // The partial entry balances in base on its own.
        assertThat(entryOf(r).isBalanced()).isTrue();
    }

    @Test
    @DisplayName("partial F_settle == F → byte-identical to the full settle (net-zero, AC-2)")
    void partialFullEquivalence() {
        SettlementResult full = settleOrThrow(10_000L, 130_000L, "13.7");
        SettlementResult viaPartial = settlePartialOrThrow(10_000L, 130_000L, 10_000L, "13.7");

        assertThat(viaPartial.realized()).isEqualTo(full.realized());
        assertThat(viaPartial.proceedsBase()).isEqualTo(full.proceedsBase());
        assertThat(viaPartial.outcome()).isEqualTo(full.outcome());
        assertThat(viaPartial.carryingSettledMinor()).isEqualTo(130_000L); // == C
        assertThat(viaPartial.settledForeignMinor()).isEqualTo(10_000L);   // == F
        assertThat(viaPartial.lines()).hasSize(full.lines().size());
        // line-for-line identical (account, direction, money, base)
        for (int i = 0; i < full.lines().size(); i++) {
            JournalLine a = full.lines().get(i);
            JournalLine b = viaPartial.lines().get(i);
            assertThat(b.ledgerAccountCode()).isEqualTo(a.ledgerAccountCode());
            assertThat(b.isDebit()).isEqualTo(a.isDebit());
            assertThat(b.money()).isEqualTo(a.money());
            assertThat(b.baseMoney()).isEqualTo(a.baseMoney());
        }
    }

    @Test
    @DisplayName("partial liability settle (F<0): F_settle negative, polarity automatic")
    void partialLiability() {
        // F = -10000, carried C = -130000; settle F_settle = -4000 @ 12.5.
        // C_settle = round(-130000 × 4000/10000) = -52000; proceeds = round(-4000 × 12.5) = -50000;
        // realized = -50000 − (-52000) = +2000 → gain.
        SettlementResult r = settlePartialOrThrow(-10_000L, -130_000L, -4_000L, "12.5");

        assertThat(r.settledForeignMinor()).isEqualTo(-4_000L);
        assertThat(r.carryingSettledMinor()).isEqualTo(-52_000L);
        assertThat(r.proceedsBase()).isEqualTo(-50_000L);
        assertThat(r.realized()).isEqualTo(2_000L);
        assertThat(r.outcome()).isEqualTo(Outcome.FX_GAIN);

        JournalLine removal = r.lines().get(0);
        assertThat(removal.isDebit()).isTrue();                        // liability removed by DEBIT
        assertThat(removal.money().minorUnits()).isEqualTo(4_000L);    // |F_settle|
        assertThat(removal.baseAmountMinor()).isEqualTo(52_000L);      // |C_settle|
        assertThat(entryOf(r).isBalanced()).isTrue();
    }

    @Test
    @DisplayName("tiny tranche where round(C × |F_settle|/|F|) == 0 → C_settle 0, realized = pure FX")
    void tinyTrancheCarryingZero() {
        // F = 10000, carried C = 5; settle F_settle = 1 @ 13.7.
        // C_settle = round(5 × 1/10000) = round(0.0005) = 0; proceeds = round(1 × 13.7) = 14;
        // realized = 14 − 0 = 14 → all FX gain.
        SettlementResult r = settlePartialOrThrow(10_000L, 5L, 1L, "13.7");

        assertThat(r.carryingSettledMinor()).isZero();
        assertThat(r.proceedsBase()).isEqualTo(14L);
        assertThat(r.realized()).isEqualTo(14L);
        assertThat(r.outcome()).isEqualTo(Outcome.FX_GAIN);

        JournalLine removal = r.lines().get(0);
        assertThat(removal.money().minorUnits()).isEqualTo(1L);        // |F_settle|
        assertThat(removal.baseAmountMinor()).isZero();                // |C_settle| = 0
        assertThat(entryOf(r).isBalanced()).isTrue();
    }

    @Test
    @DisplayName("sequential partials summing to F remove exactly C (no rounding drift)")
    void sequentialPartialsNoDrift() {
        // F = 10000, C = 130000. Settle 3333 then 3333 then the residual 3334 (= F).
        // C_settle1 = round(130000 × 3333/10000) = round(43329) = 43329.
        long c1 = settlePartialOrThrow(10_000L, 130_000L, 3_333L, "13.7").carryingSettledMinor();
        long fRem1 = 10_000L - 3_333L, cRem1 = 130_000L - c1;          // (6667, 86671)
        long c2 = settlePartialOrThrow(fRem1, cRem1, 3_333L, "13.7").carryingSettledMinor();
        long fRem2 = fRem1 - 3_333L, cRem2 = cRem1 - c2;               // (3334, ...)
        // Final settle of the whole residual removes exactly cRem2 (round(C × F/F) = C).
        long c3 = settlePartialOrThrow(fRem2, cRem2, fRem2, "13.7").carryingSettledMinor();

        assertThat(fRem2).isEqualTo(3_334L);
        assertThat(c3).isEqualTo(cRem2);                               // residual fully removed
        assertThat(c1 + c2 + c3).isEqualTo(130_000L);                  // sums to C, no drift
    }
}
