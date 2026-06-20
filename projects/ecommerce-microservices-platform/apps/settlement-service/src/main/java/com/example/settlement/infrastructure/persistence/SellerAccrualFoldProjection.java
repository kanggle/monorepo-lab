package com.example.settlement.infrastructure.persistence;

/**
 * JPQL constructor-expression projection for the per-seller period-close fold over
 * {@code commission_accrual}. {@code accrualCount} is the {@code COUNT(*)} of folded
 * rows (returned as {@code long} from JPQL; narrowed to {@code int} at the domain
 * boundary).
 */
public record SellerAccrualFoldProjection(
        String sellerId,
        long payableNetMinor,
        long commissionMinor,
        long accrualCount) {
}
