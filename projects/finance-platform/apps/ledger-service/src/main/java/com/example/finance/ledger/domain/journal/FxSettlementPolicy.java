package com.example.finance.ledger.domain.journal;

import com.example.finance.ledger.domain.account.LedgerAccountCodes;
import com.example.finance.ledger.domain.error.LedgerErrors.SettlementRateInvalidException;
import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.money.LedgerReportingCurrency;
import com.example.finance.ledger.domain.money.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Realized FX gain/loss on settlement policy (10th increment, TASK-FIN-BE-016 —
 * architecture.md § FX settlement). Pure domain logic — NO Spring/JPA. Given a
 * foreign position's debit-positive signed balances (foreign {@code F} + base
 * carrying {@code C}) and a settlement (spot) rate, computes the base proceeds, the
 * realized gain/loss, and builds the balanced base-currency (KRW) 3-line entry that
 * <b>removes</b> the position at carrying and recognises the realized difference.
 *
 * <p>Where the 9th-increment {@link FxRevaluationPolicy} marks an OPEN position to
 * spot (unrealized), settlement <b>closes</b> it (realized). It <b>reuses</b> the
 * 8th-increment multi-currency line + the 9th-increment FX accounts — no new line
 * primitive, no migration. The three unattached {@link JournalLine}s it returns are:
 * <ul>
 *   <li>a <b>position-removal</b> line on the foreign account — the 8th-increment
 *       multi-currency form {@link JournalLine#of(String, String, EntryDirection,
 *       Money, Money)} ({@code money = |F| {currency}}, {@code baseAmount = |C| KRW}),
 *       posted on the side that <b>zeroes</b> the {@code (account, currency)}
 *       position;</li>
 *   <li>a <b>base proceeds</b> line on the operator-supplied {@code proceedsAccountCode}
 *       — an ordinary KRW line for {@code |proceedsBase|}; and</li>
 *   <li>the realized <b>{@code FX_GAIN}</b> (income) / <b>{@code FX_LOSS}</b> (expense)
 *       contra — an ordinary KRW line for {@code |realized|} (omitted when
 *       {@code realized == 0} — a 2-line removal+proceeds entry that still balances).</li>
 * </ul>
 * All KRW base amounts net ({@code Σ baseDebit == Σ baseCredit}), so the existing
 * {@link JournalEntry} factory accepts it unchanged.
 *
 * <p><b>Polarity is automatic</b> for asset AND liability positions — every line's
 * direction is a sign. The removal + proceeds directions follow {@code sign(F)} (a
 * debit-balance asset is removed by a CREDIT and brings base IN → DR proceeds; a
 * credit-balance liability by a DEBIT and pays base OUT → CR proceeds); the FX line's
 * direction follows {@code sign(realized)} ({@code > 0} → CR {@code FX_GAIN}, {@code < 0}
 * → DR {@code FX_LOSS}). No account-type branching.
 *
 * <p><b>No double-count vs revaluation</b> — {@code realized = proceedsBase − C} is
 * measured against the <b>carrying</b> {@code C}, which already embeds any prior
 * revaluation, so a revalue-then-settle realizes only the incremental movement.
 *
 * <p><b>F5</b>: money stays integer minor units; only {@code settlementRate} is an
 * exact {@link BigDecimal}; {@code proceedsBase} is rounded HALF_UP to a {@code long}.
 *
 * <p><b>12th increment (TASK-FIN-BE-018) — partial / weighted-average settlement.</b>
 * The {@code settleForeignMinor} overload settles a <b>portion</b> {@code F_settle} of
 * the position at its weighted-average unit cost: the settled carrying is the
 * proportional share {@code C_settle = round(C × |F_settle|/|F|)} (HALF_UP, signed) and
 * the residual {@code (F − F_settle, C − C_settle)} simply remains OPEN (double-entry
 * leaves it — no extra line). The full-settle overload delegates with the same portion.
 *
 * <p><b>17th increment (TASK-FIN-BE-025) — FIFO lot consumption.</b> A shared private
 * {@code settleCore} now builds the proceeds / realized / 3-line entry from a settled
 * carrying {@code C_settle} <b>parameter</b>. The weighted-average {@code settle(...)}
 * overload computes {@code C_settle} from the pool average then calls the core
 * (byte-identical output — net-zero). A new public {@code settleWithCarrying(...)} takes a
 * <b>pre-computed</b> {@code C_settle} (the sum of the FIFO-consumed lots' carrying slices,
 * walked in the use case) and calls the SAME core — so the FIFO entry shape is identical;
 * only the carrying basis differs. The policy stays pure (the lot walk lives in the use case).
 *
 * <p>The original full-settle overload delegates with
 * {@code F_settle == F} → byte-identical output (net-zero, AC-2).
 */
public final class FxSettlementPolicy {

    private FxSettlementPolicy() {
    }

    /** Whether the settlement realized a gain ({@code FX_GAIN}), a loss ({@code FX_LOSS}), or neither. */
    public enum Outcome {
        FX_GAIN(LedgerAccountCodes.FX_GAIN),
        FX_LOSS(LedgerAccountCodes.FX_LOSS),
        NONE(null);

        private final String accountCode;

        Outcome(String accountCode) {
            this.accountCode = accountCode;
        }

        /** The contra GL account code for this outcome ({@code null} for {@link #NONE}). */
        public String accountCode() {
            return accountCode;
        }
    }

    /**
     * The result of settling a position: the signed realized base gain/loss
     * ({@code realized}, KRW minor, debit-positive), the signed {@code proceedsBase}
     * (KRW minor), the {@link Outcome} ({@link Outcome#NONE} when {@code realized == 0}),
     * the signed settled foreign portion {@code settledForeignMinor} ({@code F_settle},
     * equal to {@code F} on a full settle) and its weighted-average carrying share
     * {@code carryingSettledMinor} ({@code C_settle}) — the caller subtracts these from
     * the position to expose the residual OPEN {@code (F − F_settle, C − C_settle)} —
     * and the unattached lines to post (3 lines, or 2 when {@code realized == 0}).
     */
    public record SettlementResult(long realized, long proceedsBase, Outcome outcome,
                                   long settledForeignMinor, long carryingSettledMinor,
                                   List<JournalLine> lines) {
    }

    /**
     * Settle the <b>whole</b> {@code (ledgerAccountCode, currency)} foreign position
     * (10th increment — TASK-FIN-BE-016). Convenience overload of
     * {@link #settle(String, String, Currency, long, long, long, BigDecimal, String)}
     * with {@code settleForeignMinor == foreignBalanceMinor} — byte-identical to the
     * full-settle behaviour (net-zero, AC-2).
     *
     * @param tenantId            the owning tenant (stamped onto the built lines)
     * @param ledgerAccountCode   the foreign account whose position is removed
     * @param currency            the position's foreign currency (must not be base/KRW)
     * @param foreignBalanceMinor the position's foreign balance {@code F}
     *                            ({@code Σdebit − Σcredit}, debit-positive)
     * @param carryingBaseMinor   the position's current base carrying {@code C}
     *                            ({@code ΣbaseDebit − ΣbaseCredit}, includes prior revaluation)
     * @param settlementRate      the base-minor-per-foreign-minor spot factor (strictly positive)
     * @param proceedsAccountCode the operator-supplied base-currency proceeds account
     * @return the 3-line (or 2-line when realized == 0) settlement entry, or
     *         {@link Optional#empty()} when there is no position ({@code F == 0})
     * @throws SettlementRateInvalidException if {@code settlementRate ≤ 0}
     */
    public static Optional<SettlementResult> settle(String tenantId, String ledgerAccountCode,
                                                    Currency currency, long foreignBalanceMinor,
                                                    long carryingBaseMinor, BigDecimal settlementRate,
                                                    String proceedsAccountCode) {
        return settle(tenantId, ledgerAccountCode, currency, foreignBalanceMinor,
                carryingBaseMinor, foreignBalanceMinor, settlementRate, proceedsAccountCode);
    }

    /**
     * Settle a <b>portion</b> {@code F_settle} of the {@code (ledgerAccountCode,
     * currency)} foreign position (12th increment — TASK-FIN-BE-018, weighted-average).
     * The settled portion's carrying base is a <b>proportional share</b> of the
     * position's carrying at its average unit cost:
     * {@code C_settle = round(C × |F_settle| / |F|)} (HALF_UP, signed). The 10th's 3-line
     * entry is reused unchanged with the partial quantities — position-removal
     * {@code money = |F_settle| {currency}}, {@code baseAmount = |C_settle| KRW};
     * {@code proceedsBase = round(F_settle × settlementRate)}; realized
     * {@code = proceedsBase − C_settle}. The <b>residual</b> {@code (F − F_settle,
     * C − C_settle)} simply remains on the account — double-entry leaves it OPEN, no
     * extra line. Rounding is <b>self-correcting</b>: a final settle of the residual
     * ({@code F_settle = F}) removes exactly {@code C} ({@code round(C × F/F) = C}), so
     * repeated partials net to zero carrying with no drift.
     *
     * <p>When {@code settleForeignMinor == foreignBalanceMinor} the output is
     * byte-identical to the full settlement (net-zero, AC-2 — the {@code F_settle/F}
     * ratio collapses to 1 and {@code C_settle == C}).
     *
     * <p>{@code F_settle} carries the <b>same sign</b> as {@code F} (validated upstream
     * in the use case — sign / zero / over-settle → {@code SETTLEMENT_AMOUNT_INVALID});
     * polarity stays automatic via {@code sign(F)} / {@code sign(realized)}.
     *
     * @param tenantId            the owning tenant (stamped onto the built lines)
     * @param ledgerAccountCode   the foreign account whose position is reduced
     * @param currency            the position's foreign currency (must not be base/KRW)
     * @param foreignBalanceMinor the position's foreign balance {@code F} (debit-positive)
     * @param carryingBaseMinor   the position's current base carrying {@code C}
     * @param settleForeignMinor  the settled portion {@code F_settle} (same sign as {@code F},
     *                            {@code 0 < |F_settle| ≤ |F|}; the use case enforces this)
     * @param settlementRate      the base-minor-per-foreign-minor spot factor (strictly positive)
     * @param proceedsAccountCode the operator-supplied base-currency proceeds account
     * @return the 3-line (or 2-line when realized == 0) settlement entry, or
     *         {@link Optional#empty()} when there is no position ({@code F == 0})
     * @throws SettlementRateInvalidException if {@code settlementRate ≤ 0}
     */
    public static Optional<SettlementResult> settle(String tenantId, String ledgerAccountCode,
                                                    Currency currency, long foreignBalanceMinor,
                                                    long carryingBaseMinor, long settleForeignMinor,
                                                    BigDecimal settlementRate,
                                                    String proceedsAccountCode) {
        Objects.requireNonNull(settlementRate, "settlementRate");
        if (settlementRate.signum() <= 0) {
            throw new SettlementRateInvalidException(
                    "settlementRate must be strictly positive: " + settlementRate.toPlainString());
        }
        if (foreignBalanceMinor == 0L) {
            return Optional.empty();
        }

        // C_settle = round(C × |F_settle| / |F|), HALF_UP, signed integer KRW minor — the
        // weighted-average proportional share of the carrying at the position's average
        // unit cost. When F_settle == F the ratio is exactly 1 → C_settle == C (the
        // full-settle path, byte-identical to the 10th). Self-correcting: a final
        // residual settle removes exactly C_remaining (round(C × F/F) = C), no drift.
        long carryingSettledMinor = carryingBaseMinor == 0L ? 0L
                : new BigDecimal(carryingBaseMinor)
                        .multiply(new BigDecimal(Math.abs(settleForeignMinor)))
                        .divide(new BigDecimal(Math.abs(foreignBalanceMinor)), 0, RoundingMode.HALF_UP)
                        .longValueExact();

        return settleCore(tenantId, ledgerAccountCode, currency, foreignBalanceMinor,
                settleForeignMinor, carryingSettledMinor, settlementRate, proceedsAccountCode);
    }

    /**
     * Settle a portion {@code F_settle} of the position with a <b>pre-computed</b> settled
     * carrying {@code C_settle} (17th increment — TASK-FIN-BE-025, FIFO consumption). Where the
     * weighted-average {@code settle(...)} overload derives {@code C_settle} from the pool
     * average, the FIFO path computes it in the use case by walking the open lots
     * {@code (acquired_at, seq)} ASC — {@code C_settle = Σ round(lot.carrying × consumed /
     * lot.remaining)} — and passes the sum here. The carrying-removal / proceeds / realized FX
     * lines are then built by the SAME private {@code settleCore}, so the entry shape is
     * byte-identical to a weighted-average settle; only the carrying basis (which lots are
     * realized) differs. The policy stays pure — the lot walk is the use case's responsibility
     * (it needs the repository).
     *
     * <p>{@code foreignBalanceMinor} is still required (its sign drives the asset-vs-liability
     * polarity); {@code carryingSettledMinor} ({@code C_settle}) carries the same sign as
     * {@code F}.
     *
     * @param carryingSettledMinor the pre-computed settled carrying {@code C_settle} (the sum of
     *                             the consumed lots' carrying slices; same sign as {@code F})
     * @return the 3-line (or 2-line when realized == 0) settlement entry, or
     *         {@link Optional#empty()} when there is no position ({@code F == 0})
     * @throws SettlementRateInvalidException if {@code settlementRate ≤ 0}
     */
    public static Optional<SettlementResult> settleWithCarrying(
            String tenantId, String ledgerAccountCode, Currency currency,
            long foreignBalanceMinor, long settleForeignMinor, long carryingSettledMinor,
            BigDecimal settlementRate, String proceedsAccountCode) {
        Objects.requireNonNull(settlementRate, "settlementRate");
        if (settlementRate.signum() <= 0) {
            throw new SettlementRateInvalidException(
                    "settlementRate must be strictly positive: " + settlementRate.toPlainString());
        }
        if (foreignBalanceMinor == 0L) {
            return Optional.empty();
        }
        return settleCore(tenantId, ledgerAccountCode, currency, foreignBalanceMinor,
                settleForeignMinor, carryingSettledMinor, settlementRate, proceedsAccountCode);
    }

    /**
     * The shared settlement core (17th increment — TASK-FIN-BE-025): given the settled foreign
     * portion {@code F_settle} and its settled carrying {@code C_settle} (however derived —
     * weighted-average pool share or FIFO lot walk), build {@code proceedsBase},
     * {@code realized} and the balanced 3-line (or 2-line when {@code realized == 0})
     * base-currency entry. Pure — no repository, no Spring. Callers validate
     * {@code settlementRate > 0} and {@code F != 0} before delegating here.
     */
    private static Optional<SettlementResult> settleCore(
            String tenantId, String ledgerAccountCode, Currency currency,
            long foreignBalanceMinor, long settleForeignMinor, long carryingSettledMinor,
            BigDecimal settlementRate, String proceedsAccountCode) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(ledgerAccountCode, "ledgerAccountCode");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(proceedsAccountCode, "proceedsAccountCode");

        // proceedsBase = round(F_settle × settlementRate), HALF_UP, signed integer KRW minor
        // (F5: the only decimal is the rate; the result is exact integer minor units).
        long proceedsBase = new BigDecimal(settleForeignMinor)
                .multiply(settlementRate)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        long realized = proceedsBase - carryingSettledMinor;

        Currency base = LedgerReportingCurrency.BASE;
        boolean assetSide = foreignBalanceMinor > 0L; // debit-balance asset removed by CREDIT

        // (1) Position-removal line — reduces the (account, currency) position by the
        //     settled portion. The 8th-incr multi-currency form: money = |F_settle|
        //     {currency}, baseAmount = |C_settle| KRW. The residual stays OPEN.
        JournalLine removal = JournalLine.of(tenantId, ledgerAccountCode,
                assetSide ? EntryDirection.CREDIT : EntryDirection.DEBIT,
                Money.of(Math.abs(settleForeignMinor), currency),
                Money.of(Math.abs(carryingSettledMinor), base));

        // (2) Base proceeds line — an ordinary KRW line on the proceeds account; an
        //     asset settlement brings base IN (DR), a liability pays base OUT (CR).
        JournalLine proceeds = JournalLine.of(tenantId, proceedsAccountCode,
                assetSide ? EntryDirection.DEBIT : EntryDirection.CREDIT,
                Money.of(Math.abs(proceedsBase), base));

        List<JournalLine> lines = new ArrayList<>(3);
        lines.add(removal);
        lines.add(proceeds);

        // (3) Realized FX contra — CR FX_GAIN when realized > 0, DR FX_LOSS when < 0;
        //     no FX line when realized == 0 (the 2 lines already balance: |C| == |proceedsBase|).
        Outcome outcome;
        if (realized > 0L) {
            outcome = Outcome.FX_GAIN;
            lines.add(JournalLine.credit(tenantId, LedgerAccountCodes.FX_GAIN,
                    Money.of(Math.abs(realized), base)));
        } else if (realized < 0L) {
            outcome = Outcome.FX_LOSS;
            lines.add(JournalLine.debit(tenantId, LedgerAccountCodes.FX_LOSS,
                    Money.of(Math.abs(realized), base)));
        } else {
            outcome = Outcome.NONE;
        }

        return Optional.of(new SettlementResult(realized, proceedsBase, outcome,
                settleForeignMinor, carryingSettledMinor, List.copyOf(lines)));
    }
}
