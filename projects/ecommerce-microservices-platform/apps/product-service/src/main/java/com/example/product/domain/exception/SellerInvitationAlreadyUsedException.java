package com.example.product.domain.exception;

/**
 * The invitation was already accepted by another account (single use). Mapped to {@code 409 SELLER_INVITATION_ALREADY_USED} (product-api.md § Seller members, TASK-MONO-752).
 */
public class SellerInvitationAlreadyUsedException extends RuntimeException {

    public SellerInvitationAlreadyUsedException() {
        super("The invitation has already been used");
    }
}
