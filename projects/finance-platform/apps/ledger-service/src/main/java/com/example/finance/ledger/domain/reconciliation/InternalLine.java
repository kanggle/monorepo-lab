package com.example.finance.ledger.domain.reconciliation;

import com.example.finance.ledger.domain.journal.EntryDirection;
import com.example.finance.ledger.domain.money.Money;

/**
 * A pure value carrier for one internal ledger line on the reconciled clearing
 * account, fed to the {@link ReconciliationMatcher} (architecture.md
 * § Reconciliation). It carries the owning {@code journalEntryId}, the
 * {@code ledgerAccountCode}, the {@link EntryDirection}, and the {@link Money}.
 * The infrastructure {@code ReconciliationRepository.findUnmatchedInternalLines}
 * builds these from journal lines whose entry is not already matched. Pure Java.
 */
public record InternalLine(String journalEntryId, String ledgerAccountCode,
                           EntryDirection direction, Money money) {
}
