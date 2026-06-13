package com.example.settlement.domain.repository;

import java.util.Optional;

/**
 * Persistence port for per-seller commission-rate overrides keyed by
 * {@code (tenant_id, seller_id)}. A missing row → the caller falls back to the
 * platform default (see {@code CommissionRateResolver}). Setting a rate is
 * prospective (it never rewrites booked accruals — F3).
 */
public interface CommissionRateRepository {

    /** The per-seller override bps for {@code (tenantId, sellerId)}, if set. */
    Optional<Integer> findRateBps(String tenantId, String sellerId);

    /** Upserts the per-seller override (idempotent on the composite key). */
    void upsert(String tenantId, String sellerId, int rateBps);
}
