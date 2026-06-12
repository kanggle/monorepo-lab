package com.example.finance.ledger.domain.journal;

import com.example.finance.ledger.domain.account.LedgerAccountCodes;
import com.example.finance.ledger.domain.error.LedgerErrors.RevaluationRateInvalidException;
import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.money.LedgerReportingCurrency;
import com.example.finance.ledger.domain.money.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * FX gain/loss revaluation policy (9th increment, TASK-FIN-BE-015 — architecture.md
 * § FX gain/loss revaluation). Pure domain logic — NO Spring/JPA. Given a foreign
 * position's debit-positive signed balances (foreign + base carrying) and a closing
 * (spot) rate, computes the carrying delta and builds the balanced base-currency
 * (KRW) adjusting entry that trues the position's base carrying value up to spot.
 *
 * <p>The two unattached {@link JournalLine}s it returns are:
 * <ul>
 *   <li>a <b>base-carrying adjustment</b> on the foreign account
 *       ({@link JournalLine#baseAdjustment}) — zero foreign {@code money}, the KRW
 *       carrying delta as {@code baseAmount}; and</li>
 *   <li>a contra <b>{@code FX_GAIN}</b> (income) / <b>{@code FX_LOSS}</b> (expense)
 *       ordinary positive KRW line.</li>
 * </ul>
 * Both base amounts are {@code |delta|}, so the entry balances in the base currency
 * ({@code Σ baseDebit == Σ baseCredit}) and the existing {@link JournalEntry} factory
 * accepts it unchanged.
 *
 * <p><b>Polarity is automatic</b> — the sign of {@code delta} alone selects gain vs
 * loss; there is no asset/liability branching (the debit-positive arithmetic yields a
 * loss for a growing liability and a gain for an appreciating asset with the same
 * rule). <b>F5</b>: money stays integer minor units; only {@code closingRate} is an
 * exact {@link BigDecimal}; {@code revaluedBase} is rounded HALF_UP to a {@code long}.
 */
public final class FxRevaluationPolicy {

    private FxRevaluationPolicy() {
    }

    /** Whether the revaluation recognised a gain ({@code FX_GAIN}) or a loss ({@code FX_LOSS}). */
    public enum Outcome {
        FX_GAIN(LedgerAccountCodes.FX_GAIN),
        FX_LOSS(LedgerAccountCodes.FX_LOSS);

        private final String accountCode;

        Outcome(String accountCode) {
            this.accountCode = accountCode;
        }

        /** The contra GL account code for this outcome. */
        public String accountCode() {
            return accountCode;
        }
    }

    /**
     * The result of a non-trivial revaluation: the signed base-currency carrying
     * {@code delta} (KRW minor units, debit-positive), the {@link Outcome}, and the
     * two unattached lines to post. Returned only when {@code delta != 0}.
     */
    public record RevaluationResult(long delta, Outcome outcome, List<JournalLine> lines) {
    }

    /**
     * Compute the revaluation for one {@code (ledgerAccountCode, currency)} position.
     *
     * @param tenantId           the owning tenant (stamped onto the built lines)
     * @param ledgerAccountCode  the foreign account whose carrying is trued
     * @param currency           the position's foreign currency (must not be base/KRW)
     * @param foreignBalanceMinor the position's foreign balance ({@code Σdebit − Σcredit}, debit-positive)
     * @param carryingBaseMinor  the position's current base carrying ({@code ΣbaseDebit − ΣbaseCredit})
     * @param closingRate        the base-minor-per-foreign-minor spot factor (strictly positive)
     * @return the 2-line adjusting entry, or {@link Optional#empty()} when already at
     *         spot ({@code delta == 0})
     * @throws RevaluationRateInvalidException if {@code closingRate ≤ 0}
     */
    public static Optional<RevaluationResult> revalue(String tenantId, String ledgerAccountCode,
                                                      Currency currency, long foreignBalanceMinor,
                                                      long carryingBaseMinor, BigDecimal closingRate) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(ledgerAccountCode, "ledgerAccountCode");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(closingRate, "closingRate");
        if (closingRate.signum() <= 0) {
            throw new RevaluationRateInvalidException(
                    "closingRate must be strictly positive: " + closingRate.toPlainString());
        }

        // revaluedBase = round(foreignBalance × closingRate), HALF_UP, integer KRW minor
        // (F5: the only decimal is the rate; the result is exact integer minor units).
        long revaluedBase = new BigDecimal(foreignBalanceMinor)
                .multiply(closingRate)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        long delta = revaluedBase - carryingBaseMinor;
        if (delta == 0L) {
            return Optional.empty();
        }

        long magnitude = Math.abs(delta);
        Money baseDelta = Money.of(magnitude, LedgerReportingCurrency.BASE);
        Currency base = LedgerReportingCurrency.BASE;

        JournalLine adjustment;
        JournalLine contra;
        Outcome outcome;
        if (delta > 0L) {
            // base carrying rose → gain: DR the foreign account / CR FX_GAIN.
            outcome = Outcome.FX_GAIN;
            adjustment = JournalLine.baseAdjustment(tenantId, ledgerAccountCode, currency,
                    EntryDirection.DEBIT, baseDelta, closingRate);
            contra = JournalLine.credit(tenantId, LedgerAccountCodes.FX_GAIN,
                    Money.of(magnitude, base));
        } else {
            // base carrying fell → loss: CR the foreign account / DR FX_LOSS.
            outcome = Outcome.FX_LOSS;
            adjustment = JournalLine.baseAdjustment(tenantId, ledgerAccountCode, currency,
                    EntryDirection.CREDIT, baseDelta, closingRate);
            contra = JournalLine.debit(tenantId, LedgerAccountCodes.FX_LOSS,
                    Money.of(magnitude, base));
        }
        return Optional.of(new RevaluationResult(delta, outcome, List.of(adjustment, contra)));
    }
}
