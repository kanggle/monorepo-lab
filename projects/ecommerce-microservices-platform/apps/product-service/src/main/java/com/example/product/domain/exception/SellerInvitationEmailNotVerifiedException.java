package com.example.product.domain.exception;

/**
 * IAM refused the seller-member grant because the logged-in account names the invited address but has not
 * verified it (IAM {@code 403 EMAIL_NOT_VERIFIED}); nothing is linked and the invitation stays {@code PENDING} —
 * the person verifies through IAM and accepts the same invitation again. Mapped to
 * {@code 403 SELLER_INVITATION_EMAIL_NOT_VERIFIED} (product-api.md § accept, TASK-MONO-770 — ADR-MONO-080 D3 · R1).
 */
public class SellerInvitationEmailNotVerifiedException extends RuntimeException {

    public SellerInvitationEmailNotVerifiedException() {
        super("The logged-in account has not verified its email; verify it and accept the invitation again");
    }
}
