package com.example.auth.domain.tenant;

import java.util.Objects;

/**
 * Value object carrying the tenant context associated with a login or token operation.
 *
 * <p>auth-service owns its own tenant model snapshot so it does not import
 * account-service domain classes (architecture boundary rule).
 *
 * <p>Follows specs/features/multi-tenancy.md §Tenant Model.
 */
public record TenantContext(String tenantId, String tenantType) {

    /** Default tenant for the B2C fan-platform product. Used as fallback when tenant context is absent. */
    public static final String DEFAULT_TENANT_ID = "fan-platform";
    public static final String DEFAULT_TENANT_TYPE = "B2C_CONSUMER";

    /**
     * Platform-scope sentinel (ADR-002; multi-tenancy.md §Tenant Model). SUPER_ADMIN
     * cross-tenant operators carry {@code tenant_id = "*"} rather than a concrete tenant.
     * It is NOT a row in account-service's {@code tenants} table — see
     * {@link com.example.auth.infrastructure.tenant.TenantTypeResolver} for why the
     * {@code tenant_type} lookup must short-circuit on it (TASK-BE-466).
     */
    public static final String PLATFORM_SCOPE_TENANT_ID = "*";

    /**
     * The operator console's own tenant slug — the {@code oauth_clients.tenant_id} of the
     * {@code platform-console-web} client (seeded {@code gap} in V0015, renamed {@code iam} in
     * V0024) and a reserved slug no consumer tenant can register (multi-tenancy.md § TenantId).
     *
     * <p>TASK-BE-604 (owner decision D, 2026-09-26): the form-login cross-tenant credential
     * fallback is kept only for a login initiated by a client of THIS tenant. Operators whose
     * only credential lives in a consumer tenant (ADR-MONO-044 D5 self-onboarding: operator
     * {@code oidc_subject} = consumer {@code account_id}, no {@code iam} credential) reach the
     * console only through that fallback. Until now the value existed only in SQL migrations;
     * it is a constant here, not configuration, because it is the same kind of fact as
     * {@link #PLATFORM_SCOPE_TENANT_ID} — a reserved platform slug, not a per-deployment choice.
     */
    public static final String CONSOLE_TENANT_ID = "iam";

    /**
     * TASK-BE-614 (ADR-MONO-078 A; multi-tenancy.md § 소비자 계정 풀 § 1; jwt-standard-claims.md
     * {@code tenant_id} row): the reserved tenant the consumer-account POOL is stored under
     * ({@code accounts} / {@code credentials} rows with this tenant). 🔴 A storage value only — it
     * must NEVER be a token's {@code tenant_id}; a pool principal's token carries the requesting
     * client's SITE. The single home of this literal in auth-service: the issuer refuses to mint
     * it ({@code TenantClaimTokenCustomizer}) and the assume-tenant exchange refuses to select it
     * ({@code AssumeTenantAuthenticationProvider}).
     */
    public static final String CONSUMER_POOL_TENANT_ID = "consumer-pool";

    /** TASK-BE-614: {@code true} when {@code tenantId} is {@link #CONSUMER_POOL_TENANT_ID}. */
    public static boolean isConsumerPool(String tenantId) {
        return CONSUMER_POOL_TENANT_ID.equals(tenantId);
    }

    /**
     * TASK-BE-615 (multi-tenancy.md § 소비자 계정 풀 § 4) — whether a consumer-pool account is
     * considered for a client of {@code clientTenant}: every client tenant except the console
     * ({@link #CONSOLE_TENANT_ID} — contract D1, a pool account is not an operator) and the pool
     * itself. Whether that tenant is actually a consumer site, and whether the account is a member,
     * is account-service's answer; this is only the cheap, I/O-free pre-filter the form login, the
     * authorize gate, the session-tenant rule and the issuer share.
     */
    public static boolean poolPrincipalMapsTo(String clientTenant) {
        return clientTenant != null && !clientTenant.isBlank()
                && !CONSOLE_TENANT_ID.equals(clientTenant.trim())
                && !isConsumerPool(clientTenant.trim());
    }

    /**
     * TASK-BE-653 (auth-events.md {@code siteTenantId}) — the SITE a login of a consumer-pool account was
     * made to, for the optional {@code siteTenantId} of {@code auth.login.attempted/failed/succeeded}.
     *
     * <p>Non-null only when BOTH hold: the account's own tenant is {@link #CONSUMER_POOL_TENANT_ID}, and
     * there is an initiating client whose tenant a pool principal maps onto ({@link #poolPrincipalMapsTo} —
     * never the console {@code iam}, never the pool itself). Every other login — a non-pool account, a pool
     * login with no initiating client (operator-invitation acceptance, a direct visit), a pool operator through
     * the console — answers {@code null}. {@code initiatingClientTenant} must be the REAL client's tenant, never
     * a substitute such as the {@link #DEFAULT_TENANT_ID} fallback: that would put "no client" under a site.
     *
     * <p>{@code tenantId} on the events is unchanged (the account's own tenant); this is an additive field.
     */
    public static String siteTenantOfPoolLogin(String accountTenantId, String initiatingClientTenant) {
        if (!isConsumerPool(accountTenantId) || !poolPrincipalMapsTo(initiatingClientTenant)) {
            return null;
        }
        return initiatingClientTenant.trim();
    }

    public TenantContext {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        Objects.requireNonNull(tenantType, "tenantType must not be null");
        if (tenantType.isBlank()) {
            throw new IllegalArgumentException("tenantType must not be blank");
        }
    }

    /**
     * Returns the default tenant context (fan-platform / B2C_CONSUMER).
     */
    public static TenantContext defaultContext() {
        return new TenantContext(DEFAULT_TENANT_ID, DEFAULT_TENANT_TYPE);
    }

    // TASK-BE-407: the static resolveTenantType(tenantId) hardcoded fallback was
    // removed — it misclassified every non-"fan-platform" tenant as B2B_ENTERPRISE.
    // tenant_type is now resolved from account-service's authoritative
    // tenants.tenant_type via
    // com.example.auth.infrastructure.tenant.TenantTypeResolver. DEFAULT_TENANT_ID /
    // DEFAULT_TENANT_TYPE remain in use for defaultContext() and as the resolver's
    // pre-seeded cache entry for the B2C fan-platform hot path.
}
