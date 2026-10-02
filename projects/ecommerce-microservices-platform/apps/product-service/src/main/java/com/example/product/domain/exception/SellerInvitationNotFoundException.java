package com.example.product.domain.exception;

/**
 * The invitation token matches no invitation in this tenant. Mapped to {@code 404 SELLER_INVITATION_NOT_FOUND} (product-api.md § Seller members, TASK-MONO-752).
 */
public class SellerInvitationNotFoundException extends RuntimeException {

    public SellerInvitationNotFoundException() {
        super("Invitation not found");
    }
}
