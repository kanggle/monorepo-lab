package com.example.finance.ledger.domain.journal.repository;

import com.example.finance.ledger.domain.journal.FxRateQuoteHistory;
import com.example.finance.ledger.domain.money.Currency;

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

    /**
     * Return the most-recent history rows for a currency pair, newest first
     * (27th increment — TASK-FIN-BE-040, ADR-002 history-read drill).
     *
     * <p>Ordering is {@code fetched_at DESC, id DESC} for deterministic tie-breaking
     * when two rows share the same {@code fetched_at} instant. Capped to {@code limit}
     * rows; the JPA adapter translates {@code limit} to a {@link org.springframework.data.domain.PageRequest}
     * (no {@code Page}/{@code Pageable} in this domain port — Spring-free boundary).
     * An unknown / never-polled pair returns an empty list (not an exception).
     *
     * @param base    the base currency (always {@link com.example.finance.ledger.domain.money.LedgerReportingCurrency#BASE} in v1)
     * @param foreign the foreign currency
     * @param limit   maximum number of rows to return (caller-normalised: 1 ≤ limit ≤ 500)
     * @return up to {@code limit} history rows, newest first; never {@code null}
     */
    List<FxRateQuoteHistory> findHistory(Currency base, Currency foreign, int limit);
}
