package com.example.finance.ledger.presentation.dto;

import com.example.finance.ledger.application.view.FxPositionLotView;

import java.time.Instant;

/**
 * Per-lot JSON response for the FX position lots read endpoint (20th increment —
 * TASK-FIN-BE-028). Money minor units are serialised as <b>strings</b> (F5 wire
 * form — {@code long} → {@code String} to avoid JSON integer precision loss for
 * large amounts); {@code acquiredAt} is an ISO-8601 instant.
 *
 * <p>All four monetary fields use the string form; zero-amount fields are included
 * (not omitted) to keep the shape predictable for the lot drill-in consumer.
 *
 * @param lotId                  unique lot identifier
 * @param currency               ISO-4217 code (e.g. {@code "USD"})
 * @param acquiredAt             lot acquisition instant (ISO-8601)
 * @param seq                    FIFO tiebreak sequence (the source journal line id)
 * @param originalForeignMinor   foreign quantity originally acquired (string integer)
 * @param remainingForeignMinor  still-open foreign quantity (string integer)
 * @param originalBaseMinor      KRW cost at acquisition (string integer)
 * @param carryingBaseMinor      current KRW carrying value (string integer)
 * @param sourceJournalEntryId   the entry that created this lot (provenance)
 */
public record FxPositionLotResponse(
        String lotId,
        String currency,
        Instant acquiredAt,
        long seq,
        String originalForeignMinor,
        String remainingForeignMinor,
        String originalBaseMinor,
        String carryingBaseMinor,
        String sourceJournalEntryId) {

    /** Factory from the application-layer view. */
    public static FxPositionLotResponse from(FxPositionLotView view) {
        return new FxPositionLotResponse(
                view.lotId(),
                view.currency(),
                view.acquiredAt(),
                view.seq(),
                Long.toString(view.originalForeignMinor()),
                Long.toString(view.remainingForeignMinor()),
                Long.toString(view.originalBaseMinor()),
                Long.toString(view.carryingBaseMinor()),
                view.sourceJournalEntryId());
    }
}
