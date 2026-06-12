package com.example.finance.ledger.presentation.dto;

import com.example.finance.ledger.application.view.AccountingPeriodView;
import com.example.finance.ledger.domain.period.PeriodBalanceSnapshot;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Accounting-period detail response (ledger-api.md § 6 / § 8) — the summary
 * fields plus the close-time {@code snapshot} (the trial-balance shape). The
 * snapshot is {@code null} while OPEN (omitted from JSON).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PeriodDetailResponse(
        String periodId,
        String status,
        Instant from,
        Instant to,
        Instant closedAt,
        String closedBy,
        Long entryCount,
        SnapshotResponse snapshot) {

    /** Close-time trial-balance snapshot (reuses the § 4 trial-balance shape). */
    public record SnapshotResponse(
            List<AccountTotalsResponse> accounts,
            MoneyResponse grandDebitTotal,
            MoneyResponse grandCreditTotal,
            boolean inBalance) {
    }

    public record AccountTotalsResponse(String ledgerAccountCode,
                                        MoneyResponse debitTotal, MoneyResponse creditTotal) {
    }

    public static PeriodDetailResponse from(AccountingPeriodView v) {
        return new PeriodDetailResponse(v.periodId(), v.status().name(), v.from(), v.to(),
                v.closedAt(), v.closedBy(), v.entryCount(), snapshotFrom(v.snapshot()));
    }

    private static SnapshotResponse snapshotFrom(PeriodBalanceSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        List<AccountTotalsResponse> accounts = snapshot.accounts().stream()
                .map(a -> new AccountTotalsResponse(a.ledgerAccountCode(),
                        MoneyResponse.from(a.debitTotal()), MoneyResponse.from(a.creditTotal())))
                .toList();
        return new SnapshotResponse(accounts,
                MoneyResponse.from(snapshot.grandDebitTotal()),
                MoneyResponse.from(snapshot.grandCreditTotal()),
                snapshot.inBalance());
    }
}
