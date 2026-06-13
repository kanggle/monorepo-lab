package com.example.settlement.domain.model;

/**
 * A seller's aggregated settlement balance within one tenant: the currently
 * settleable net ({@code Σ sellerNetMinor}, ACCRUAL − REVERSAL), the platform's
 * commission ({@code Σ commissionMinor}), the gross, and the ledger row count.
 * A fully refunded order nets these back to their pre-order values.
 */
public record SellerBalance(
        String sellerId,
        long accruedNetMinor,
        long platformCommissionMinor,
        long grossMinor,
        long accrualCount) {

    public static SellerBalance empty(String sellerId) {
        return new SellerBalance(sellerId, 0L, 0L, 0L, 0L);
    }
}
