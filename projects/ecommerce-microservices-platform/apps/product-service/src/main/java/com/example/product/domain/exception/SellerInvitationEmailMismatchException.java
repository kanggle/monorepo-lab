package com.example.product.domain.exception;

/**
 * IAM verified that the logged-in account's email is not the invitation's email; nothing is linked. Mapped to {@code 403 SELLER_INVITATION_EMAIL_MISMATCH} (product-api.md § Seller members, TASK-MONO-752).
 */
public class SellerInvitationEmailMismatchException extends RuntimeException {

    public SellerInvitationEmailMismatchException() {
        super("The logged-in account is not the invited email's account");
    }
}
