package com.example.finance.ledger.presentation.dto;

import com.example.finance.ledger.application.SettleForeignPositionUseCase.Result;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response body for the FX settlement endpoint (ledger-api.md § 11, 10th increment —
 * TASK-FIN-BE-016). A booked settlement ({@code settled=true}) carries the signed
 * {@code realizedBaseMinor} + {@code proceedsBaseMinor} (minor-unit strings, F5), the
 * {@code outcome} ({@code FX_GAIN}/{@code FX_LOSS}/{@code NONE}), the residual OPEN
 * position {@code residualForeignMinor} + {@code residualCarryingBaseMinor} left after a
 * partial settle (both {@code "0"} on a full settle — 12th increment, TASK-FIN-BE-018),
 * and the posted {@code entry}. A no-op/replay ({@code settled=false}) carries the
 * {@code reason} ({@code NO_POSITION}/{@code REPLAY}) and, on a replay, the original
 * {@code entry}. Null fields are omitted.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SettlementResponse(
        boolean settled,
        String realizedBaseMinor,
        String proceedsBaseMinor,
        String outcome,
        String residualForeignMinor,
        String residualCarryingBaseMinor,
        String reason,
        JournalEntryResponse entry) {

    public static SettlementResponse from(Result result) {
        if (result.settled()) {
            return new SettlementResponse(true,
                    Long.toString(result.realizedBaseMinor()),
                    Long.toString(result.proceedsBaseMinor()),
                    result.outcome().name(),
                    Long.toString(result.residualForeignMinor()),
                    Long.toString(result.residualCarryingBaseMinor()), null,
                    JournalEntryResponse.from(result.entry()));
        }
        JournalEntryResponse entry = result.entry() == null
                ? null : JournalEntryResponse.from(result.entry());
        return new SettlementResponse(false, null, null, null, null, null,
                result.reason().name(), entry);
    }
}
