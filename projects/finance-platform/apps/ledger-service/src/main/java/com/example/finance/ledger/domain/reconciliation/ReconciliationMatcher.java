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
 * a declared base amount, produces no base-leg discrepancy (net-zero — exact
 * comparison; a configurable FX tolerance is forward-declared).
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
     * @param at                the run instant (matchedAt / detectedAt)
     */
    public static ReconciliationResult match(String tenantId, String statementId,
                                             String ledgerAccountCode,
                                             List<ExternalStatementLine> externalLines,
                                             List<InternalLine> internalLines,
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
                matches.add(ReconciliationMatch.of(null, tenantId, ext.lineId(),
                        ext.externalRef(), internal.journalEntryId(), ledgerAccountCode,
                        ext.money(), at));
                // (11th incr) base (FX) leg — a foreign line with a declared base whose
                // bank-reported KRW value differs from the internal carrying base ALSO
                // records an AMOUNT_MISMATCH (the match is still recorded; F8 — never
                // auto-adjusted). KRW lines / base-less lines never fire (net-zero).
                if (ext.currency() != LedgerReportingCurrency.BASE
                        && ext.baseAmount() != null
                        && ext.baseAmount().minorUnits() != internal.baseMoney().minorUnits()) {
                    discrepancies.add(ReconciliationDiscrepancy.open(null, tenantId, statementId,
                            ledgerAccountCode, DiscrepancyType.AMOUNT_MISMATCH,
                            ext.externalRef(), internal.journalEntryId(),
                            internal.baseMoney().minorUnits(), ext.baseAmount().minorUnits(),
                            LedgerReportingCurrency.BASE, at));
                }
            } else {
                // UNMATCHED_EXTERNAL — expected = the external amount, actual = 0.
                discrepancies.add(ReconciliationDiscrepancy.open(null, tenantId, statementId,
                        ledgerAccountCode, DiscrepancyType.UNMATCHED_EXTERNAL,
                        ext.externalRef(), null,
                        ext.amountMinor(), 0L, ext.currency(), at));
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
}
