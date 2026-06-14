package com.example.finance.ledger.domain.journal.repository;

import com.example.finance.ledger.domain.journal.FxPositionLot;
import com.example.finance.ledger.domain.money.Currency;

import java.util.List;

/**
 * Outbound port for FX acquisition lot persistence (16th increment —
 * TASK-FIN-BE-024, architecture.md § FX position lots, ADR-001 D2). Lots are
 * insert-only in this increment ({@code save} is the only exercised method —
 * shadow / write-only). {@link #findOpenLots} is defined now (FIFO read for
 * FIN-BE-025) even though nothing reads it yet. Implemented by an infrastructure
 * JPA adapter.
 */
public interface FxPositionLotRepository {

    /** Persist a newly-acquired (or backfilled) lot. */
    FxPositionLot save(FxPositionLot lot);

    /**
     * The still-open lots ({@code remaining_foreign_minor > 0}) of one
     * {@code (tenant, ledgerAccountCode, currency)} position, ordered FIFO by
     * {@code (acquired_at, seq)} — the consumption order FIN-BE-025 will walk.
     * Empty when the position has no open lots.
     */
    List<FxPositionLot> findOpenLots(String tenantId, String ledgerAccountCode,
                                     Currency currency);
}
