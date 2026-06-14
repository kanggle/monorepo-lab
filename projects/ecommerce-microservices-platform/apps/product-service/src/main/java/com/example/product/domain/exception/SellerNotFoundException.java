package com.example.product.domain.exception;

/**
 * Raised when a seller cannot be resolved within the current tenant — either it
 * does not exist or it belongs to another tenant (cross-tenant lookups are hidden,
 * not 403'd, per ADR-MONO-030 Step 3 M3). Mapped to {@code 404 SELLER_NOT_FOUND}.
 */
public class SellerNotFoundException extends RuntimeException {

    public SellerNotFoundException(String sellerId) {
        super("Seller not found: " + sellerId);
    }
}
