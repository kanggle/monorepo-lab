package com.example.scmplatform.gateway.filter;

import com.example.scmplatform.gateway.security.TenantClaimValidator;
import java.util.Collection;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Adds verified identity headers derived from the authenticated JWT before the
 * request is routed downstream:
 *
 * <ul>
 *   <li>{@code X-User-Id} ← {@code sub}</li>
 *   <li>{@code X-Account-Id} ← {@code sub} (alias used by scm-platform downstream services)</li>
 *   <li>{@code X-Actor-Id} ← {@code sub}</li>
 *   <li>{@code X-User-Email} ← {@code email} (when present)</li>
 *   <li>{@code X-User-Role} / {@code X-Roles} ← {@code role}/{@code roles}</li>
 *   <li>{@code X-Tenant-Id} ← {@code tenant_id}</li>
 *   <li>{@code X-Account-Type} ← {@code account_type} (when present)</li>
 *   <li>{@code X-Scopes} ← {@code scope} (string, space-delimited per RFC 6749)</li>
 *   <li>{@code X-Token-Type} ← {@code "client_credentials"} when {@code email} claim is
 *       absent and {@code sub} matches the client_id (machine token), else
 *       {@code "user"} — this lets downstream services distinguish human users
 *       from service-to-service callers per Edge Case E1.</li>
 * </ul>
 *
 * <p>This filter satisfies both the "TenantGateFilter" and "HeaderEnrichmentFilter"
 * roles described in TASK-SCM-BE-001. Tenant gating itself is enforced upstream
 * by {@link TenantClaimValidator} during JWT decoding — by the time this filter
 * runs the security context already contains a token with an acceptable
 * {@code tenant_id} (either {@code scm} or the SUPER_ADMIN wildcard).
 *
 * <p>Runs after Spring Security has populated the security context. If no JWT is
 * present (public routes), the filter becomes a no-op.
 */
@Component
public class JwtHeaderEnrichmentFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(token -> enrich(exchange, token.getToken()))
                .defaultIfEmpty(exchange)
                .flatMap(chain::filter);
    }

    private ServerWebExchange enrich(ServerWebExchange exchange, Jwt jwt) {
        String subject = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String tenantId = jwt.getClaimAsString(TenantClaimValidator.CLAIM_TENANT_ID);
        String role = resolveRole(jwt);
        String scope = jwt.getClaimAsString("scope");

        ServerHttpRequest.Builder builder = exchange.getRequest().mutate();
        if (subject != null) {
            builder.header("X-User-Id", subject);
            builder.header("X-Account-Id", subject);
            builder.header("X-Actor-Id", subject);
        }
        if (email != null) {
            builder.header("X-User-Email", email);
        }
        if (tenantId != null && !tenantId.isBlank()) {
            builder.header("X-Tenant-Id", tenantId);
        }
        // Always set X-User-Role / X-Roles. When no role claim is present, emit ""
        // (empty string) — downstream services must treat this as "no authorized
        // role" and deny access; leaving the header absent would let a buggy
        // service fall through to a default.
        builder.header("X-User-Role", role);
        builder.header("X-Roles", role);
        String accountType = jwt.getClaimAsString("account_type");
        if (accountType != null) {
            builder.header("X-Account-Type", accountType);
        }
        if (scope != null && !scope.isBlank()) {
            builder.header("X-Scopes", scope);
        }
        // Edge Case E1: client_credentials tokens have no email claim and their
        // sub is the client_id itself. Downstream services that need to
        // distinguish machine vs human callers can branch on this header.
        builder.header("X-Token-Type", isClientCredentialsToken(jwt) ? "client_credentials" : "user");
        return exchange.mutate().request(builder.build()).build();
    }

    /**
     * Heuristic for client_credentials grant detection:
     * <ul>
     *   <li>No {@code email} claim (RFC 6749 / OIDC client_credentials does not
     *       carry user identity claims), AND</li>
     *   <li>Either an {@code azp} claim equal to {@code sub} (Spring Authorization
     *       Server populates {@code azp} = client_id; for client_credentials the
     *       {@code sub} is also the client_id), OR no {@code email} at all when
     *       only {@code scope} is present (typical machine token shape).</li>
     * </ul>
     * Deliberately conservative — false positives (treating a user token as
     * machine) are far worse than false negatives.
     */
    private boolean isClientCredentialsToken(Jwt jwt) {
        if (jwt.getClaimAsString("email") != null) {
            return false;
        }
        String azp = jwt.getClaimAsString("azp");
        String sub = jwt.getSubject();
        if (azp != null && azp.equals(sub)) {
            return true;
        }
        // Fallback: no email + scope claim present + no roles → likely machine.
        Collection<String> roles = jwt.getClaimAsStringList("roles");
        boolean hasRolesArray = roles != null && !roles.isEmpty();
        boolean hasRoleString = jwt.getClaimAsString("role") != null;
        boolean hasScope = jwt.getClaimAsString("scope") != null;
        return hasScope && !hasRolesArray && !hasRoleString;
    }

    /**
     * Resolves a role claim with defined precedence:
     * {@code roles} (array, joined on {@code ","}) → {@code role} (string) → {@code ""}.
     * Never returns {@code null}; callers can write the result directly to a header.
     */
    private String resolveRole(Jwt jwt) {
        Collection<String> multi = jwt.getClaimAsStringList("roles");
        if (multi != null && !multi.isEmpty()) {
            return String.join(",", multi);
        }
        Object single = jwt.getClaim("role");
        if (single instanceof String s && !s.isBlank()) {
            return s;
        }
        return "";
    }

    @Override
    public int getOrder() {
        // Runs after Spring Security's auth filter (around HIGHEST + 100)
        // but before the route-routing filter.
        return -1;
    }
}
