package com.example.finance.ledger.application.view;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Per-row read projection of one FX rate history audit trail entry (27th increment —
 * TASK-FIN-BE-040, ADR-002 history-read drill). Carries the exact decimal rate as
 * {@link BigDecimal} (application layer never serialises to strings — that is the DTO's
 * responsibility per F5). No staleness fields: history rows are raw provenance, not a
 * live-cache freshness check.
 *
 * @param rate      exact decimal rate (base-minor per foreign-minor, same unit as the cache)
 * @param asOf      provider-stated rate instant (the provenance basis)
 * @param fetchedAt when the quote was pulled from the provider
 * @param source    provider identifier (audit provenance)
 */
public record FxRateHistoryView(
        BigDecimal rate,
        Instant asOf,
        Instant fetchedAt,
        String source) {
}
