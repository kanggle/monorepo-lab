package com.example.account.domain.tenant;

/**
 * Lifecycle status of a tenant.
 *
 * <p>{@code ACTIVE} — tenant is operational; new logins and signups are allowed.
 * <p>{@code SUSPENDED} — tenant is frozen; new logins and signups are blocked.
 *    Existing access tokens remain valid until expiry (short TTL is the safety net).
 */
public enum TenantStatus {
    ACTIVE,
    SUSPENDED
}
