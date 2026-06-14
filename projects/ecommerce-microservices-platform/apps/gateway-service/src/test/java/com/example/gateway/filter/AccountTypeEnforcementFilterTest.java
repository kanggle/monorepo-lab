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

class AccountTypeEnforcementFilterTest {

    private final AccountTypeEnforcementFilter filter =
            new AccountTypeEnforcementFilter(new ObjectMapper());

    // -----------------------------------------------------------------------
    // Consumer routes (non-admin)
    // -----------------------------------------------------------------------

    @Test
    void consumerRoute_consumerToken_passesThrough() {
        MockServerWebExchange exchange = exchangeFor("/api/orders/123");
        CapturingChain chain = new CapturingChain();
        Jwt jwt = jwtWith("CONSUMER");

        run(exchange, chain, jwt);

        assertThat(chain.called).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void consumerRoute_operatorToken_returns403() {
        MockServerWebExchange exchange = exchangeFor("/api/orders/123");
        CapturingChain chain = new CapturingChain();
        Jwt jwt = jwtWith("OPERATOR");

        run(exchange, chain, jwt);

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void consumerRoute_missingAccountTypeClaim_returns403() {
        MockServerWebExchange exchange = exchangeFor("/api/orders/123");
        CapturingChain chain = new CapturingChain();
        Jwt jwt = jwtWithoutAccountType();

        run(exchange, chain, jwt);

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // -----------------------------------------------------------------------
    // Admin routes
    // -----------------------------------------------------------------------

    @Test
    void adminRoute_operatorToken_passesThrough() {
        MockServerWebExchange exchange = exchangeFor("/api/admin/products/42");
        CapturingChain chain = new CapturingChain();
        Jwt jwt = jwtWith("OPERATOR");

        run(exchange, chain, jwt);

        assertThat(chain.called).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminRoute_consumerToken_returns403() {
        MockServerWebExchange exchange = exchangeFor("/api/admin/products/42");
        CapturingChain chain = new CapturingChain();
        Jwt jwt = jwtWith("CONSUMER");

        run(exchange, chain, jwt);

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminRoute_missingAccountTypeClaim_returns403() {
        MockServerWebExchange exchange = exchangeFor("/api/admin/users");
        CapturingChain chain = new CapturingChain();
        Jwt jwt = jwtWithoutAccountType();

        run(exchange, chain, jwt);

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // -----------------------------------------------------------------------
    // Role-based admission (ADR-MONO-032 dual-read) — roles claim, no account_type
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
    void adminRoute_customerRoleOnly_returns403() {
        MockServerWebExchange exchange = exchangeFor("/api/admin/products/42");
        CapturingChain chain = new CapturingChain();
        Jwt jwt = jwtWithRoles("CUSTOMER");

        run(exchange, chain, jwt);

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
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

    private static Jwt jwtWith(String accountType) {
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

    private static Jwt jwtWithoutAccountType() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claims(c -> c.putAll(Map.of("iss", "test")))
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
