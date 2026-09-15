package com.example.settlement.domain.repository;

import com.example.settlement.domain.model.PromotionCost;

import java.util.List;

/**
 * Persistence port for the append-only promotion-cost ledger (TASK-BE-592). Insert-only (F3).
 */
public interface PromotionCostRepository {

    /** Appends COST / REVERSAL rows (insert-only — never updates). */
    void appendAll(List<PromotionCost> costs);

    /**
     * All rows (COST and REVERSAL) of an order — the consume path, addressed by the
     * globally-unique {@code orderId} and therefore tenant-agnostic, like the accrual lookups.
     */
    List<PromotionCost> findByOrderId(String orderId);
}
