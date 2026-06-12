package com.example.finance.ledger.application.view;

import com.example.finance.ledger.domain.money.Money;
import com.example.finance.ledger.domain.reconciliation.DiscrepancyStatus;
import com.example.finance.ledger.domain.reconciliation.DiscrepancyType;
import com.example.finance.ledger.domain.reconciliation.ReconciliationDiscrepancy;
import com.example.finance.ledger.domain.reconciliation.ResolutionType;

import java.time.Instant;

/**
 * Read view of one {@link ReconciliationDiscrepancy} (reconciliation-api.md
 * § 1/§ 4/§ 5). Carries the recorded amounts (as {@link Money} minor units, F5),
 * the OPEN/RESOLVED status, and — when RESOLVED — the resolution record.
 */
public record DiscrepancyView(
        String discrepancyId,
        String ledgerAccountCode,
        DiscrepancyType type,
        String externalRef,
        String journalEntryId,
        Money expected,
        Money actual,
        DiscrepancyStatus status,
        ResolutionType resolutionType,
        String note,
        String resolvedBy,
        Instant resolvedAt,
        Instant detectedAt) {

    public static DiscrepancyView from(ReconciliationDiscrepancy d) {
        return new DiscrepancyView(
                d.discrepancyId(), d.ledgerAccountCode(), d.type(),
                d.externalRef(), d.journalEntryId(),
                Money.of(d.expectedMinor(), d.currency()),
                Money.of(d.actualMinor(), d.currency()),
                d.status(), d.resolutionType(), d.note(), d.resolvedBy(),
                d.resolvedAt(), d.detectedAt());
    }
}
