package com.example.apigateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.apigateway.error.GatewayErrorHandler;
import com.example.apigateway.testfixtures.RecordingGatewayFilterChain;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
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
 * {@link RoleAdmissionFilter} with the {@link RoleAdmissions#roleOrScope()} predicate every
 * gateway wires. The negative cases are the point: a filter tested only with admitted tokens
 * stays green even if admission is removed entirely (ADR-MONO-049 § D5-8).
 */
@DisplayName("RoleAdmissionFilter — role-or-scope admission, 403 on neither")
class RoleAdmissionFilterTest {

    private final GatewayErrorHandler errorHandler = new GatewayErrorHandler(new ObjectMapper());
    private final RoleAdmissionFilter filter = new RoleAdmissionFilter(
            RoleAdmissions.roleOrScope(), "access requires a role", errorHandler);

    @Test
    @DisplayName("roles 배열이 있으면 통과")
    void admitsRoleArrayToken() {
        RecordingGatewayFilterChain chain = runAuthenticated(jwt(Map.of("roles", List.of("SCM_OPERATOR"))));
        assertThat(chain.wasCalled()).isTrue();
    }

    @Test
    @DisplayName("단수 role claim 만 있어도 통과 (JwtClaims.role fallback)")
    void admitsSingularRoleClaim() {
        RecordingGatewayFilterChain chain = runAuthenticated(jwt(Map.of("role", "BUYER")));
        assertThat(chain.wasCalled()).isTrue();
    }

    @Test
    @DisplayName("scope 만 있는 머신 토큰(client_credentials)은 통과 — role 없어도")
    void admitsScopeOnlyMachineToken() {
        RecordingGatewayFilterChain chain = runAuthenticated(jwt(Map.of("scope", "scm.read scm.write")));
        assertThat(chain.wasCalled()).isTrue();
    }

    @Test
    @DisplayName("role 도 scope 도 없으면 403 (인증됐지만 인가 실패)")
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
    @DisplayName("빈 roles + scope 없음 → 403")
    void rejectsEmptyRolesNoScopeWith403() {
        MockServerWebExchange exchange = get();
        RecordingGatewayFilterChain chain = new RecordingGatewayFilterChain();

        filter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                        new JwtAuthenticationToken(jwt(Map.of("roles", List.of())))))
                .block();

        assertThat(chain.wasCalled()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("security context 가 없으면(공개 경로) 통과")
    void passesWhenNoSecurityContext() {
        MockServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.get("/actuator/health"));
        RecordingGatewayFilterChain chain = new RecordingGatewayFilterChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.wasCalled()).isTrue();
    }

    @Test
    @DisplayName("순서 불변식: admission(-2) 은 header enrichment(-1) 보다 먼저 실행된다")
    void runsBeforeHeaderEnrichment() {
        assertThat(filter.getOrder())
                .isEqualTo(RoleAdmissionFilter.ADMISSION_ORDER)
                .isEqualTo(-2)
                .as("a rejected request must not first have identity headers injected")
                .isLessThan(new JwtHeaderEnrichmentFilter(List.of()).getOrder());
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
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/resource"));
    }

    // Deliberately UNSIGNED (alg="none"): admission runs after signature verification, so it must
    // exercise a token the resource server would have rejected on its own (ADR-MONO-049 § D5-8).
    private static Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder b = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuer("test")
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        claims.forEach(b::claim);
        return b.build();
    }
}
