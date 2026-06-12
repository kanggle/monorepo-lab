package com.example.finance.ledger.presentation.dto;

import com.example.finance.ledger.application.RevalueForeignBalanceUseCase.Result;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response body for the FX revaluation endpoint (ledger-api.md § 10, 9th increment —
 * TASK-FIN-BE-015). A booked revaluation ({@code revalued=true}) carries the signed
 * {@code deltaBaseMinor} (minor-unit string, F5), the {@code outcome}
 * ({@code FX_GAIN}/{@code FX_LOSS}), and the posted {@code entry}. A no-op/replay
 * ({@code revalued=false}) carries the {@code reason}
 * ({@code AT_SPOT}/{@code NO_POSITION}/{@code REPLAY}) and, on a replay, the original
 * {@code entry}. Null fields are omitted.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RevaluationResponse(
        boolean revalued,
        String deltaBaseMinor,
        String outcome,
        String reason,
        JournalEntryResponse entry) {

    public static RevaluationResponse from(Result result) {
        if (result.revalued()) {
            return new RevaluationResponse(true,
                    Long.toString(result.deltaBaseMinor()),
                    result.outcome().name(), null,
                    JournalEntryResponse.from(result.entry()));
        }
        JournalEntryResponse entry = result.entry() == null
                ? null : JournalEntryResponse.from(result.entry());
        return new RevaluationResponse(false, null, null, result.reason().name(), entry);
    }
}
