package com.example.settlement.presentation.dto;

import com.example.settlement.domain.model.SellerBalance;

import java.time.Instant;

/** {@code GET /sellers/{sellerId}/balance} response (settlement-api.md). */
public record SellerBalanceResponse(
        String sellerId,
        long accruedNetMinor,
        long platformCommissionMinor,
        long grossMinor,
        long accrualCount,
        String asOf) {

    public static SellerBalanceResponse from(SellerBalance b) {
        return new SellerBalanceResponse(
                b.sellerId(), b.accruedNetMinor(), b.platformCommissionMinor(),
                b.grossMinor(), b.accrualCount(), Instant.now().toString());
    }
}
