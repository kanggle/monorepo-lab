package com.example.gateway.filter;

import com.example.security.oauth2.TenantClaimValidator;
import com.example.web.dto.ErrorResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Enforces role-based admission per route (ADR-MONO-035 4b-2a / ADR-032 D3):
 * <ul>
 *   <li>{@code /api/admin/**} → requires the {@code ECOMMERCE_OPERATOR} role, <b>or</b> a
 *       platform SUPER_ADMIN wildcard token ({@code tenant_id="*"}) on a <b>safe (read) method</b>
 *       (GET/HEAD) only (TASK-BE-506); any other token → 403</li>
 *   <li>{@code operator-on-public} read trees ({@code /api/promotions},
 *       {@code /api/shippings}, {@code /api/notifications}) → admit {@code CUSTOMER}
 *       <b>or</b> {@code ECOMMERCE_OPERATOR}; the per-endpoint operator/consumer split is enforced
 *       service-side via the gateway-injected {@code X-User-Role} header (TASK-BE-380).</li>
 *   <li>All other authenticated routes → requires the {@code CUSTOMER} role; any other token → 403</li>
 *   <li>Public routes (no security context) → passes through unchanged</li>
 * </ul>
 *
 * <p><b>Why the operator-on-public exception (TASK-BE-380):</b> promotion-api.md,
 * shipping-api.md and notification-api.md deliberately place their <i>operator</i>
 * (Admin) endpoints on the public path tree — e.g. {@code GET /api/promotions (Admin)},
 * {@code GET /api/shippings (Admin)}, {@code GET /api/notifications/templates (Admin)} —
 * each gated service-side by {@code X-User-Role == ECOMMERCE_OPERATOR}, NOT under {@code /api/admin/**}.
 * A prefix-only "{@code non-/api/admin → CUSTOMER}" rule therefore 403s the operator
 * before the request reaches the service that would accept it (live-surfaced by the
 * platform-console absorption PC-FE-086/088/089). Admitting {@code ECOMMERCE_OPERATOR} on exactly
 * these read trees reconciles the gateway with those producer contracts; the services
 * keep the fine-grained per-endpoint gating. iam-integration.md § "Account-Type 강제"
 * documents the exception.
 *
 * <p>The legacy {@code account_type} OR-branch has been removed (4b-2a): operators now
 * carry domain roles (post-4a), consumers carry {@code CUSTOMER}, so the {@code account_type}
 * claim is no longer consulted at the gateway for admission decisions.
 *
 * <p>Uses a Boolean intermediate value to avoid the {@code switchIfEmpty(chain.filter())}
 * anti-pattern: {@code Mono<Void>} completes empty on success, which makes it
 * indistinguishable from "no auth context present".
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountTypeEnforcementFilter implements GlobalFilter, Ordered {

    private static final int ORDER = -2;
    private static final String MSG_CONSUMER_ONLY = "This operation requires a consumer account";
    private static final String MSG_OPERATOR_ONLY = "This operation requires an operator account";

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .flatMap(auth -> {
                    org.springframework.security.oauth2.jwt.Jwt token = auth.getToken();
                    java.util.List<String> roles = token.getClaimAsStringList("roles");
                    boolean isAdmin = path.startsWith("/api/admin/");
                    if (isAdmin) {
                        boolean allowed = hasRole(roles, "ECOMMERCE_OPERATOR")
                                || isSuperAdminWildcardRead(exchange, token);
                        return Mono.just(allowed);
                    } else {
                        // Consumers (CUSTOMER) pass everywhere on the public tree; operators
                        // (ECOMMERCE_OPERATOR) are additionally admitted on the operator-on-public read
                        // trees (promotion/shipping/notification), where the producer
                        // contracts host Admin endpoints that self-gate via X-User-Role.
                        boolean allowed = hasRole(roles, "CUSTOMER")
                                || (isOperatorPublicPath(path) && hasRole(roles, "ECOMMERCE_OPERATOR"));
                        return Mono.just(allowed);
                    }
                })
                // No JWT context → public route, pass through
                .defaultIfEmpty(Boolean.TRUE)
                .flatMap(allowed -> {
                    if (allowed) {
                        return chain.filter(exchange);
                    }
                    boolean isAdmin = path.startsWith("/api/admin/");
                    String message = isAdmin ? MSG_OPERATOR_ONLY : MSG_CONSUMER_ONLY;
                    return writeForbidden(exchange, message);
                });
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private static boolean hasRole(java.util.List<String> roles, String role) {
        return roles != null && roles.contains(role);
    }

    /**
     * TASK-BE-506: admit a platform SUPER_ADMIN <b>wildcard</b> token on {@code /api/admin/**}
     * for <b>read (safe) methods only</b>.
     *
     * <p>The platform super-admin's console operator-overview forwards the operator's base OIDC
     * domain-facing token: {@code tenant_id="*"}, scope {@code openid profile email tenant.read},
     * and <b>no {@code roles} claim</b> — the admin-plane {@code SUPER_ADMIN} role is deliberately
     * kept off the domain token (ADR-033 S2 / ADR-034 U5). The layer-1 tenant gate already admits
     * the wildcard ({@code allowSuperAdminWildcard()}); this account-type plane was the straggler
     * that 403'd it for lack of {@code ECOMMERCE_OPERATOR}. Opening the read path here makes the
     * console ecommerce overview card consistent with the finance (FIN-BE-048/049) and erp
     * (ERP-BE-031) parity fixes (FIN-BE-050 sibling-parity audit provenance).
     *
     * <p><b>Invariant — widen READ visibility only, never mutation.</b> The admission is gated
     * strictly on (safe method {@code AND} {@code tenant_id="*"}); a wildcard super-admin token is
     * still 403'd on any write (POST/PUT/PATCH/DELETE) to {@code /api/admin/**}. This is an
     * account-type-plane bypass, not a resource-server authority grant, and — because the
     * downstream product-service trusts the gateway-injected headers — the gateway is the sole
     * enforcement point, so the short-circuit is exactly this and nothing wider. The shared
     * {@link TenantClaimValidator#WILDCARD_TENANT} constant is the same value the layer-1 gate
     * admits on, so the two cannot drift.
     */
    private static boolean isSuperAdminWildcardRead(ServerWebExchange exchange, Jwt token) {
        return isSafeMethod(exchange.getRequest().getMethod())
                && TenantClaimValidator.WILDCARD_TENANT.equals(
                        token.getClaimAsString(TenantClaimValidator.CLAIM_TENANT_ID));
    }

    /** Safe (read-only, side-effect-free) HTTP methods: GET and HEAD. */
    private static boolean isSafeMethod(HttpMethod method) {
        return HttpMethod.GET.equals(method) || HttpMethod.HEAD.equals(method);
    }

    /**
     * TASK-BE-380: the public-path read trees whose producer contracts host
     * <i>operator</i> (Admin) endpoints alongside consumer ones (promotion-api.md /
     * shipping-api.md / notification-api.md). On these, an {@code ECOMMERCE_OPERATOR} token is
     * admitted in addition to {@code CUSTOMER}; the service then enforces the
     * per-endpoint {@code X-User-Role == ECOMMERCE_OPERATOR} split. Scoped (not a blanket
     * ECOMMERCE_OPERATOR-everywhere) to keep the blast radius to exactly the contracted trees.
     */
    private static boolean isOperatorPublicPath(String path) {
        return path.startsWith("/api/promotions")
                || path.startsWith("/api/shippings")
                || path.startsWith("/api/notifications");
    }

    private Mono<Void> writeForbidden(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        ErrorResponse body = ErrorResponse.of("FORBIDDEN", message);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            return response.setComplete();
        }
    }
}
