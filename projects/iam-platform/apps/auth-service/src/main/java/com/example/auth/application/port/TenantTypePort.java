package com.example.auth.application.port;

/**
 * Port for resolving a tenant's authoritative {@code tenant_type} on the
 * token-issuance hot path (login / refresh / social-callback) — TASK-BE-407.
 *
 * <p>The application layer depends on this interface; the concrete cache-first
 * implementation lives in
 * {@link com.example.auth.infrastructure.tenant.TenantTypeResolver} (mirroring the
 * {@link AccountServicePort} → {@code AccountServiceClient} arrangement). This keeps
 * the strict layered boundary intact: {@code application} must not import
 * {@code infrastructure} classes directly.</p>
 *
 * <p>Replaces the previous hardcoded 2-value fallback that misclassified new B2C
 * tenants (e.g. {@code ecommerce}) as {@code B2B_ENTERPRISE}.</p>
 */
public interface TenantTypePort {

    /**
     * Resolves the {@code tenant_type} for {@code tenantId}.
     *
     * <p>Implementations consult a cache first and query account-service on a miss.
     * An unknown tenant (account-service 404) yields the default tenant type rather
     * than a silent misclassification; an account-service outage propagates as
     * {@link com.example.auth.application.exception.AccountServiceUnavailableException}
     * (fail-closed — the value must be authoritative).</p>
     *
     * @param tenantId the tenant whose type to resolve (null/blank yields the default
     *                 tenant type)
     * @return the authoritative tenant_type string, or the default tenant type when
     *         the tenant is unknown
     * @throws com.example.auth.application.exception.AccountServiceUnavailableException
     *         if account-service is unavailable
     */
    String resolve(String tenantId);
}
