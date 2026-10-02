package com.example.account.application.exception;

/**
 * TASK-MONO-752 — a site-role grant/revoke on an account of the site itself, not a consumer-pool
 * account. A site account cannot hold {@code consumer_site_roles}. Maps to 409
 * {@code SITE_ROLE_REQUIRES_POOL_ACCOUNT} (consumer-site-roles.md rule 3).
 */
public class SiteRoleRequiresPoolAccountException extends RuntimeException {

    public SiteRoleRequiresPoolAccountException(String message) {
        super(message);
    }
}
