package com.example.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.apigateway.security.TenantClaimValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;

/**
 * Pins the authentication entry point's status mapping (TASK-BE-501).
 *
 * <p>{@link TenantClaimValidator}'s javadoc has promised {@code tenant_mismatch → 403
 * TENANT_FORBIDDEN} since it was written, but {@link SecurityConfig} answered every
 * authentication failure with 401 — the promised code was never emitted anywhere in the
 * service. These tests hold the two apart: a cross-tenant token (signature-valid, wrong
 * edge) must not be reported as "authenticate yourself", because re-authenticating
 * produces the identical rejection.
 *
 * <p>Docker-free by construction: the entry point lambda is invoked directly rather than
 * through a booted gateway, so the assertion is about the mapping decision and nothing
 * else. {@code GatewayIntegrationTest} covers the same mapping end-to-end over a real
 * JWKS-signed token.
 */
@DisplayName("SecurityConfig — 인증 실패 상태코드 매핑")
class SecurityConfigTenantErrorMappingTest {

    private final SecurityConfig config = new SecurityConfig();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("tenant_mismatch 토큰은 403 TENANT_FORBIDDEN 으로 응답한다")
    void tenantMismatchMapsToForbidden() {
        MeterRegistry registry = new SimpleMeterRegistry();
        GatewayMetrics metrics = new GatewayMetrics(registry);
        ServerAuthenticationEntryPoint entryPoint = config.unauthorizedEntryPoint(objectMapper, metrics);

        MockServerWebExchange exchange = exchangeFor("/api/orders/123");
        entryPoint.commence(exchange, tenantMismatch("tenant_id claim is required")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        String body = bodyOf(exchange);
        assertThat(body).contains("TENANT_FORBIDDEN");
        assertThat(body).contains("tenant_id claim is required");
        assertThat(body).doesNotContain("UNAUTHORIZED");

        assertThat(counter(registry, GatewayMetrics.REASON_TENANT_MISMATCH)).isEqualTo(1.0);
        assertThat(counter(registry, "invalid")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Spring 이 원인 체인으로 감싼 tenant_mismatch 도 403 으로 인식한다")
    void tenantMismatchWrappedInCauseChainStillMapsToForbidden() {
        MeterRegistry registry = new SimpleMeterRegistry();
        GatewayMetrics metrics = new GatewayMetrics(registry);
        ServerAuthenticationEntryPoint entryPoint = config.unauthorizedEntryPoint(objectMapper, metrics);

        // The resource-server filter does not always hand the OAuth2AuthenticationException
        // over as the top frame; testing only the top frame would pass here and fail in prod.
        AuthenticationException wrapped =
                new WrappingAuthenticationException("auth failed", tenantMismatch("tenant_id 'wms' is not allowed"));

        MockServerWebExchange exchange = exchangeFor("/api/orders/123");
        entryPoint.commence(exchange, wrapped).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(bodyOf(exchange)).contains("TENANT_FORBIDDEN");
        assertThat(counter(registry, GatewayMetrics.REASON_TENANT_MISMATCH)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("그 외 인증 실패는 여전히 401 UNAUTHORIZED 다 (회귀 방지)")
    void genericAuthenticationFailureStillMapsToUnauthorized() {
        MeterRegistry registry = new SimpleMeterRegistry();
        GatewayMetrics metrics = new GatewayMetrics(registry);
        ServerAuthenticationEntryPoint entryPoint = config.unauthorizedEntryPoint(objectMapper, metrics);

        OAuth2AuthenticationException expired =
                new OAuth2AuthenticationException(new OAuth2Error("invalid_token", "Jwt expired", null));

        MockServerWebExchange exchange = exchangeFor("/api/orders/123");
        entryPoint.commence(exchange, expired).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(bodyOf(exchange)).contains("UNAUTHORIZED");
        assertThat(counter(registry, "invalid")).isEqualTo(1.0);
        assertThat(counter(registry, GatewayMetrics.REASON_TENANT_MISMATCH)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("OAuth2 오류가 아닌 인증 실패(토큰 부재)도 401 이다")
    void nonOAuth2AuthenticationFailureMapsToUnauthorized() {
        MeterRegistry registry = new SimpleMeterRegistry();
        GatewayMetrics metrics = new GatewayMetrics(registry);
        ServerAuthenticationEntryPoint entryPoint = config.unauthorizedEntryPoint(objectMapper, metrics);

        MockServerWebExchange exchange = exchangeFor("/api/orders/123");
        entryPoint.commence(exchange, new WrappingAuthenticationException("no token", null)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(bodyOf(exchange)).contains("UNAUTHORIZED");
    }

    // -----------------------------------------------------------------------

    private static OAuth2AuthenticationException tenantMismatch(String description) {
        return new OAuth2AuthenticationException(new OAuth2Error(
                TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH, description, null));
    }

    private static MockServerWebExchange exchangeFor(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
    }

    private static String bodyOf(MockServerWebExchange exchange) {
        return exchange.getResponse().getBodyAsString()
                .defaultIfEmpty("")
                .block();
    }

    private static double counter(MeterRegistry registry, String reason) {
        return registry.counter("gateway_jwt_validation_failure_total", "reason", reason).count();
    }

    /** Stands in for the generic wrapper Spring Security may place around the OAuth2 cause. */
    private static final class WrappingAuthenticationException extends AuthenticationException {
        WrappingAuthenticationException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }
}
