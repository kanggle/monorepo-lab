package com.example.product.domain.exception;

/**
 * The invitation is past its expiry. Mapped to {@code 410 SELLER_INVITATION_EXPIRED} (product-api.md § Seller members, TASK-MONO-752).
 */
public class SellerInvitationExpiredException extends RuntimeException {

    public SellerInvitationExpiredException() {
        super("The invitation has expired");
    }
}
