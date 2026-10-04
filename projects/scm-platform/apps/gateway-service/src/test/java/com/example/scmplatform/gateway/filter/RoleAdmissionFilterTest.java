package com.example.scmplatform.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.apigateway.error.GatewayErrorHandler;
import com.example.apigateway.filter.RoleAdmissionFilter;
import com.example.apigateway.testfixtures.GatewayTestJwts;
import com.example.apigateway.testfixtures.RecordingGatewayFilterChain;
import com.example.scmplatform.gateway.config.GatewayIdentityConfig;
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
 * scm's rule-6 admission (TASK-MONO-416), asserted against the {@link RoleAdmissionFilter} this
 * gateway actually wires — constructed from the production {@link GatewayIdentityConfig} bean,
 * not hand-rolled, so removing or weakening the bean turns this red. Same shape as erp's and
 * finance's {@code RoleAdmissionFilterTest}.
 *
 * <p><strong>Why scm needed this file only now (TASK-MONO-697 AC-4).</strong> The scope leg of
 * "role OR scope" used to be witnessed by one integration cell,
 * {@code GatewayBootstrapIntegrationTest} — a {@code client_credentials} token, scope and no role,
 * routed with 200. That token carries {@code aud = scm-platform-internal-services-client}, which is
 * not on this edge's audience allowlist; once the gateway ships ENFORCE the decoder refuses it
 * with 403 {@code AUDIENCE_FORBIDDEN} before admission ever runs, so the cell could no longer say
 * anything about admission. The proof moved here, below the decoder, where the audience gate does
 * not apply. Giving the integration token an allowlisted {@code aud} instead would have kept the
 * old cell green on a token production never issues.
 */
@DisplayName("scm 역할 admission — role/scope 있으면 통과, 둘 다 없으면 403")
class RoleAdmissionFilterTest {

    private final RoleAdmissionFilter filter = new GatewayIdentityConfig()
            .roleAdmissionFilter(new GatewayErrorHandler(new ObjectMapper()));

    @Test
    @DisplayName("BUYER 역할 토큰은 통과")
    void admitsBuyerRoleToken() {
        assertThat(runAuthenticated(jwt(Map.of("roles", List.of("BUYER")))).wasCalled()).isTrue();
    }

    @Test
    @DisplayName("scope 만 있는 머신 토큰(client_credentials 모양)은 통과 — role 없어도 (TASK-MONO-697 AC-4 이전처)")
    void admitsScopeOnlyMachineToken() {
        assertThat(runAuthenticated(jwt(Map.of("scope", "scm.read scm.write"))).wasCalled()).isTrue();
    }

    @Test
    @DisplayName("역할도 scope 도 없으면 403")
    void rejectsNoRoleNoScopeWith403() {
        MockServerWebExchange exchange = get();
        RecordingGatewayFilterChain chain = new RecordingGatewayFilterChain();

        filter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                        new JwtAuthenticationToken(jwt(Map.of("email", "roleless@test.local")))))
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
                MockServerHttpRequest.get("/api/v1/procurement/po"));
    }

    // Deliberately UNSIGNED (alg="none", +300s): admission runs after signature verification, so
    // it must exercise a token the resource server would have rejected on its own.
    private static Jwt jwt(Map<String, Object> claims) {
        return GatewayTestJwts.jwt("none", Duration.ofSeconds(300), claims);
    }
}
