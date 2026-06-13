package com.example.settlement.application.exception;

/**
 * Thrown when a seller-scoped operator targets a seller outside their bound scope
 * (or a resource in another tenant). Surfaces as HTTP 404 {@code SETTLEMENT_NOT_FOUND}
 * — 404-over-403 so cross-tenant / cross-seller existence is never disclosed (M3).
 */
public class SellerScopeForbiddenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SellerScopeForbiddenException(String sellerId) {
        super("seller not found in caller's scope: " + sellerId);
    }
}
