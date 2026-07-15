package com.example.finance.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.apigateway.error.GatewayErrorHandler;
import com.example.apigateway.filter.RoleAdmissionFilter;
import com.example.finance.gateway.config.GatewayIdentityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
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
 * finance's rule-6 admission (TASK-MONO-416), asserted against the {@link RoleAdmissionFilter}
 * this gateway actually wires — constructed from the production {@link GatewayIdentityConfig}
 * bean, not hand-rolled, so removing or weakening the bean turns this red.
 *
 * <p>The <em>negative</em> case is the reason this file exists: a gateway tested only with
 * admitted tokens stays green even if admission is dropped entirely (ADR-MONO-049 § D5-8).
 * finance's suite is Docker-free by design, so this exercises the production filter directly
 * rather than through a Testcontainers HTTP chain.
 */
@DisplayName("finance 역할 admission — role/scope 있으면 통과, 둘 다 없으면 403")
class RoleAdmissionFilterTest {

    private final RoleAdmissionFilter filter = new GatewayIdentityConfig()
            .roleAdmissionFilter(new GatewayErrorHandler(new ObjectMapper()));

    @Test
    @DisplayName("operator 역할 토큰은 통과")
    void admitsOperatorRoleToken() {
        assertThat(runAuthenticated(jwt(Map.of("roles", List.of("OPERATOR")))).called).isTrue();
    }

    @Test
    @DisplayName("scope 만 있는 머신 토큰은 통과")
    void admitsScopeOnlyMachineToken() {
        assertThat(runAuthenticated(jwt(Map.of("scope", "finance.read"))).called).isTrue();
    }

    @Test
    @DisplayName("역할도 scope 도 없으면 403")
    void rejectsNoRoleNoScopeWith403() {
        MockServerWebExchange exchange = get();
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                        new JwtAuthenticationToken(jwt(Map.of()))))
                .block();

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("security context 없음(공개 경로) → 통과")
    void passesPublicRoute() {
        CapturingChain chain = new CapturingChain();
        filter.filter(MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health")), chain).block();
        assertThat(chain.called).isTrue();
    }

    // --- helpers ---

    private CapturingChain runAuthenticated(Jwt jwt) {
        CapturingChain chain = new CapturingChain();
        filter.filter(get(), chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                        new JwtAuthenticationToken(jwt)))
                .block();
        return chain;
    }

    private static MockServerWebExchange get() {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/finance/accounts/1"));
    }

    private static Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder b = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuer("http://iam.local")
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        claims.forEach(b::claim);
        return b.build();
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
