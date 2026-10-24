package com.example.finance.account.domain.transaction.repository;

import java.time.Instant;
import java.util.List;

/**
 * Outbound port for the overview performance metrics (TASK-MONO-812, ADR-MONO-084 P7).
 * Tenant-scoped, half-open window {@code [from, to)}; the rows are raw — the application
 * service applies the inclusion rule (type · status), sums per currency and buckets into KST
 * days ({@code account-api.md} § GET /api/finance/accounts/metrics).
 */
public interface TransactionMetricsQuery {

    /**
     * Every transaction of the tenant whose {@code settled_at} ∈ {@code [from, to)},
     * <b>whatever its type or current status</b>. A row never settled (REQUESTED … FAILED —
     * {@code settled_at IS NULL}) never matches.
     */
    List<SettledTransactionRow> settledIn(String tenantId, Instant from, Instant to);
}
