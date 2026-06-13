package com.example.settlement.infrastructure.persistence;

/**
 * JPQL constructor-expression projection for a seller's aggregated balance.
 * {@code accrualCount} counts ledger rows (ACCRUAL + REVERSAL).
 */
public record SellerBalanceProjection(
        long accruedNetMinor,
        long platformCommissionMinor,
        long grossMinor,
        long accrualCount) {
}
