package com.example.auth.domain.repository;

/**
 * Port interface for refresh token blacklist (backed by Redis).
 */
public interface TokenBlacklist {

    /**
     * Adds a refresh token JTI to the blacklist under the tenant-aware key
     * ({@code refresh:blacklist:{tenant_id}:{jti}}, TASK-BE-229).
     *
     * @param tenantId   the tenant_id from the refresh token
     * @param jti        the JWT ID of the refresh token
     * @param ttlSeconds TTL matching the token's remaining lifetime
     */
    void blacklist(String tenantId, String jti, long ttlSeconds);

    /**
     * Checks if a refresh token JTI is blacklisted under the tenant-aware key,
     * with a read-only fallback to the legacy {@code refresh:blacklist:{jti}}
     * key for tokens issued before TASK-BE-229.
     * Returns true if blacklisted OR if Redis is unavailable (fail-closed).
     *
     * @param tenantId the tenant_id from the refresh token
     * @param jti      the JWT ID of the refresh token
     */
    boolean isBlacklisted(String tenantId, String jti);

    /**
     * @deprecated since TASK-BE-295 — use {@link #blacklist(String, String, long)}.
     * Retained for the BE-229 default-tenant fallback; delegates to the
     * default tenant ({@code TenantContext.DEFAULT_TENANT_ID}).
     */
    @Deprecated
    void blacklist(String jti, long ttlSeconds);

    /**
     * @deprecated since TASK-BE-295 — use {@link #isBlacklisted(String, String)}.
     * Retained for the BE-229 default-tenant fallback; delegates to the
     * default tenant ({@code TenantContext.DEFAULT_TENANT_ID}).
     */
    @Deprecated
    boolean isBlacklisted(String jti);
}
