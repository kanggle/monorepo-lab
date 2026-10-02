package com.example.account.application.exception;

/**
 * TASK-MONO-752 — a site-role grant/revoke for a (site, role) pair outside the closed grantable list
 * ({@link com.example.account.domain.consumerpool.GrantableSiteRoles}), or on a tenant that is not a consumer
 * site. Nothing is written. Maps to 400 {@code SITE_ROLE_NOT_GRANTABLE} (consumer-site-roles.md).
 */
public class SiteRoleNotGrantableException extends RuntimeException {

    public SiteRoleNotGrantableException(String message) {
        super(message);
    }
}
