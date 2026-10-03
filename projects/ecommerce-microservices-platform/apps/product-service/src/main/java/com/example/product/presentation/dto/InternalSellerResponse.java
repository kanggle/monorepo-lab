package com.example.product.presentation.dto;

import com.example.product.application.dto.SellerSummary;

/**
 * {@code GET /internal/sellers/{sellerId}} body (TASK-MONO-759, {@code product-api.md} § Internal
 * seller read). Deliberately the minimum the fan caller needs — id and status — so the internal
 * surface exposes nothing the operator plane does not need to share across projects.
 */
public record InternalSellerResponse(String sellerId, String status) {

    public static InternalSellerResponse from(SellerSummary seller) {
        return new InternalSellerResponse(seller.sellerId(), seller.status().name());
    }
}
