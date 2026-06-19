package com.example.auth.infrastructure.tenant;

import com.example.auth.application.exception.AccountServiceUnavailableException;
import com.example.auth.application.port.AccountServicePort;
import com.example.auth.application.port.TenantTypePort;
import com.example.auth.domain.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a tenant's authoritative {@code tenant_type} for the token-issuance hot
 * path (login / refresh / social-callback), replacing the hardcoded 2-value fallback
 * that previously lived in a static {@code TenantContext.resolveTenantType(String)}
 * method (now removed) and misclassified new B2C tenants (e.g. {@code ecommerce}) as
 * {@code B2B_ENTERPRISE} (TASK-BE-407).
 *
 * <p><b>Cache.</b> Cache-first; on a miss it calls
 * {@link AccountServicePort#getTenantType(String)}. The tenant set is small and
 * changes only on tenant provisioning, so a dependency-free unbounded
 * {@link ConcurrentHashMap} is sufficient — no eviction is needed (a tenant's type
 * is immutable for its lifetime, and the cardinality is bounded by the number of
 * provisioned tenants, a handful). This deliberately avoids adding Caffeine to the
 * classpath when a plain map matches the codebase's no-extra-dependency norm.
 *
 * <p><b>Pre-seed.</b> {@link TenantContext#DEFAULT_TENANT_ID} (the B2C
 * {@code fan-platform}) is pre-seeded to {@link TenantContext#DEFAULT_TENANT_TYPE}
 * so the dominant hot path performs <i>zero</i> network calls and never regresses.
 *
 * <p><b>Failure / not-found policy.</b>
 * <ul>
 *   <li>account-service down → {@link AccountServiceUnavailableException} propagates
 *       (fail-closed: the value must be authoritative; callers in the security path
 *       wrap it into an {@code AuthenticationServiceException}, see
 *       {@code CredentialAuthenticationProvider}).</li>
 *   <li>unknown tenant (404 → empty) → falls back to
 *       {@link TenantContext#DEFAULT_TENANT_TYPE} rather than silently misclassifying.
 *       This is a safe, explicit default (the previous hardcode silently produced
 *       {@code B2B_ENTERPRISE} for every non-fan tenant). The miss is NOT cached so a
 *       later-provisioned tenant resolves correctly on the next call.</li>
 * </ul>
 */
@Slf4j
@Component
public class TenantTypeResolver implements TenantTypePort {

    private final AccountServicePort accountServicePort;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public TenantTypeResolver(AccountServicePort accountServicePort) {
        this.accountServicePort = accountServicePort;
        // Pre-seed the default B2C tenant so the hot path never hits the network for it.
        this.cache.put(TenantContext.DEFAULT_TENANT_ID, TenantContext.DEFAULT_TENANT_TYPE);
    }

    /**
     * Resolves the {@code tenant_type} for {@code tenantId}, consulting the cache
     * first and querying account-service on a miss.
     *
     * @param tenantId the tenant whose type to resolve (must not be null/blank)
     * @return the authoritative tenant_type; {@link TenantContext#DEFAULT_TENANT_TYPE}
     *         when the tenant is unknown (404)
     * @throws AccountServiceUnavailableException if account-service is unavailable
     */
    @Override
    public String resolve(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return TenantContext.DEFAULT_TENANT_TYPE;
        }
        String cached = cache.get(tenantId);
        if (cached != null) {
            return cached;
        }
        // Miss: query account-service (propagates AccountServiceUnavailableException).
        Optional<String> resolved = accountServicePort.getTenantType(tenantId);
        if (resolved.isEmpty()) {
            // Unknown tenant — safe default, do NOT cache (tenant may be provisioned later).
            log.warn("tenant_type unknown for tenantId={} (account-service 404); "
                    + "defaulting to {}", tenantId, TenantContext.DEFAULT_TENANT_TYPE);
            return TenantContext.DEFAULT_TENANT_TYPE;
        }
        String tenantType = resolved.get();
        cache.put(tenantId, tenantType);
        return tenantType;
    }
}
