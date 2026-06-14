package com.example.finance.ledger.domain.reconciliation;

import com.example.finance.ledger.domain.money.LedgerReportingCurrency;
import com.example.finance.ledger.domain.money.Money;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The pure reconciliation matching engine (architecture.md § Reconciliation,
 * fintech F8). Given the external statement lines and the internal
 * clearing-account ledger lines in scope, it produces {@link ReconciliationMatch}es
 * and OPEN {@link ReconciliationDiscrepancy}s. No Spring/JPA — exhaustively
 * unit-tested.
 *
 * <p><b>First increment = 1:1 by (amountMinor, currency, direction).</b> For each
 * external line, the FIRST not-yet-consumed internal line with an equal (amount,
 * currency, direction) is a match (both are consumed). An external line with no
 * candidate → an {@code UNMATCHED_EXTERNAL} discrepancy (expected = the external
 * amount, actual = 0). After all external lines, every not-consumed internal line
 * → an {@code UNMATCHED_INTERNAL} discrepancy (expected = 0, actual = the internal
 * amount). The algorithm is <b>deterministic</b> (it consumes candidates in input
 * order).
 *
 * <p><b>(11th incr — TASK-FIN-BE-017, multi-currency reconciliation) base (FX)
 * leg.</b> When a foreign-currency external line matches an internal line on the
 * transaction leg, the matcher additionally compares the bank-reported base (KRW)
 * value to the internal line's carrying base. <b>Iff</b> {@code currency != KRW}
 * AND the external {@code baseAmount} is present AND it differs from the internal
 * {@code baseMoney}, it ALSO records an {@code AMOUNT_MISMATCH} discrepancy
 * (expected = the internal carrying base, actual = the external base, currency =
 * KRW, carrying BOTH the matched {@code externalRef} and {@code journalEntryId}).
 * <b>The transaction-leg match is still recorded</b> — the settlement is identified;
 * the discrepancy flags only the FX value gap. A KRW line, or a foreign line without
 * a declared base amount, produces no base-leg discrepancy (net-zero).
 *
 * <p><b>(13th incr — TASK-FIN-BE-020, configurable FX tolerance) base-leg tolerance.</b>
 * The base-leg difference is no longer compared with an exact {@code !=}; it is passed
 * through an {@link FxTolerance} value object resolved per-tenant by the use case. A
 * difference <b>within</b> the tolerance band matches cleanly (no discrepancy — the
 * transaction match is STILL recorded; tolerance suppresses only the base-leg
 * discrepancy, never the match, and never auto-posts a correction — F8). A difference
 * <b>above</b> the band records the {@code AMOUNT_MISMATCH} exactly as FIN-BE-017.
 * Under {@link FxTolerance#EXACT} (no configured tolerance — the dominant path) the
 * band is 0 and the matcher is byte-identical to FIN-BE-017 (net-zero). The matcher
 * stays pure — the tolerance is passed in; it never reads a repository.
 *
 * <p><b>(14th incr — TASK-FIN-BE-021, cross-currency base-leg matching) base-external
 * → foreign-internal fallback.</b> When a <b>base-currency (KRW)</b> external line
 * finds <b>no same-currency candidate</b> via {@link #findCandidate} (which stays the
 * unchanged, first-precedence exact {@code (amount, currency, direction)} pass), the
 * matcher runs a strict <b>fallback</b> {@link #findCrossCurrencyCandidate}: the FIRST
 * not-consumed <b>foreign</b> internal line (same direction; {@code currency != KRW})
 * whose carrying base ({@code baseMoney}) is <b>within</b> the per-tenant
 * {@link FxTolerance} of the external KRW amount. A bank often settles a foreign
 * position <b>in the base currency (KRW)</b> while the ledger booked the underlying as
 * a foreign line carrying a KRW base; under the same-currency-only matcher that KRW
 * external → {@code UNMATCHED_EXTERNAL} and the foreign internal → {@code UNMATCHED_INTERNAL}
 * (two spurious discrepancies for one settlement). The fallback pairs them: it consumes
 * the foreign internal, marks the external matched, and records a {@link ReconciliationMatch}
 * (carrying the external KRW {@code money} + the internal {@code journalEntryId}) flagged
 * {@code crossCurrency=true}. For a cross-currency match the carrying-base comparison
 * <b>is</b> the match key — within tolerance → a clean match with <b>NO</b>
 * {@code AMOUNT_MISMATCH}; beyond tolerance → not a candidate → the line falls through to
 * {@code UNMATCHED_EXTERNAL} exactly as before. <b>Precedence + net-zero</b>: same-currency
 * matching runs first and is byte-unchanged; the cross-currency pass is a strict fallback
 * that fires only for a KRW external with no KRW candidate but a carrying-base-matching
 * foreign internal — every existing same-currency / same-foreign-currency reconciliation
 * is unaffected. A <b>foreign</b> external line NEVER enters the cross-currency pass (the
 * direction is base-external → foreign-internal only). The matcher stays pure.
 *
 * <p><b>(19th incr — TASK-FIN-BE-027, reverse cross-currency matching) foreign-external
 * → KRW-internal fallback.</b> The exact <b>mirror</b> of the 14th increment, in the
 * opposite direction. A bank frequently settles a position <b>in a foreign currency</b>
 * (reporting the bank-side base/KRW value) while the ledger booked the settlement as a
 * <b>base-currency (KRW)</b> internal line. When such a <b>foreign</b> external line finds
 * <b>no same-currency candidate</b> via {@link #findCandidate} <b>and</b> carries a declared
 * {@code baseAmount} (the bank-reported KRW value), the matcher runs a strict
 * <b>fallback</b> {@link #findReverseCrossCurrencyCandidate}: the FIRST not-consumed
 * <b>base-currency (KRW)</b> internal line (same direction; {@code currency == KRW}) whose
 * {@code money} (its KRW amount) is <b>within</b> the per-tenant {@link FxTolerance} of the
 * external's declared {@code baseAmount}. Under the same-currency-only matcher that foreign
 * external → {@code UNMATCHED_EXTERNAL} and the KRW internal → {@code UNMATCHED_INTERNAL}
 * (two spurious discrepancies for one settlement). The fallback pairs them: it consumes the
 * KRW internal, marks the external matched, and records a {@link ReconciliationMatch}
 * (carrying the external <b>foreign</b> {@code money} + the internal {@code journalEntryId})
 * flagged {@code crossCurrency=true}. As with the 14th increment the base comparison <b>is</b>
 * the match key — within tolerance → a clean match with <b>NO</b> {@code AMOUNT_MISMATCH};
 * beyond tolerance, or no declared {@code baseAmount}, → not a candidate → the line falls
 * through to {@code UNMATCHED_EXTERNAL} exactly as before. <b>Precedence + net-zero</b>:
 * same-currency matching runs first and is byte-unchanged; the two cross-currency passes are
 * mutually exclusive ({@code currency == KRW} external → 14th-incr {@link #findCrossCurrencyCandidate}
 * only; {@code currency != KRW} external → this 19th-incr reverse pass only) and both are strict
 * fallbacks. Every existing reconciliation — same-currency, FIN-BE-017 base-leg, FIN-BE-020
 * tolerance, and FIN-BE-021 KRW-external — is unaffected. The matcher stays pure.
 *
 * <p><b>F8 — no auto-close.</b> The matcher only RECORDS discrepancies (always
 * OPEN); it never posts a balancing entry, mutates a journal entry, or
 * auto-resolves a difference.
 */
public final class ReconciliationMatcher {

    private ReconciliationMatcher() {
    }

    /**
     * Run the 1:1 matcher. The matched {@link ExternalStatementLine}s are flipped
     * to {@code MATCHED} in place; the result carries the matches + OPEN
     * discrepancies. {@code at} stamps {@code matchedAt} / {@code detectedAt}.
     *
     * @param tenantId          the reconciliation tenant
     * @param statementId       the ingested statement (discrepancy provenance)
     * @param ledgerAccountCode the reconciled clearing account
     * @param externalLines     the ingested statement lines (mutated: matchStatus)
     * @param internalLines     the unmatched internal ledger lines on the account
     * @param tolerance         the tenant's base-leg FX tolerance (13th incr — resolved
     *                          by the use case; {@link FxTolerance#EXACT} = net-zero)
     * @param at                the run instant (matchedAt / detectedAt)
     */
    public static ReconciliationResult match(String tenantId, String statementId,
                                             String ledgerAccountCode,
                                             List<ExternalStatementLine> externalLines,
                                             List<InternalLine> internalLines,
                                             FxTolerance tolerance,
                                             Instant at) {
        List<ReconciliationMatch> matches = new ArrayList<>();
        List<ReconciliationDiscrepancy> discrepancies = new ArrayList<>();
        boolean[] consumed = new boolean[internalLines.size()];

        for (ExternalStatementLine ext : externalLines) {
            int candidate = findCandidate(internalLines, consumed, ext);
            if (candidate >= 0) {
                consumed[candidate] = true;
                ext.markMatched();
                InternalLine internal = internalLines.get(candidate);
                // Same-currency match (precedence) — never cross-currency (crossCurrency=false).
                matches.add(ReconciliationMatch.of(null, tenantId, ext.lineId(),
                        ext.externalRef(), internal.journalEntryId(), ledgerAccountCode,
                        ext.money(), false, at));
                // (11th incr) base (FX) leg — a foreign line with a declared base whose
                // bank-reported KRW value differs from the internal carrying base ALSO
                // records an AMOUNT_MISMATCH (the match is still recorded; F8 — never
                // auto-adjusted). KRW lines / base-less lines never fire (net-zero).
                // (13th incr) the exact compare is gated through the per-tenant
                // FxTolerance — within the band → no discrepancy (match still recorded);
                // above → AMOUNT_MISMATCH as FIN-BE-017. EXACT band == 0 ⇒ net-zero.
                if (ext.currency() != LedgerReportingCurrency.BASE
                        && ext.baseAmount() != null
                        && !tolerance.isWithinTolerance(
                                internal.baseMoney().minorUnits(),
                                ext.baseAmount().minorUnits())) {
                    discrepancies.add(ReconciliationDiscrepancy.open(null, tenantId, statementId,
                            ledgerAccountCode, DiscrepancyType.AMOUNT_MISMATCH,
                            ext.externalRef(), internal.journalEntryId(),
                            internal.baseMoney().minorUnits(), ext.baseAmount().minorUnits(),
                            LedgerReportingCurrency.BASE, at));
                }
            } else {
                // (14th incr — TASK-FIN-BE-021) cross-currency base-leg FALLBACK. Only a
                // BASE-currency (KRW) external with no same-currency candidate falls back to
                // a foreign internal line whose carrying base (baseMoney) is within tolerance
                // of the external KRW amount. The base comparison IS the match key — within
                // tolerance → a clean match (crossCurrency=true, NO AMOUNT_MISMATCH); beyond
                // tolerance → not a candidate → UNMATCHED_EXTERNAL as before (net-zero for
                // every non-KRW external and every KRW external with no carrying-base match).
                // (19th incr — TASK-FIN-BE-027) the mirror direction. A FOREIGN external
                // (currency != KRW) carrying a declared baseAmount falls back to the FIRST
                // not-consumed BASE-currency (KRW) internal line whose KRW amount (money) is
                // within tolerance of the external's bank-reported base. The two passes are
                // mutually exclusive (KRW external → 14th-incr pass; foreign external → reverse
                // pass) and same-currency-first. A foreign external without a baseAmount, or with
                // no within-tolerance KRW internal, → UNMATCHED_EXTERNAL exactly as before.
                int crossCandidate;
                if (ext.currency() == LedgerReportingCurrency.BASE) {
                    crossCandidate = findCrossCurrencyCandidate(internalLines, consumed, ext, tolerance);
                } else if (ext.baseAmount() != null) {
                    crossCandidate = findReverseCrossCurrencyCandidate(internalLines, consumed, ext, tolerance);
                } else {
                    crossCandidate = -1;
                }
                if (crossCandidate >= 0) {
                    consumed[crossCandidate] = true;
                    ext.markMatched();
                    InternalLine internal = internalLines.get(crossCandidate);
                    matches.add(ReconciliationMatch.of(null, tenantId, ext.lineId(),
                            ext.externalRef(), internal.journalEntryId(), ledgerAccountCode,
                            ext.money(), true, at));
                } else {
                    // UNMATCHED_EXTERNAL — expected = the external amount, actual = 0.
                    discrepancies.add(ReconciliationDiscrepancy.open(null, tenantId, statementId,
                            ledgerAccountCode, DiscrepancyType.UNMATCHED_EXTERNAL,
                            ext.externalRef(), null,
                            ext.amountMinor(), 0L, ext.currency(), at));
                }
            }
        }

        for (int i = 0; i < internalLines.size(); i++) {
            if (consumed[i]) {
                continue;
            }
            // UNMATCHED_INTERNAL — expected = 0, actual = the internal amount.
            InternalLine internal = internalLines.get(i);
            Money money = internal.money();
            discrepancies.add(ReconciliationDiscrepancy.open(null, tenantId, statementId,
                    ledgerAccountCode, DiscrepancyType.UNMATCHED_INTERNAL,
                    null, internal.journalEntryId(),
                    0L, money.minorUnits(), money.currency(), at));
        }

        return new ReconciliationResult(matches, discrepancies);
    }

    /** The index of the first not-consumed internal line equal on (amount, currency, direction). */
    private static int findCandidate(List<InternalLine> internalLines, boolean[] consumed,
                                     ExternalStatementLine ext) {
        for (int i = 0; i < internalLines.size(); i++) {
            if (consumed[i]) {
                continue;
            }
            InternalLine internal = internalLines.get(i);
            if (internal.direction() == ext.direction()
                    && internal.money().currency() == ext.currency()
                    && internal.money().minorUnits() == ext.amountMinor()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * (14th incr — TASK-FIN-BE-021) The index of the FIRST not-consumed <b>foreign</b>
     * internal line (same direction; {@code currency != KRW}) whose carrying base
     * ({@code baseMoney}) is within {@code tolerance} of the base-currency (KRW)
     * external amount. The caller guarantees {@code ext} is a base-currency line; the
     * external KRW amount ({@code ext.amountMinor()}) is the base amount compared
     * against the internal carrying base ({@code internal.baseMoney().minorUnits()}).
     * Under {@link FxTolerance#EXACT} the band is 0 ⇒ exact carrying-base equality.
     * Returns {@code -1} when no foreign internal carries a within-tolerance base.
     */
    private static int findCrossCurrencyCandidate(List<InternalLine> internalLines,
                                                  boolean[] consumed, ExternalStatementLine ext,
                                                  FxTolerance tolerance) {
        for (int i = 0; i < internalLines.size(); i++) {
            if (consumed[i]) {
                continue;
            }
            InternalLine internal = internalLines.get(i);
            if (internal.direction() == ext.direction()
                    && internal.money().currency() != LedgerReportingCurrency.BASE
                    && tolerance.isWithinTolerance(
                            internal.baseMoney().minorUnits(), ext.amountMinor())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * (19th incr — TASK-FIN-BE-027) The mirror of {@link #findCrossCurrencyCandidate}.
     * The index of the FIRST not-consumed <b>base-currency (KRW)</b> internal line (same
     * direction; {@code currency == KRW}) whose {@code money} (its KRW amount) is within
     * {@code tolerance} of the <b>foreign</b> external line's bank-reported base amount
     * ({@code ext.baseAmount()}). The caller guarantees {@code ext} is a foreign line with a
     * non-null {@code baseAmount}; that declared base ({@code ext.baseAmount().minorUnits()})
     * is compared against the internal KRW amount ({@code internal.money().minorUnits()}).
     * Under {@link FxTolerance#EXACT} the band is 0 ⇒ exact KRW-amount equality. Returns
     * {@code -1} when no KRW internal carries a within-tolerance amount.
     */
    private static int findReverseCrossCurrencyCandidate(List<InternalLine> internalLines,
                                                         boolean[] consumed, ExternalStatementLine ext,
                                                         FxTolerance tolerance) {
        for (int i = 0; i < internalLines.size(); i++) {
            if (consumed[i]) {
                continue;
            }
            InternalLine internal = internalLines.get(i);
            if (internal.direction() == ext.direction()
                    && internal.money().currency() == LedgerReportingCurrency.BASE
                    && tolerance.isWithinTolerance(
                            internal.money().minorUnits(), ext.baseAmount().minorUnits())) {
                return i;
            }
        }
        return -1;
    }
}
