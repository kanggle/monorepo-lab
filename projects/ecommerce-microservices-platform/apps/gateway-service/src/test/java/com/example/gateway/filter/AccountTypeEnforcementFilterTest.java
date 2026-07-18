package com.example.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.security.oauth2.TenantClaimValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Tests for {@link AccountTypeEnforcementFilter} — roles-only admission (ADR-MONO-035 4b-2a).
 * The legacy {@code account_type} OR-branch is gone: only the {@code roles} claim decides.
 */
class AccountTypeEnforcementFilterTest {

    private final AccountTypeEnforcementFilter filter =
            new AccountTypeEnforcementFilter(new ObjectMapper());

    // -----------------------------------------------------------------------
    // Consumer routes (non-admin) — CUSTOMER role required
    // -----------------------------------------------------------------------

    @Test
    void consumerRoute_customerRole_passesThrough() {
        MockServerWebExchange exchange = exchangeFor("/api/orders/123");
        CapturingChain chain = new CapturingChain();
        Jwt jwt = jwtWithRoles("CUSTOMER");

        run(exchange, chain, jwt);

        assertThat(chain.called).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void consumerRoute_adminRoleOnly_returns403() {
        // ECOMMERCE_OPERATOR without CUSTOMER cannot reach consumer routes.
        MockServerWebExchange exchange = exchangeFor("/api/orders/123");
        CapturingChain chain = new CapturingChain();
        Jwt jwt = jwtWithRoles("ECOMMERCE_OPERATOR");

        run(exchange, chain, jwt);

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void consumerRoute_noRole_returns403() {
        // A token with no roles (e.g. issuance outage) → 403 at gateway.
        MockServerWebExchange exchange = exchangeFor("/api/orders/123");
        CapturingChain chain = new CapturingChain();
        Jwt jwt = jwtWithoutRoles();

        run(exchange, chain, jwt);

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void consumerRoute_accountTypeOnlyNoRole_returns403() {
        // The legacy account_type=CONSUMER leg is gone — no role → 403.
        MockServerWebExchange exchange = exchangeFor("/api/orders/123");
        CapturingChain chain = new CapturingChain();
        Jwt jwt = jwtWithAccountTypeOnly("CONSUMER");

        run(exchange, chain, jwt);

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // -----------------------------------------------------------------------
    // Admin routes — ECOMMERCE_OPERATOR role required
    // -----------------------------------------------------------------------

    @Test
    void adminRoute_adminRole_passesThrough() {
        MockServerWebExchange exchange = exchangeFor("/api/admin/products/42");
        CapturingChain chain = new CapturingChain();
        Jwt jwt = jwtWithRoles("ECOMMERCE_OPERATOR");

        run(exchange, chain, jwt);

        assertThat(chain.called).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminRoute_customerRoleOnly_returns403() {
        MockServerWebExchange exchange = exchangeFor("/api/admin/products/42");
        CapturingChain chain = new CapturingChain();
        Jwt jwt = jwtWithRoles("CUSTOMER");

        run(exchange, chain, jwt);

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminRoute_noRole_returns403() {
        MockServerWebExchange exchange = exchangeFor("/api/admin/users");
        CapturingChain chain = new CapturingChain();
        Jwt jwt = jwtWithoutRoles();

        run(exchange, chain, jwt);

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminRoute_accountTypeOnlyNoRole_returns403() {
        // The legacy account_type=OPERATOR leg is gone — no role → 403.
        MockServerWebExchange exchange = exchangeFor("/api/admin/products/42");
        CapturingChain chain = new CapturingChain();
        Jwt jwt = jwtWithAccountTypeOnly("OPERATOR");

        run(exchange, chain, jwt);

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // -----------------------------------------------------------------------
    // SUPER_ADMIN wildcard READ admission on /api/admin/** (TASK-BE-506)
    //
    // The platform super-admin's console operator-overview forwards the base OIDC
    // domain token: tenant_id="*", NO roles claim (SUPER_ADMIN kept off the domain
    // token per ADR-033 S2 / ADR-034 U5). Admit it for SAFE methods (GET/HEAD) only;
    // writes stay operator-gated. Consistent with finance (FIN-BE-048/049) and
    // erp (ERP-BE-031); FIN-BE-050 sibling-parity audit provenance.
    // -----------------------------------------------------------------------

    @Test
    void adminRoute_superAdminWildcard_get_passesThrough() {
        MockServerWebExchange exchange = exchangeFor(HttpMethod.GET, "/api/admin/products");
        CapturingChain chain = new CapturingChain();

        run(exchange, chain, jwtWildcardTenantNoRoles());

        assertThat(chain.called).as("wildcard super-admin admitted on GET /api/admin/**").isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminRoute_superAdminWildcard_head_passesThrough() {
        MockServerWebExchange exchange = exchangeFor(HttpMethod.HEAD, "/api/admin/products");
        CapturingChain chain = new CapturingChain();

        run(exchange, chain, jwtWildcardTenantNoRoles());

        assertThat(chain.called).as("wildcard super-admin admitted on HEAD /api/admin/**").isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminRoute_superAdminWildcard_writeMethods_return403() {
        // Invariant: widen READ visibility only, never mutation. A wildcard super-admin
        // token must still be 403'd on every write to /api/admin/**.
        for (HttpMethod method : new HttpMethod[] {
                HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE}) {
            MockServerWebExchange exchange = exchangeFor(method, "/api/admin/products");
            CapturingChain chain = new CapturingChain();

            run(exchange, chain, jwtWildcardTenantNoRoles());

            assertThat(chain.called).as("wildcard super-admin 403'd on %s /api/admin/**", method).isFalse();
            assertThat(exchange.getResponse().getStatusCode())
                    .as("write %s stays operator-gated", method).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void adminRoute_nonWildcardNonOperator_get_returns403() {
        // The admission is keyed strictly on the wildcard tenant, NOT on authentication:
        // a well-formed non-wildcard token with no operator role is still 403'd on a read.
        MockServerWebExchange exchange = exchangeFor(HttpMethod.GET, "/api/admin/products");
        CapturingChain chain = new CapturingChain();

        run(exchange, chain, jwtWithTenantNoRoles("ecommerce"));

        assertThat(chain.called).as("non-wildcard non-operator blocked on GET /api/admin/**").isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminRoute_operatorRole_writeMethod_stillPassesThrough() {
        // No regression: an ECOMMERCE_OPERATOR keeps write access (the wildcard-read
        // admission is additive, it does not narrow the operator's authority).
        MockServerWebExchange exchange = exchangeFor(HttpMethod.POST, "/api/admin/products");
        CapturingChain chain = new CapturingChain();

        run(exchange, chain, jwtWithRoles("ECOMMERCE_OPERATOR"));

        assertThat(chain.called).as("ECOMMERCE_OPERATOR admitted on POST /api/admin/**").isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    // -----------------------------------------------------------------------
    // Role-based admission — roles claim, with or without account_type
    // -----------------------------------------------------------------------

    @Test
    void consumerRoute_customerRole_noAccountType_passesThrough() {
        MockServerWebExchange exchange = exchangeFor("/api/orders/123");
        CapturingChain chain = new CapturingChain();
        Jwt jwt = jwtWithRoles("CUSTOMER");

        run(exchange, chain, jwt);

        assertThat(chain.called).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminRoute_adminRole_noAccountType_passesThrough() {
        MockServerWebExchange exchange = exchangeFor("/api/admin/products/42");
        CapturingChain chain = new CapturingChain();
        Jwt jwt = jwtWithRoles("ECOMMERCE_OPERATOR");

        run(exchange, chain, jwt);

        assertThat(chain.called).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void dualCapability_customerAndAdminRoles_passBothSurfaces() {
        // The unified-identity defining case: one account holds CUSTOMER + ECOMMERCE_OPERATOR.
        Jwt jwt = jwtWithRoles("CUSTOMER", "ECOMMERCE_OPERATOR");

        MockServerWebExchange consumer = exchangeFor("/api/orders/123");
        CapturingChain consumerChain = new CapturingChain();
        run(consumer, consumerChain, jwt);
        assertThat(consumerChain.called).isTrue();

        MockServerWebExchange admin = exchangeFor("/api/admin/products/42");
        CapturingChain adminChain = new CapturingChain();
        run(admin, adminChain, jwt);
        assertThat(adminChain.called).isTrue();
    }

    // -----------------------------------------------------------------------
    // Operator-on-public admission (TASK-BE-380) — ECOMMERCE_OPERATOR admitted on the
    // promotion/shipping/notification read trees whose producer contracts host
    // Admin endpoints on the public path; the service self-gates via X-User-Role.
    // -----------------------------------------------------------------------

    @Test
    void operatorPublicPath_adminRole_passesThrough() {
        for (String path : new String[] {
                "/api/promotions", "/api/promotions/p1",
                "/api/shippings", "/api/shippings/s1/status",
                "/api/notifications/templates", "/api/notifications/templates/t1"}) {
            MockServerWebExchange exchange = exchangeFor(path);
            CapturingChain chain = new CapturingChain();
            run(exchange, chain, jwtWithRoles("ECOMMERCE_OPERATOR"));

            assertThat(chain.called).as("ECOMMERCE_OPERATOR admitted on %s", path).isTrue();
            assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void operatorPublicPath_customerRole_stillPassesThrough() {
        // Consumers keep reaching the consumer endpoints on these same trees.
        MockServerWebExchange exchange = exchangeFor("/api/promotions");
        CapturingChain chain = new CapturingChain();
        run(exchange, chain, jwtWithRoles("CUSTOMER"));

        assertThat(chain.called).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void nonOperatorPublicPath_adminRoleOnly_returns403() {
        // Scope guard: the ECOMMERCE_OPERATOR exception is limited to the contracted operator
        // read trees — other public trees stay CUSTOMER-only.
        for (String path : new String[] {"/api/products/1", "/api/orders/1", "/api/search"}) {
            MockServerWebExchange exchange = exchangeFor(path);
            CapturingChain chain = new CapturingChain();
            run(exchange, chain, jwtWithRoles("ECOMMERCE_OPERATOR"));

            assertThat(chain.called).as("ECOMMERCE_OPERATOR blocked on non-operator public %s", path).isFalse();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // -----------------------------------------------------------------------
    // Public routes (no security context)
    // -----------------------------------------------------------------------

    @Test
    void noSecurityContext_publicRoute_passesThrough() {
        MockServerWebExchange exchange = exchangeFor("/api/products/42");
        CapturingChain chain = new CapturingChain();

        // No contextWrite → no security context
        filter.filter(exchange, chain).block();

        assertThat(chain.called).isTrue();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void run(MockServerWebExchange exchange, CapturingChain chain, Jwt jwt) {
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);
        filter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
                .block();
    }

    private static MockServerWebExchange exchangeFor(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
    }

    private static MockServerWebExchange exchangeFor(HttpMethod method, String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.method(method, path).build());
    }

    /**
     * The platform super-admin's base OIDC domain token as forwarded by the console
     * operator-overview: {@code tenant_id="*"} and <b>no roles claim</b> (SUPER_ADMIN
     * is kept off the domain token per ADR-033 S2 / ADR-034 U5).
     */
    private static Jwt jwtWildcardTenantNoRoles() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("super-admin")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claims(c -> {
                    c.put("iss", "test");
                    c.put(TenantClaimValidator.CLAIM_TENANT_ID, TenantClaimValidator.WILDCARD_TENANT);
                })
                .build();
    }

    /** A well-formed non-wildcard token with no roles — proves admission is keyed on the wildcard. */
    private static Jwt jwtWithTenantNoRoles(String tenantId) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claims(c -> {
                    c.put("iss", "test");
                    c.put(TenantClaimValidator.CLAIM_TENANT_ID, tenantId);
                })
                .build();
    }

    /** JWT with no roles claim and no account_type. */
    private static Jwt jwtWithoutRoles() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claims(c -> c.putAll(Map.of("iss", "test")))
                .build();
    }

    /** JWT with account_type but no roles — tests that the dead leg is truly gone. */
    private static Jwt jwtWithAccountTypeOnly(String accountType) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claims(c -> {
                    c.put("iss", "test");
                    c.put("account_type", accountType);
                })
                .build();
    }

    private static Jwt jwtWithRoles(String... roles) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claims(c -> {
                    c.put("iss", "test");
                    c.put("roles", java.util.List.of(roles));
                })
                .build();
    }

    private static final class CapturingChain implements GatewayFilterChain {
        boolean called = false;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.called = true;
            return Mono.empty();
        }
    }
}
