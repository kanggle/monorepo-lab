package com.example.account.infrastructure.config;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * TASK-BE-640 — which workload may call the tenant lifecycle API ({@code POST}/{@code GET /internal/tenants},
 * {@code GET}/{@code PATCH /internal/tenants/{tenantId}}), judged by the token {@code sub}.
 *
 * <h2>Why</h2>
 * <p>{@code /internal/**} already requires the {@code internal.invoke} scope (TASK-BE-514). That scope says
 * «a system workload», not «which one» — and six clients hold it, including a tenant workload
 * ({@code product-service-client}). The iam gateway no longer routes these paths (TASK-BE-638), but
 * account-service can be reached directly (in the demo it also sits on {@code traefik-net}). Without this
 * check, any {@code internal.invoke} holder could create tenants, list every tenant, or re-status one
 * ({@code SUSPENDED → ACTIVE}, {@code PENDING → ACTIVE} — skipping admin-service's first-admin gate).
 *
 * <h2>The discriminator</h2>
 * <p>On a {@code client_credentials} token, and on a workload assume-tenant token, {@code sub} is the client
 * id ({@code jwt-standard-claims.md}: «Its {@code sub} remains the client»). An identity-bearing token's
 * {@code sub} is an account UUID, which is never a client id. This is the «subject allowlist» that the
 * same contract's JWT Validation rule 5, <i>Behind the edge</i>, names as a resource server's workload
 * discriminator.
 *
 * <h2>Fail-closed</h2>
 * <p>An empty list admits no JWT caller — {@link #fromCsv(String, boolean)} turns an absent or blank value
 * into an empty list. A mis-configuration therefore surfaces as {@code 403}, never as an open API.
 *
 * <h2>The dev bypass</h2>
 * <p>Under the {@code test}/{@code standalone} bypass, {@link InternalApiFilter} authenticates
 * {@code /internal/**} without a JWT. That principal is admitted <b>only when the bypass flag this policy was
 * built with is on</b> — the flag {@link SecurityConfig} computes, which is off in every real profile.
 * A request that presents a JWT is judged by the list even when the bypass is on (the bearer filter
 * replaces the bypass principal).
 */
public final class TenantLifecycleCallerPolicy implements AuthorizationManager<RequestAuthorizationContext> {

    private final Set<String> allowedClientIds;
    private final boolean bypassActive;

    TenantLifecycleCallerPolicy(Set<String> allowedClientIds, boolean bypassActive) {
        this.allowedClientIds = Set.copyOf(allowedClientIds);
        this.bypassActive = bypassActive;
    }

    /** Parses a comma-separated client-id list; {@code null}/blank → empty (refuse every JWT caller). */
    static TenantLifecycleCallerPolicy fromCsv(String csv, boolean bypassActive) {
        Set<String> ids = (csv == null || csv.isBlank())
                ? Set.of()
                : Arrays.stream(csv.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toUnmodifiableSet());
        return new TenantLifecycleCallerPolicy(ids, bypassActive);
    }

    Set<String> allowedClientIds() {
        return allowedClientIds;
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        return new AuthorizationDecision(allows(authentication.get()));
    }

    boolean allows(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (authentication instanceof JwtAuthenticationToken jwt) {
            String subject = jwt.getToken().getSubject();
            return subject != null && allowedClientIds.contains(subject);
        }
        return bypassActive
                && authentication instanceof PreAuthenticatedAuthenticationToken
                && InternalApiFilter.INTERNAL_PRINCIPAL.equals(authentication.getName());
    }
}
