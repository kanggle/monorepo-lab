package com.example.finance.ledger.domain.journal.repository;

import com.example.finance.ledger.domain.journal.FxRateQuote;
import com.example.finance.ledger.domain.money.Currency;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for the FX rate quote cache (23rd increment — TASK-FIN-BE-031, ADR-002 D2).
 * One row per currency pair; an upsert is last-write-wins. <b>Shadow / net-zero</b>: only the
 * scheduled poller writes here; no operator path reads it in this increment. Implemented by an
 * infrastructure JPA adapter.
 */
public interface FxRateQuoteRepository {

    /** The latest cached quote for the pair, or empty when none has been fetched yet. */
    Optional<FxRateQuote> findLatest(Currency base, Currency foreign);

    /** Upsert a quote (insert-or-update on the {@code (base_currency, foreign_currency)} PK). */
    FxRateQuote save(FxRateQuote quote);

    /** All cached quotes (ordering unspecified) — for IT assertions / diagnostics. */
    List<FxRateQuote> findAll();
}
