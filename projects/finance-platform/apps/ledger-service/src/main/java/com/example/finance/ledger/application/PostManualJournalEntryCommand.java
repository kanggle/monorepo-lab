package com.example.finance.ledger.application;

import com.example.finance.ledger.domain.journal.EntryDirection;
import com.example.finance.ledger.domain.money.Money;

import java.time.Instant;
import java.util.List;

/**
 * Command to post an operator-initiated manual adjusting entry (5th increment,
 * TASK-FIN-BE-011 — architecture.md § Manual Journal Posting). The controller maps
 * the validated request body + the {@code Idempotency-Key} header + the resolved
 * actor to this; the use case builds a balanced {@code JournalEntry} and funnels it
 * through the single guarded write path. Money is already a resolved {@link Money}
 * value object (minor units, F5).
 *
 * @param tenantId       the operator's tenant (row-level isolation)
 * @param operatorSubject the JWT subject recorded as the audit actor
 * @param idempotencyKey the client {@code Idempotency-Key} (namespaced
 *                       {@code manual:{key}} into the dedupe store, F1)
 * @param postedAt       optional effective instant — {@code null} defaults to now
 *                       (a back-dated adjusting entry)
 * @param reference      optional operator narrative — the entry's
 *                       {@code source.sourceTransactionId} + part of the audit reason
 * @param memo           optional operator narrative — the audit reason
 * @param lines          the balanced operator-supplied lines (≥2; balanced in the
 *                       base/reporting currency — 8th incr)
 */
public record PostManualJournalEntryCommand(
        String tenantId,
        String operatorSubject,
        String idempotencyKey,
        Instant postedAt,
        String reference,
        String memo,
        List<ManualLine> lines) {

    /**
     * One operator-supplied line. (8th incr) an optional {@code baseAmount}
     * (base/KRW currency) for a foreign-currency line; {@code null} → the line is
     * treated as base-currency ({@code baseAmount = money}, {@code rate = 1}).
     */
    public record ManualLine(String ledgerAccountCode, EntryDirection direction, Money money,
                             Money baseAmount) {
    }
}
