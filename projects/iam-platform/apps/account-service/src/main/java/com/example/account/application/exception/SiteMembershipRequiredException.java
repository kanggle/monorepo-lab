package com.example.account.application.exception;

/**
 * TASK-MONO-752 — a site-role grant refused because the pool account holds no ACTIVE membership of the
 * site. The grant never creates one (membership = consent, ADR-MONO-078 D3). Maps to 409
 * {@code SITE_MEMBERSHIP_REQUIRED} (consumer-site-roles.md rule 5).
 */
public class SiteMembershipRequiredException extends RuntimeException {

    public SiteMembershipRequiredException(String message) {
        super(message);
    }
}
