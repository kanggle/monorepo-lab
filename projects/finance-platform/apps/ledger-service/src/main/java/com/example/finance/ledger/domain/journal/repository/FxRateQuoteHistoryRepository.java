package com.example.finance.ledger.domain.journal.repository;

import com.example.finance.ledger.domain.journal.FxRateQuoteHistory;

import java.util.List;

/**
 * Outbound port for the FX rate quote history audit trail (26th increment — TASK-FIN-BE-039,
 * ADR-002 § 3.1 item 3). <b>Append-only</b>: one row is inserted per poll run per currency pair;
 * no update / delete path. Implemented by an infrastructure JPA adapter.
 *
 * <p>The history table ({@code fx_rate_quote_history}) mirrors the columns of the latest-only
 * {@code fx_rate_quote} cache plus a surrogate {@code id} (BIGINT AUTO_INCREMENT) so that many
 * rows can exist per pair over time.
 */
public interface FxRateQuoteHistoryRepository {

    /**
     * Append a history row (insert-only — never update or delete). Returns the persisted row
     * (with the surrogate {@code id} assigned by the database).
     */
    FxRateQuoteHistory append(FxRateQuoteHistory history);

    /** All history rows (ordering by {@code fetched_at} ascending) — for IT assertions. */
    List<FxRateQuoteHistory> findAll();
}
