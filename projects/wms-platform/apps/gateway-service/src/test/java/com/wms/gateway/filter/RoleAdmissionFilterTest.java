package com.wms.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.apigateway.error.GatewayErrorHandler;
import com.example.apigateway.filter.RoleAdmissionFilter;
import com.wms.gateway.config.GatewayIdentityConfig;
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
 * wms's rule-6 admission after converging onto the shared {@link RoleAdmissionFilter}
 * (TASK-MONO-419, replacing the former {@code AccountTypeValidationFilter}). Asserted against
 * the filter this gateway actually wires — constructed from the production
 * {@link GatewayIdentityConfig} bean, so removing or weakening the bean turns this red.
 *
 * <p>Preserves the negative-case coverage the old filter test carried (empty roles → 403):
 * a gateway tested only with admitted tokens stays green even if admission is dropped
 * (ADR-MONO-049 § D5-8). Adds the scope-only case, which is behaviour-identical to the old
 * presence-only filter today (wms has no active machine traffic) but forward-compatible with
 * wms's registered {@code client_credentials} client going live.
 */
@DisplayName("wms 역할 admission — role/scope 있으면 통과, 둘 다 없으면 403")
class RoleAdmissionFilterTest {

    private final RoleAdmissionFilter filter = new GatewayIdentityConfig()
            .roleAdmissionFilter(new GatewayErrorHandler(new ObjectMapper()));

    @Test
    @DisplayName("operator 역할 토큰은 통과 (wms 는 operator-only 플랫폼)")
    void admitsRoleToken() {
        assertThat(runAuthenticated(jwt(Map.of("roles", List.of("WMS_OPERATOR")))).called).isTrue();
    }

    @Test
    @DisplayName("scope 만 있는 머신 토큰은 통과 (등록된 client_credentials 클라이언트 대비)")
    void admitsScopeOnlyMachineToken() {
        assertThat(runAuthenticated(jwt(Map.of("scope", "wms.inbound.read"))).called).isTrue();
    }

    @Test
    @DisplayName("역할도 scope 도 없으면 403 (기존 presence-only 필터의 음성 케이스 보존)")
    void rejectsNoRoleNoScopeWith403() {
        MockServerWebExchange exchange = get();
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                        new JwtAuthenticationToken(jwt(Map.of("account_type", "OPERATOR")))))
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
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/master/warehouses"));
    }

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

    private static final class CapturingChain implements GatewayFilterChain {
        boolean called = false;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.called = true;
            return Mono.empty();
        }
    }
}
