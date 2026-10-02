package com.example.product.domain.exception;

/**
 * An invitation was issued or accepted for a seller that is not ACTIVE. Mapped to {@code 409 SELLER_NOT_ACTIVE} (product-api.md § Seller members, TASK-MONO-752).
 */
public class SellerNotActiveException extends RuntimeException {

    public SellerNotActiveException() {
        super("The seller is not ACTIVE");
    }
}
