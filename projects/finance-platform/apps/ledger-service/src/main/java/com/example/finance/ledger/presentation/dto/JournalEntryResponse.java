package com.example.finance.ledger.presentation.dto;

import com.example.finance.ledger.application.view.JournalEntryView;

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

    public record LineResponse(String ledgerAccountCode, String direction, MoneyResponse money) {
    }

    public static JournalEntryResponse from(JournalEntryView v) {
        List<LineResponse> lines = v.lines().stream()
                .map(l -> new LineResponse(l.ledgerAccountCode(), l.direction().name(),
                        MoneyResponse.from(l.money())))
                .toList();
        return new JournalEntryResponse(
                v.entryId(), v.postedAt(),
                new SourceResponse(v.sourceType(), v.sourceTransactionId(), v.sourceEventId()),
                v.reversalOfEntryId(), lines, v.balanced());
    }
}
