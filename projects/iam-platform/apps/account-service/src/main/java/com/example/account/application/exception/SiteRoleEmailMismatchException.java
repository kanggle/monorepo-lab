package com.example.account.application.exception;

/**
 * TASK-MONO-752 — a site-role grant refused because the caller's {@code expectedEmail} (the address its
 * invitation was sent to) is not the pool account's own email. Nothing is written. Maps to 403
 * {@code SITE_ROLE_EMAIL_MISMATCH} (consumer-site-roles.md rule 4 — ADR-MONO-034 § 1.3: an email is not
 * an identity until the logged-in account is that email's account).
 */
public class SiteRoleEmailMismatchException extends RuntimeException {

    public SiteRoleEmailMismatchException(String message) {
        super(message);
    }
}
