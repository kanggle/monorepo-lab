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

    /**
     * Inserts a newly-opened period and <b>flushes immediately</b>, so a violation of
     * the partial unique index {@code (tenant_id, period_from, period_to) WHERE status
     * = 'OPEN'} (Flyway V6) is raised as a {@code DataIntegrityViolationException} at
     * this call rather than being deferred to transaction commit — where the caller's
     * {@code catch} could no longer translate it to a 409 (TASK-BE-535).
     */
    SettlementPeriod insertOpen(SettlementPeriod period);

    /** Loads a period by id within the given tenant ({@code empty} if absent / cross-tenant). */
    Optional<SettlementPeriod> findById(String periodId, String tenantId);

    /** Lists the tenant's periods, most recent first (by {@code period_to} desc). */
    List<SettlementPeriod> findAll(String tenantId);
}
