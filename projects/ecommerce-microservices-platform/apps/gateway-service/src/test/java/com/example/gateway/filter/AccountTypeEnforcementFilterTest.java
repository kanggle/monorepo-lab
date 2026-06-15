package com.example.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
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
        // ADMIN without CUSTOMER cannot reach consumer routes.
        MockServerWebExchange exchange = exchangeFor("/api/orders/123");
        CapturingChain chain = new CapturingChain();
        Jwt jwt = jwtWithRoles("ADMIN");

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
    // Admin routes — ADMIN role required
    // -----------------------------------------------------------------------

    @Test
    void adminRoute_adminRole_passesThrough() {
        MockServerWebExchange exchange = exchangeFor("/api/admin/products/42");
        CapturingChain chain = new CapturingChain();
        Jwt jwt = jwtWithRoles("ADMIN");

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
        Jwt jwt = jwtWithRoles("ADMIN");

        run(exchange, chain, jwt);

        assertThat(chain.called).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void dualCapability_customerAndAdminRoles_passBothSurfaces() {
        // The unified-identity defining case: one account holds CUSTOMER + ADMIN.
        Jwt jwt = jwtWithRoles("CUSTOMER", "ADMIN");

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
    // Operator-on-public admission (TASK-BE-380) — ADMIN admitted on the
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
            run(exchange, chain, jwtWithRoles("ADMIN"));

            assertThat(chain.called).as("ADMIN admitted on %s", path).isTrue();
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
        // Scope guard: the ADMIN exception is limited to the contracted operator
        // read trees — other public trees stay CUSTOMER-only.
        for (String path : new String[] {"/api/products/1", "/api/orders/1", "/api/search"}) {
            MockServerWebExchange exchange = exchangeFor(path);
            CapturingChain chain = new CapturingChain();
            run(exchange, chain, jwtWithRoles("ADMIN"));

            assertThat(chain.called).as("ADMIN blocked on non-operator public %s", path).isFalse();
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
