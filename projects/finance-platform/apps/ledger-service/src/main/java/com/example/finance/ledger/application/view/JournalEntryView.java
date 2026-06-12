package com.example.finance.ledger.application.view;

import java.time.Instant;
import java.util.List;

/** A journal entry + its lines for the read API (ledger-api.md § 1). */
public record JournalEntryView(
        String entryId,
        Instant postedAt,
        String sourceType,
        String sourceTransactionId,
        String sourceEventId,
        String reversalOfEntryId,
        List<JournalLineView> lines,
        boolean balanced) {
}
