package com.example.auth.domain.repository;

/**
 * Port interface for login failure counting (backed by Redis).
 */
public interface LoginAttemptCounter {

    /**
     * Returns the current failure count for the given tenant + email hash
     * ({@code login:fail:{tenant_id}:{email_hash}}, TASK-BE-229).
     * Returns 0 if Redis is unavailable (fail-open for counter reads).
     */
    int getFailureCount(String tenantId, String emailHash);

    /**
     * Increments the failure count and resets the TTL window
     * (tenant-aware key, TASK-BE-229).
     */
    void incrementFailureCount(String tenantId, String emailHash);

    /**
     * Clears the failure count on successful login (tenant-aware key).
     */
    void resetFailureCount(String tenantId, String emailHash);

    /**
     * Returns the remaining seconds until the failure-count window resets — the
     * Redis TTL on the counter key — for use as the {@code Retry-After} value on
     * a {@code LOGIN_RATE_LIMITED} 429 (TASK-BE-512; {@code rate-limiting.md}
     * "429 응답은 항상 Retry-After 포함"). Implementations must always return a
     * positive number of seconds, falling back to the full configured window
     * when the TTL is unknown, the key has no expiry, or Redis is unavailable.
     */
    long getTtlSeconds(String tenantId, String emailHash);

    /**
     * @deprecated since TASK-BE-295 — use {@link #getFailureCount(String, String)};
     * delegates to the default tenant ({@code TenantContext.DEFAULT_TENANT_ID}).
     */
    @Deprecated
    int getFailureCount(String emailHash);

    /**
     * @deprecated since TASK-BE-295 — use {@link #incrementFailureCount(String, String)};
     * delegates to the default tenant ({@code TenantContext.DEFAULT_TENANT_ID}).
     */
    @Deprecated
    void incrementFailureCount(String emailHash);

    /**
     * @deprecated since TASK-BE-295 — use {@link #resetFailureCount(String, String)};
     * delegates to the default tenant ({@code TenantContext.DEFAULT_TENANT_ID}).
     */
    @Deprecated
    void resetFailureCount(String emailHash);
}
