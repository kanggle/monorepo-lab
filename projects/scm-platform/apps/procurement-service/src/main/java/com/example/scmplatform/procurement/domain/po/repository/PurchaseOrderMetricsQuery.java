package com.example.scmplatform.procurement.domain.po.repository;

import java.time.Instant;
import java.util.List;

/**
 * Outbound port for the overview performance metrics (TASK-MONO-810, ADR-MONO-084 P5).
 * Every method is tenant-scoped and reads a half-open window {@code [from, to)}; the rows
 * are raw — the application service applies the exclusions, sums per currency and buckets
 * into KST days ({@code procurement-api.md} § GET /api/procurement/po/metrics).
 */
public interface PurchaseOrderMetricsQuery {

    /**
     * Every PO of the tenant whose {@code submitted_at} ∈ {@code [from, to)}, <b>whatever its
     * current status</b> (a {@code DRAFT} has no {@code submitted_at} and never matches).
     */
    List<SubmittedPoRow> submittedIn(String tenantId, Instant from, Instant to);

    /**
     * Every status-history row of the tenant with {@code to_status = RECEIVED} and
     * {@code occurred_at} ∈ {@code [from, to)} — one row per transition, not per PO.
     */
    List<ReceivedTransitionRow> receivedIn(String tenantId, Instant from, Instant to);
}
