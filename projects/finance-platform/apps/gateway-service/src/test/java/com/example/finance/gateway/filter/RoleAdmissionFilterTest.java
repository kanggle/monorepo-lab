package com.example.finance.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.apigateway.error.GatewayErrorHandler;
import com.example.apigateway.filter.RoleAdmissionFilter;
import com.example.apigateway.testfixtures.GatewayTestJwts;
import com.example.apigateway.testfixtures.RecordingGatewayFilterChain;
import com.example.finance.gateway.config.GatewayIdentityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

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
        assertThat(runAuthenticated(jwt(Map.of("roles", List.of("OPERATOR")))).wasCalled()).isTrue();
    }

    @Test
    @DisplayName("scope 만 있는 머신 토큰은 통과")
    void admitsScopeOnlyMachineToken() {
        assertThat(runAuthenticated(jwt(Map.of("scope", "finance.read"))).wasCalled()).isTrue();
    }

    @Test
    @DisplayName("역할도 scope 도 없으면 403")
    void rejectsNoRoleNoScopeWith403() {
        MockServerWebExchange exchange = get();
        RecordingGatewayFilterChain chain = new RecordingGatewayFilterChain();

        filter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                        new JwtAuthenticationToken(jwt(Map.of()))))
                .block();

        assertThat(chain.wasCalled()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("security context 없음(공개 경로) → 통과")
    void passesPublicRoute() {
        RecordingGatewayFilterChain chain = new RecordingGatewayFilterChain();
        filter.filter(MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health")), chain).block();
        assertThat(chain.wasCalled()).isTrue();
    }

    // --- helpers ---

    private RecordingGatewayFilterChain runAuthenticated(Jwt jwt) {
        RecordingGatewayFilterChain chain = new RecordingGatewayFilterChain();
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

    // Deliberately UNSIGNED (alg="none", +300s): admission runs after signature verification, so
    // it must exercise a token the resource server would have rejected on its own. The alg/ttl
    // deltas are passed explicitly to the shared builder rather than flattened to its RS256 default.
    private static Jwt jwt(Map<String, Object> claims) {
        return GatewayTestJwts.jwt("none", Duration.ofSeconds(300), claims);
    }
}
