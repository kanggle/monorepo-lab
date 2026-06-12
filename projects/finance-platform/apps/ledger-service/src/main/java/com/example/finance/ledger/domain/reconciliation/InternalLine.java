package com.example.finance.ledger.domain.reconciliation;

import com.example.finance.ledger.domain.journal.EntryDirection;
import com.example.finance.ledger.domain.money.Money;

/**
 * A pure value carrier for one internal ledger line on the reconciled clearing
 * account, fed to the {@link ReconciliationMatcher} (architecture.md
 * § Reconciliation). It carries the owning {@code journalEntryId}, the
 * {@code ledgerAccountCode}, the {@link EntryDirection}, the transaction
 * {@link Money}, and (11th incr — TASK-FIN-BE-017, multi-currency reconciliation)
 * the line's carrying value in the base/reporting currency ({@code baseMoney}, KRW).
 * The infrastructure {@code ReconciliationRepository.findUnmatchedInternalLines}
 * builds these from journal lines whose entry is not already matched — {@code money}
 * from {@code JournalLine.money()} and {@code baseMoney} from
 * {@code JournalLine.baseMoney()} (a KRW line has {@code baseMoney == money}).
 * Pure Java.
 */
public record InternalLine(String journalEntryId, String ledgerAccountCode,
                           EntryDirection direction, Money money, Money baseMoney) {
}
