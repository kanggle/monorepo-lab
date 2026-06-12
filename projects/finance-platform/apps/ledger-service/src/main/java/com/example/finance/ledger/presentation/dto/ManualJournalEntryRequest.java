package com.example.finance.ledger.presentation.dto;

import com.example.finance.ledger.application.PostManualJournalEntryCommand;
import com.example.finance.ledger.application.PostManualJournalEntryCommand.ManualLine;
import com.example.finance.ledger.domain.journal.EntryDirection;

import java.time.Instant;
import java.util.List;

/**
 * Request body for the manual journal posting endpoint (ledger-api.md § 9, 5th
 * increment). {@code postedAt} / {@code reference} / {@code memo} are optional
 * operator narrative; {@code lines} MUST be ≥2 and balanced (the {@code JournalEntry}
 * factory re-asserts). Money is minor-units string (F5 — never a float; parsed via
 * {@link MoneyRequest#toMoney()}).
 *
 * <p>(8th incr — multi-currency) a line MAY carry an optional {@code baseAmount}
 * (the line's value in the base/KRW currency) when its {@code money} is foreign; a
 * base-currency (KRW) line omits it ({@code baseAmount} defaults to {@code money},
 * {@code rate = 1}). The balance is checked on the base amounts.
 */
public record ManualJournalEntryRequest(
        Instant postedAt,
        String reference,
        String memo,
        List<LineRequest> lines) {

    /**
     * One operator-supplied line. (8th incr) {@code baseAmount} is optional — present
     * for a foreign-currency line (its value in the base/KRW currency), omitted for a
     * base-currency line.
     */
    public record LineRequest(String ledgerAccountCode, EntryDirection direction,
                              MoneyRequest money, MoneyRequest baseAmount) {
    }

    /**
     * Map the validated request + the request-scoped fields (tenant, actor,
     * idempotency key) to the application command. The money parse surfaces an
     * unsupported currency / non-integer amount as a 422 via the handler. (8th incr)
     * an optional per-line {@code baseAmount} is parsed when present.
     */
    public PostManualJournalEntryCommand toCommand(String tenantId, String operatorSubject,
                                                   String idempotencyKey) {
        List<ManualLine> commandLines = lines == null ? List.of() : lines.stream()
                .map(l -> new ManualLine(l.ledgerAccountCode(), l.direction(), l.money().toMoney(),
                        l.baseAmount() == null ? null : l.baseAmount().toMoney()))
                .toList();
        return new PostManualJournalEntryCommand(
                tenantId, operatorSubject, idempotencyKey, postedAt, reference, memo, commandLines);
    }
}
