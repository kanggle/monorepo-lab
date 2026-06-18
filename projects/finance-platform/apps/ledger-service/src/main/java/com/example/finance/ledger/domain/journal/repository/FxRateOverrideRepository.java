package com.example.finance.ledger.domain.journal.repository;

import com.example.finance.ledger.domain.journal.FxRateOverride;
import com.example.finance.ledger.domain.money.Currency;

import java.util.Optional;

/**
 * Outbound port for the per-tenant FX contract-rate override (28th increment —
 * TASK-FIN-BE-042, ADR-002 § 3.1 per-tenant override / 특수 계약환율). One row per
 * {@code (tenant_id, base_currency, foreign_currency)}; an upsert is last-write-wins.
 * <b>Absence → no override</b> — {@code ResolveEffectiveFxRate} treats an empty lookup as
 * "fall through to the feed" (net-zero, today's behaviour). The lookup is tenant-scoped, so
 * tenant A's override never applies to tenant B. Implemented by an infrastructure JPA adapter.
 */
public interface FxRateOverrideRepository {

    /**
     * The tenant's contract-rate override for the {@code (base, foreign)} pair, or empty when
     * unset (→ fall through to the feed). Tenant-scoped — the {@code tenantId} is part of the key.
     */
    Optional<FxRateOverride> findOverride(String tenantId, Currency base, Currency foreign);

    /** Upsert the tenant's override (insert-or-update on the {@code (tenant, base, foreign)} PK). */
    FxRateOverride save(FxRateOverride override);
}
