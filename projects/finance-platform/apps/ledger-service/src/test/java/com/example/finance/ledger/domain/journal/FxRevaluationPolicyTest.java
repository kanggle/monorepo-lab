package com.example.finance.ledger.domain.journal;

import com.example.finance.ledger.domain.account.LedgerAccountCodes;
import com.example.finance.ledger.domain.error.LedgerErrors.RevaluationRateInvalidException;
import com.example.finance.ledger.domain.journal.FxRevaluationPolicy.Outcome;
import com.example.finance.ledger.domain.journal.FxRevaluationPolicy.RevaluationResult;
import com.example.finance.ledger.domain.money.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test for {@link FxRevaluationPolicy} (9th increment, TASK-FIN-BE-015 AC-1).
 * Proves the signed-delta polarity for all four quadrants (asset gain/loss,
 * liability gain/loss) <b>without account-type branching</b>, the {@code delta == 0}
 * no-op, HALF_UP rounding, and the {@code closingRate ≤ 0} rejection. Money stays
 * integer minor units (F5) — only the rate is a {@link BigDecimal}.
 */
class FxRevaluationPolicyTest {

    private static final String TENANT = "finance";
    private static final String CASH = LedgerAccountCodes.CASH_CLEARING;
    private static final Currency USD = Currency.USD;

    private static RevaluationResult revalueOrThrow(long foreign, long carrying, String rate) {
        return FxRevaluationPolicy.revalue(TENANT, CASH, USD, foreign, carrying,
                new BigDecimal(rate)).orElseThrow();
    }

    @Test
    @DisplayName("asset gain (F>0, rate↑ → delta>0): DR account (base +delta) / CR FX_GAIN")
    void assetGain() {
        // $100 = 10000 USD-minor debit, carried at 130000 KRW; revalue @ 13.5 → 135000.
        RevaluationResult r = revalueOrThrow(10_000L, 130_000L, "13.5");

        assertThat(r.delta()).isEqualTo(5_000L);
        assertThat(r.outcome()).isEqualTo(Outcome.FX_GAIN);
        assertThat(r.lines()).hasSize(2);

        JournalLine adjustment = r.lines().get(0);
        assertThat(adjustment.ledgerAccountCode()).isEqualTo(CASH);
        assertThat(adjustment.isDebit()).isTrue();
        assertThat(adjustment.money().minorUnits()).isZero();          // zero foreign amount
        assertThat(adjustment.money().currency()).isEqualTo(USD);
        assertThat(adjustment.baseAmountMinor()).isEqualTo(5_000L);    // KRW carrying delta
        assertThat(adjustment.baseCurrency()).isEqualTo(Currency.KRW);
        assertThat(adjustment.exchangeRate()).isEqualByComparingTo(new BigDecimal("13.5"));

        JournalLine contra = r.lines().get(1);
        assertThat(contra.ledgerAccountCode()).isEqualTo(LedgerAccountCodes.FX_GAIN);
        assertThat(contra.isCredit()).isTrue();
        assertThat(contra.money().minorUnits()).isEqualTo(5_000L);
        assertThat(contra.money().currency()).isEqualTo(Currency.KRW);
    }

    @Test
    @DisplayName("asset loss (F>0, rate↓ → delta<0): CR account (base +|delta|) / DR FX_LOSS")
    void assetLoss() {
        // carried at 135000; revalue @ 13.0 → 130000 → delta -5000.
        RevaluationResult r = revalueOrThrow(10_000L, 135_000L, "13.0");

        assertThat(r.delta()).isEqualTo(-5_000L);
        assertThat(r.outcome()).isEqualTo(Outcome.FX_LOSS);

        JournalLine adjustment = r.lines().get(0);
        assertThat(adjustment.ledgerAccountCode()).isEqualTo(CASH);
        assertThat(adjustment.isCredit()).isTrue();
        assertThat(adjustment.money().minorUnits()).isZero();
        assertThat(adjustment.baseAmountMinor()).isEqualTo(5_000L);    // positive magnitude

        JournalLine contra = r.lines().get(1);
        assertThat(contra.ledgerAccountCode()).isEqualTo(LedgerAccountCodes.FX_LOSS);
        assertThat(contra.isDebit()).isTrue();
        assertThat(contra.money().minorUnits()).isEqualTo(5_000L);
    }

    @Test
    @DisplayName("liability loss (F<0, base value ↑ → delta<0): polarity automatic, no account branching")
    void liabilityLoss() {
        // A credit-balance liability: F = -10000 (Σdebit-Σcredit), carried at -130000.
        // revalue @ 13.5 → round(-10000 × 13.5) = -135000 → delta = -135000 - (-130000) = -5000 → loss.
        RevaluationResult r = revalueOrThrow(-10_000L, -130_000L, "13.5");

        assertThat(r.delta()).isEqualTo(-5_000L);
        assertThat(r.outcome()).isEqualTo(Outcome.FX_LOSS);

        JournalLine adjustment = r.lines().get(0);
        assertThat(adjustment.isCredit()).isTrue();
        assertThat(adjustment.baseAmountMinor()).isEqualTo(5_000L);
        JournalLine contra = r.lines().get(1);
        assertThat(contra.ledgerAccountCode()).isEqualTo(LedgerAccountCodes.FX_LOSS);
        assertThat(contra.isDebit()).isTrue();
    }

    @Test
    @DisplayName("liability gain (F<0, rate↓ → delta>0): polarity automatic, no account branching")
    void liabilityGain() {
        // F = -10000, carried at -135000. revalue @ 13.0 → -130000 → delta = +5000 → gain.
        RevaluationResult r = revalueOrThrow(-10_000L, -135_000L, "13.0");

        assertThat(r.delta()).isEqualTo(5_000L);
        assertThat(r.outcome()).isEqualTo(Outcome.FX_GAIN);

        JournalLine adjustment = r.lines().get(0);
        assertThat(adjustment.isDebit()).isTrue();
        assertThat(adjustment.baseAmountMinor()).isEqualTo(5_000L);
        JournalLine contra = r.lines().get(1);
        assertThat(contra.ledgerAccountCode()).isEqualTo(LedgerAccountCodes.FX_GAIN);
        assertThat(contra.isCredit()).isTrue();
    }

    @Test
    @DisplayName("delta == 0 (already at spot) → Optional.empty (no adjustment)")
    void atSpotIsEmpty() {
        Optional<RevaluationResult> r = FxRevaluationPolicy.revalue(
                TENANT, CASH, USD, 10_000L, 135_000L, new BigDecimal("13.5"));
        assertThat(r).isEmpty();
    }

    @Test
    @DisplayName("revaluedBase = round(foreignBalance × closingRate) HALF_UP")
    void roundingHalfUp() {
        // 10000 × 13.50005 = 135000.5 → HALF_UP → 135001 → delta = 135001 - 130000 = 5001.
        RevaluationResult r = revalueOrThrow(10_000L, 130_000L, "13.50005");
        assertThat(r.delta()).isEqualTo(5_001L);

        // 10000 × 13.500049 = 135000.49 → HALF_UP → 135000 → delta = 5000.
        RevaluationResult down = revalueOrThrow(10_000L, 130_000L, "13.500049");
        assertThat(down.delta()).isEqualTo(5_000L);
    }

    @Test
    @DisplayName("the built adjusting entry balances in the base currency (Σ baseDebit == Σ baseCredit)")
    void entryBalancesInBase() {
        RevaluationResult r = revalueOrThrow(10_000L, 130_000L, "13.5");
        JournalEntry entry = JournalEntry.post("e-1", TENANT,
                java.time.Instant.parse("2026-06-30T23:59:59Z"),
                SourceRef.ofRevaluation("FX-REVAL", "reval:k-1"), r.lines());
        assertThat(entry.isBalanced()).isTrue();
        assertThat(entry.baseDebitTotal().minorUnits()).isEqualTo(5_000L);
        assertThat(entry.baseCreditTotal().minorUnits()).isEqualTo(5_000L);
    }

    @Test
    @DisplayName("closingRate == 0 → RevaluationRateInvalidException")
    void zeroRateRejected() {
        assertThatThrownBy(() -> FxRevaluationPolicy.revalue(
                TENANT, CASH, USD, 10_000L, 130_000L, new BigDecimal("0")))
                .isInstanceOf(RevaluationRateInvalidException.class);
    }

    @Test
    @DisplayName("closingRate < 0 → RevaluationRateInvalidException")
    void negativeRateRejected() {
        assertThatThrownBy(() -> FxRevaluationPolicy.revalue(
                TENANT, CASH, USD, 10_000L, 130_000L, new BigDecimal("-13.5")))
                .isInstanceOf(RevaluationRateInvalidException.class);
    }
}
