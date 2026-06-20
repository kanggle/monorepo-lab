package com.example.settlement.domain.repository;

import com.example.settlement.domain.period.SettlementPeriod;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for the {@link SettlementPeriod} aggregate. Reads are
 * tenant-scoped at the repository chokepoint (a cross-tenant {@code periodId}
 * resolves to {@link Optional#empty()} → 404, M3).
 */
public interface SettlementPeriodRepository {

    /** Persists a new or mutated period (open / close). */
    SettlementPeriod save(SettlementPeriod period);

    /** Loads a period by id within the given tenant ({@code empty} if absent / cross-tenant). */
    Optional<SettlementPeriod> findById(String periodId, String tenantId);

    /** Lists the tenant's periods, most recent first (by {@code period_to} desc). */
    List<SettlementPeriod> findAll(String tenantId);
}
