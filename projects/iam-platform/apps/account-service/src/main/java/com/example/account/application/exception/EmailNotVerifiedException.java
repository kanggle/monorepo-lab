package com.example.account.application.exception;

/**
 * TASK-MONO-770 (ADR-MONO-080 D3 · rider R1) — a write that would attach a company role to a pool account was
 * refused because the account has not verified its email. Nothing is written. Maps to 403
 * {@code EMAIL_NOT_VERIFIED}.
 *
 * <p>Thrown only by {@link com.example.account.application.service.VerifiedEmailRequirement} — the one home of
 * the predicate, so every caller (today the site-role grant; TASK-MONO-772 the operator-invitation acceptance)
 * refuses the same way.
 */
public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException(String message) {
        super(message);
    }
}
