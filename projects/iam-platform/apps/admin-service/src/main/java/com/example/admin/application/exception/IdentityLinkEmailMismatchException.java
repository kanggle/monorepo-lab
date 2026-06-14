package com.example.admin.application.exception;

/**
 * TASK-BE-373 / ADR-MONO-034 U3 — raised by the operator↔identity link operation
 * when the target account's email does NOT equal the operator's email.
 *
 * <p>Email-match is a <em>necessary</em> precondition for the link (§ 1.3): a
 * mismatch means the explicit link request is pointing at a different person, so
 * it is rejected. (Match alone is NOT sufficient — the explicit request is what
 * authorizes the link; matching email never auto-links.) Surfaces as
 * {@code 422 IDENTITY_LINK_EMAIL_MISMATCH}.
 */
public class IdentityLinkEmailMismatchException extends RuntimeException {
    public IdentityLinkEmailMismatchException(String message) {
        super(message);
    }
}
