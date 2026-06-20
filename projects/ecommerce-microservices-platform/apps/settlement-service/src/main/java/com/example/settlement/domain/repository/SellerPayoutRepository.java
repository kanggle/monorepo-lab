package com.example.settlement.domain.repository;

import com.example.settlement.domain.payout.SellerPayout;

import java.util.List;

/**
 * Persistence port for the {@link SellerPayout} aggregate. Payouts are created
 * (PENDING) at period close; the read/execute surface is TASK-BE-416.
 */
public interface SellerPayoutRepository {

    /** Persists the period's payout rows (insert at close). */
    List<SellerPayout> saveAll(List<SellerPayout> payouts);
}
