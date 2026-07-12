package com.example.fanplatform.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.apigateway.filter.JwtHeaderEnrichmentFilter;
import com.example.fanplatform.gateway.config.GatewayIdentityConfig;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class JwtHeaderEnrichmentFilterTest {

    // The bean this gateway actually registers — remove a mapping from GatewayIdentityConfig
    // and these assertions go red (TASK-MONO-355).
    private final JwtHeaderEnrichmentFilter filter =
            new GatewayIdentityConfig().jwtHeaderEnrichmentFilter();

    @Test
    void enrichesHeadersFromJwtClaims() {
        Jwt jwt = jwtBuilder()
                .subject("user-42")
                .claim("email", "user@example.com")
                .claim("role", "FAN")
                .claim("tenant_id", "fan-platform")
                .build();

        HttpHeaders headers = runAndCaptureHeaders(jwt);

        assertThat(headers.getFirst("X-User-Id")).isEqualTo("user-42");
        assertThat(headers.getFirst("X-Account-Id")).isEqualTo("user-42");
        assertThat(headers.getFirst("X-Actor-Id")).isEqualTo("user-42");
        assertThat(headers.getFirst("X-User-Email")).isEqualTo("user@example.com");
        assertThat(headers.getFirst("X-User-Role")).isEqualTo("FAN");
        assertThat(headers.getFirst("X-Roles")).isEqualTo("FAN");
        assertThat(headers.getFirst("X-Tenant-Id")).isEqualTo("fan-platform");
    }

    @Test
    void joinsRolesArrayWithCommas() {
        Jwt jwt = jwtBuilder()
                .subject("user-7")
                .claim("roles", List.of("FAN", "MEMBER_PREMIUM"))
                .claim("tenant_id", "fan-platform")
                .build();

        HttpHeaders headers = runAndCaptureHeaders(jwt);

        assertThat(headers.getFirst("X-User-Role")).isEqualTo("FAN,MEMBER_PREMIUM");
        assertThat(headers.getFirst("X-Roles")).isEqualTo("FAN,MEMBER_PREMIUM");
    }

    @Test
    void emitsEmptyRoleHeaderWhenNoRoleOrRolesClaim() {
        Jwt jwt = jwtBuilder()
                .subject("user-99")
                .claim("email", "noroles@example.com")
                .claim("tenant_id", "fan-platform")
                .build();

        HttpHeaders headers = runAndCaptureHeaders(jwt);

        // Header is always present — empty string signals "no authorized role"
        // to downstream services, which must deny access rather than default.
        assertThat(headers.containsKey("X-User-Role")).isTrue();
        assertThat(headers.getFirst("X-User-Role")).isEmpty();
        assertThat(headers.getFirst("X-Roles")).isEmpty();
    }

    @Test
    void emitsEmptyRoleHeaderWhenRoleClaimIsBlank() {
        Jwt jwt = jwtBuilder()
                .subject("user-100")
                .claim("role", "   ")
                .claim("tenant_id", "fan-platform")
                .build();

        HttpHeaders headers = runAndCaptureHeaders(jwt);

        assertThat(headers.getFirst("X-User-Role")).isEmpty();
    }

    @Test
    void rolesArrayTakesPrecedenceOverRoleString() {
        Jwt jwt = jwtBuilder()
                .subject("user-101")
                .claim("role", "LEGACY_ROLE")
                .claim("roles", List.of("FAN", "MEMBER_PREMIUM"))
                .claim("tenant_id", "fan-platform")
                .build();

        HttpHeaders headers = runAndCaptureHeaders(jwt);

        assertThat(headers.getFirst("X-User-Role")).isEqualTo("FAN,MEMBER_PREMIUM");
    }

    @Test
    void propagatesTenantHeaderForWildcardSuperAdmin() {
        // SUPER_ADMIN tokens carry tenant_id="*"; the validator already accepted
        // the token, so the enrichment filter just propagates the wildcard.
        Jwt jwt = jwtBuilder()
                .subject("super-admin-1")
                .claim("tenant_id", "*")
                .claim("role", "SUPER_ADMIN")
                .build();

        HttpHeaders headers = runAndCaptureHeaders(jwt);

        assertThat(headers.getFirst("X-Tenant-Id")).isEqualTo("*");
    }

    @Test
    void omitsTenantHeaderWhenClaimAbsent() {
        Jwt jwt = jwtBuilder()
                .subject("user-x")
                .build();

        HttpHeaders headers = runAndCaptureHeaders(jwt);

        assertThat(headers.getFirst("X-Tenant-Id")).isNull();
    }

    @Test
    void doesNotInjectAccountTypeEvenWhenClaimPresent() {
        // ADR-MONO-035 4b-2a: X-Account-Type must not be forwarded by the enrichment filter.
        Jwt jwt = jwtBuilder()
                .subject("user-42")
                .claim("email", "user@example.com")
                .claim("account_type", "CONSUMER")
                .claim("tenant_id", "fan-platform")
                .build();

        HttpHeaders headers = runAndCaptureHeaders(jwt);

        assertThat(headers.getFirst("X-Account-Type")).isNull();
        // Other headers must still be present.
        assertThat(headers.getFirst("X-User-Id")).isEqualTo("user-42");
        assertThat(headers.getFirst("X-Tenant-Id")).isEqualTo("fan-platform");
    }

    @Test
    void passesThroughWhenNoSecurityContext() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/community/posts").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        HttpHeaders forwarded = chain.captured.getRequest().getHeaders();
        assertThat(forwarded.getFirst("X-User-Id")).isNull();
        assertThat(forwarded.getFirst("X-Tenant-Id")).isNull();
    }

    private HttpHeaders runAndCaptureHeaders(Jwt jwt) {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/community/posts").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);
        filter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
                .block();

        return chain.captured.getRequest().getHeaders();
    }

    private static Jwt.Builder jwtBuilder() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claims(c -> c.putAll(Map.of("iss", "test")));
    }

    private static final class CapturingChain implements GatewayFilterChain {
        ServerWebExchange captured;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.captured = exchange;
            return Mono.empty();
        }
    }
}
