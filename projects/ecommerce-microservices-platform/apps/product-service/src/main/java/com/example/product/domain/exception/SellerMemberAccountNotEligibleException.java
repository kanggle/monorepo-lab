package com.example.product.domain.exception;

/**
 * The logged-in account cannot hold the store SELLER site role: it is not a consumer-pool account, or it has no ACTIVE store membership. Mapped to {@code 409 SELLER_MEMBER_ACCOUNT_NOT_ELIGIBLE} (product-api.md § Seller members, TASK-MONO-752).
 */
public class SellerMemberAccountNotEligibleException extends RuntimeException {

    public SellerMemberAccountNotEligibleException() {
        super("This account cannot become a seller member (not a consumer-pool account, or no store membership)");
    }
}
