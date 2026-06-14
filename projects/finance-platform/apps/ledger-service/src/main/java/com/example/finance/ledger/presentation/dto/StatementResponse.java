package com.example.finance.ledger.presentation.dto;

import com.example.finance.ledger.application.view.StatementView;

import java.time.LocalDate;
import java.util.List;

/**
 * Statement ingest / detail response (reconciliation-api.md § 1 / § 3). Carries
 * the statement summary + match/discrepancy counts + the matches and OPEN
 * discrepancies. Money is minor-units string throughout (F5).
 */
public record StatementResponse(
        String statementId,
        String ledgerAccountCode,
        String source,
        LocalDate statementDate,
        int matchedCount,
        int discrepancyCount,
        List<MatchResponse> matches,
        List<DiscrepancyResponse> discrepancies) {

    /**
     * One recorded match (statement line ↔ internal journal entry). {@code crossCurrency}
     * (14th incr — TASK-FIN-BE-021) is {@code true} iff a base-currency (KRW) external
     * line matched a foreign internal line by its carrying base; {@code false} for every
     * same-currency match (additive field).
     */
    public record MatchResponse(String statementLineExternalRef, String journalEntryId,
                                MoneyResponse money, boolean crossCurrency) {
    }

    public static StatementResponse from(StatementView v) {
        List<MatchResponse> matches = v.matches().stream()
                .map(m -> new MatchResponse(m.statementLineExternalRef(), m.journalEntryId(),
                        MoneyResponse.from(m.money()), m.crossCurrency()))
                .toList();
        List<DiscrepancyResponse> discrepancies = v.discrepancies().stream()
                .map(DiscrepancyResponse::from)
                .toList();
        return new StatementResponse(
                v.statementId(), v.ledgerAccountCode(), v.source().name(), v.statementDate(),
                v.matchedCount(), v.discrepancyCount(), matches, discrepancies);
    }
}
