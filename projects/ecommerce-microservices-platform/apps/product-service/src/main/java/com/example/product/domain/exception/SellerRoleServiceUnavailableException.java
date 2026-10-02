package com.example.product.domain.exception;

/**
 * IAM did not answer the SELLER site-role grant; accept is fail-closed, so nothing is linked. Mapped to {@code 503 SERVICE_UNAVAILABLE} (product-api.md § Seller members, TASK-MONO-752).
 */
public class SellerRoleServiceUnavailableException extends RuntimeException {

    public SellerRoleServiceUnavailableException() {
        super("The account service is unavailable; nothing was linked");
    }
}
