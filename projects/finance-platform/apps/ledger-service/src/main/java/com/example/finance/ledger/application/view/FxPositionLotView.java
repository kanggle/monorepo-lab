package com.example.finance.ledger.application.view;

import com.example.finance.ledger.domain.journal.FxPositionLot;

import java.time.Instant;

/**
 * Per-lot read projection for one open FX acquisition lot (20th increment —
 * TASK-FIN-BE-028, architecture.md § FX position lots). Carries the four
 * monetary magnitudes as {@code long} minor units (the application layer never
 * serialises to strings; that is the DTO's responsibility per F5).
 *
 * @param lotId                  unique lot identifier (UUID)
 * @param currency               ISO-4217 code string (e.g. {@code "USD"})
 * @param acquiredAt             when this lot was acquired (the journal entry's
 *                               {@code postedAt})
 * @param seq                    FIFO tiebreak within the same {@code acquiredAt}
 *                               instant (the source journal line's IDENTITY id)
 * @param originalForeignMinor   foreign quantity originally acquired (positive)
 * @param remainingForeignMinor  still-open foreign quantity (positive, since only
 *                               open lots are returned)
 * @param originalBaseMinor      base-currency (KRW) cost at acquisition
 * @param carryingBaseMinor      current carrying value (re-marked by revaluation,
 *                               decremented by FIFO consumption)
 * @param sourceJournalEntryId   the entry that created this lot (provenance)
 */
public record FxPositionLotView(
        String lotId,
        String currency,
        Instant acquiredAt,
        long seq,
        long originalForeignMinor,
        long remainingForeignMinor,
        long originalBaseMinor,
        long carryingBaseMinor,
        String sourceJournalEntryId) {

    /** Project one open lot entity. */
    public static FxPositionLotView from(FxPositionLot lot) {
        return new FxPositionLotView(
                lot.lotId(),
                lot.currency().code(),
                lot.acquiredAt(),
                lot.seq(),
                lot.originalForeignMinor(),
                lot.remainingForeignMinor(),
                lot.originalBaseMinor(),
                lot.carryingBaseMinor(),
                lot.sourceJournalEntryId());
    }
}
