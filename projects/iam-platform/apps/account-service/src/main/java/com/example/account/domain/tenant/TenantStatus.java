package com.example.account.domain.tenant;

/**
 * Lifecycle status of a tenant.
 *
 * <p>{@code ACTIVE} — tenant is operational; new logins and signups are allowed.
 * <p>{@code SUSPENDED} — tenant is frozen; new logins and signups are blocked.
 *    Existing access tokens remain valid until expiry (short TTL is the safety net).
 * <p>{@code PENDING} — TASK-MONO-773 (OD-1): «관리자 대기». A tenant created by SUPER_ADMIN waits
 *    here until its first admin accepts the invitation. It is not active: every reader that asks
 *    {@link Tenant#isActive()} refuses it exactly as it refuses SUSPENDED. It only ever arises at
 *    creation — it is never a requested transition target
 *    ({@code admin-to-account.md} § Tenant lifecycle — 773 변경분).
 */
public enum TenantStatus {
    ACTIVE,
    SUSPENDED,
    PENDING
}
