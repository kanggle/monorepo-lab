package com.example.settlement.domain.repository;

import com.example.settlement.domain.payout.SellerPayout;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for the {@link SellerPayout} aggregate. Payouts are created
 * (PENDING) at period close; the read/execute surface is TASK-BE-416.
 */
public interface SellerPayoutRepository {

    /** Persists the period's payout rows (insert at close). */
    List<SellerPayout> saveAll(List<SellerPayout> payouts);

    /**
     * Saves (updates) a single payout row — used by the execute use-case to
     * persist the PENDING→PAID|FAILED transition + {@code payout_reference} /
     * {@code paid_at} stamps (TASK-BE-416).
     */
    SellerPayout save(SellerPayout payout);

    /**
     * All payout rows for a period, tenant-scoped (M1). Returns rows in any
     * status (PENDING / PAID / FAILED). Used by the execute use-case (fetch
     * PENDING rows) and the read API (list all statuses, with seller-scope
     * ABAC applied in the use-case layer).
     *
     * @param periodId the period to query
     * @param tenantId the caller's tenant — cross-tenant results are excluded
     * @return payout rows belonging to the period in the given tenant
     */
    List<SellerPayout> findByPeriodAndTenant(String periodId, String tenantId);
}
