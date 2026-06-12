package com.example.finance.ledger.presentation.dto;

import com.example.finance.ledger.application.view.JournalEntryView;
import com.example.finance.ledger.domain.journal.JournalEntry;
import com.example.finance.ledger.domain.journal.JournalLine;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** GET /entries/{entryId} response (ledger-api.md § 1). */
public record JournalEntryResponse(
        String entryId,
        Instant postedAt,
        SourceResponse source,
        String reversalOfEntryId,
        List<LineResponse> lines,
        boolean balanced) {

    public record SourceResponse(String sourceType, String sourceTransactionId, String sourceEventId) {
    }

    /**
     * One line of the entry. (8th incr) carries the transaction {@code money}, the
     * {@code exchangeRate} (a decimal string — provenance), and the {@code baseAmount}
     * (the line's value in the base/KRW currency). A KRW line has
     * {@code exchangeRate == "1"} and {@code baseAmount == money}.
     */
    public record LineResponse(String ledgerAccountCode, String direction, MoneyResponse money,
                               String exchangeRate, MoneyResponse baseAmount) {
    }

    public static JournalEntryResponse from(JournalEntryView v) {
        List<LineResponse> lines = v.lines().stream()
                .map(l -> new LineResponse(l.ledgerAccountCode(), l.direction().name(),
                        MoneyResponse.from(l.money()),
                        rateString(l.exchangeRate()), MoneyResponse.from(l.baseAmount())))
                .toList();
        return new JournalEntryResponse(
                v.entryId(), v.postedAt(),
                new SourceResponse(v.sourceType(), v.sourceTransactionId(), v.sourceEventId()),
                v.reversalOfEntryId(), lines, v.balanced());
    }

    /** Render a rate as a plain decimal string with no trailing zeros ("1", "13.5"). */
    static String rateString(BigDecimal rate) {
        return rate.stripTrailingZeros().toPlainString();
    }

    /**
     * Build the § 1 entry shape from a freshly-posted domain entry (5th increment —
     * the manual {@code POST /entries} response, with {@code source.sourceType =
     * "MANUAL"}).
     */
    public static JournalEntryResponse from(JournalEntry entry) {
        List<LineResponse> lines = entry.lines().stream()
                .map(JournalEntryResponse::lineResponse)
                .toList();
        return new JournalEntryResponse(
                entry.entryId(), entry.postedAt(),
                new SourceResponse(entry.source().getSourceType(),
                        entry.source().getSourceTransactionId(),
                        entry.source().getSourceEventId()),
                entry.reversalOfEntryId(), lines, entry.isBalanced());
    }

    private static LineResponse lineResponse(JournalLine l) {
        return new LineResponse(l.ledgerAccountCode(), l.direction().name(),
                MoneyResponse.from(l.money()),
                rateString(l.exchangeRate()), MoneyResponse.from(l.baseMoney()));
    }
}
