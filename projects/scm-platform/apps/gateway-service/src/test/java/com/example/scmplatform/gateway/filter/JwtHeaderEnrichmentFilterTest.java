package com.example.scmplatform.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

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

    private final JwtHeaderEnrichmentFilter filter = new JwtHeaderEnrichmentFilter();

    @Test
    void enrichesHeadersFromJwtClaims() {
        Jwt jwt = jwtBuilder()
                .subject("user-42")
                .claim("email", "user@example.com")
                .claim("role", "BUYER")
                .claim("tenant_id", "scm")
                .build();

        HttpHeaders headers = runAndCaptureHeaders(jwt);

        assertThat(headers.getFirst("X-User-Id")).isEqualTo("user-42");
        assertThat(headers.getFirst("X-Account-Id")).isEqualTo("user-42");
        assertThat(headers.getFirst("X-Actor-Id")).isEqualTo("user-42");
        assertThat(headers.getFirst("X-User-Email")).isEqualTo("user@example.com");
        assertThat(headers.getFirst("X-User-Role")).isEqualTo("BUYER");
        assertThat(headers.getFirst("X-Roles")).isEqualTo("BUYER");
        assertThat(headers.getFirst("X-Tenant-Id")).isEqualTo("scm");
        // Has email claim → not a client_credentials token.
        assertThat(headers.getFirst("X-Token-Type")).isEqualTo("user");
    }

    @Test
    void joinsRolesArrayWithCommas() {
        Jwt jwt = jwtBuilder()
                .subject("user-7")
                .claim("email", "u7@example.com")
                .claim("roles", List.of("BUYER", "PROCUREMENT_OPERATOR"))
                .claim("tenant_id", "scm")
                .build();

        HttpHeaders headers = runAndCaptureHeaders(jwt);

        assertThat(headers.getFirst("X-User-Role")).isEqualTo("BUYER,PROCUREMENT_OPERATOR");
        assertThat(headers.getFirst("X-Roles")).isEqualTo("BUYER,PROCUREMENT_OPERATOR");
    }

    @Test
    void emitsEmptyRoleHeaderWhenNoRoleOrRolesClaim() {
        // Edge Case E3: client_credentials tokens carry only a `scope` claim,
        // no role/roles. Header is always emitted (empty string) so downstream
        // services authorise via scope rather than role.
        Jwt jwt = jwtBuilder()
                .subject("scm-platform-internal-services-client")
                .claim("scope", "scm.read scm.write")
                .claim("azp", "scm-platform-internal-services-client")
                .claim("tenant_id", "scm")
                .build();

        HttpHeaders headers = runAndCaptureHeaders(jwt);

        // Header is always present — empty string signals "no authorized role"
        // to downstream services, which must authorise via X-Scopes instead.
        assertThat(headers.containsKey("X-User-Role")).isTrue();
        assertThat(headers.getFirst("X-User-Role")).isEmpty();
        assertThat(headers.getFirst("X-Roles")).isEmpty();
        assertThat(headers.getFirst("X-Scopes")).isEqualTo("scm.read scm.write");
    }

    @Test
    void emitsEmptyRoleHeaderWhenRoleClaimIsBlank() {
        Jwt jwt = jwtBuilder()
                .subject("user-100")
                .claim("email", "u100@example.com")
                .claim("role", "   ")
                .claim("tenant_id", "scm")
                .build();

        HttpHeaders headers = runAndCaptureHeaders(jwt);

        assertThat(headers.getFirst("X-User-Role")).isEmpty();
    }

    @Test
    void rolesArrayTakesPrecedenceOverRoleString() {
        Jwt jwt = jwtBuilder()
                .subject("user-101")
                .claim("email", "u101@example.com")
                .claim("role", "LEGACY_ROLE")
                .claim("roles", List.of("BUYER", "PROCUREMENT_OPERATOR"))
                .claim("tenant_id", "scm")
                .build();

        HttpHeaders headers = runAndCaptureHeaders(jwt);

        assertThat(headers.getFirst("X-User-Role")).isEqualTo("BUYER,PROCUREMENT_OPERATOR");
    }

    @Test
    void propagatesTenantHeaderForWildcardSuperAdmin() {
        // SUPER_ADMIN tokens carry tenant_id="*"; the validator already accepted
        // the token, so the enrichment filter just propagates the wildcard.
        Jwt jwt = jwtBuilder()
                .subject("super-admin-1")
                .claim("email", "super@example.com")
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
                .claim("email", "x@example.com")
                .build();

        HttpHeaders headers = runAndCaptureHeaders(jwt);

        assertThat(headers.getFirst("X-Tenant-Id")).isNull();
    }

    @Test
    void clientCredentialsTokenFlaggedAsClientCredentialsTokenType() {
        // Edge Case E1: client_credentials tokens have sub = client_id,
        // no email, often azp = sub. The X-Token-Type header lets downstream
        // services distinguish machine callers from human users.
        Jwt jwt = jwtBuilder()
                .subject("scm-platform-internal-services-client")
                .claim("azp", "scm-platform-internal-services-client")
                .claim("scope", "scm.read scm.write")
                .claim("tenant_id", "scm")
                .build();

        HttpHeaders headers = runAndCaptureHeaders(jwt);

        assertThat(headers.getFirst("X-Token-Type")).isEqualTo("client_credentials");
        assertThat(headers.getFirst("X-Account-Id"))
                .as("X-Account-Id propagates client_id for client_credentials tokens")
                .isEqualTo("scm-platform-internal-services-client");
        assertThat(headers.getFirst("X-Scopes")).isEqualTo("scm.read scm.write");
    }

    @Test
    void doesNotInjectAccountTypeEvenWhenClaimPresent() {
        // ADR-MONO-035 4b-2a: X-Account-Type must not be forwarded by the enrichment filter.
        Jwt jwt = jwtBuilder()
                .subject("user-42")
                .claim("email", "user@example.com")
                .claim("account_type", "OPERATOR")
                .claim("tenant_id", "scm")
                .build();

        HttpHeaders headers = runAndCaptureHeaders(jwt);

        assertThat(headers.getFirst("X-Account-Type")).isNull();
        // Other headers must still be present.
        assertThat(headers.getFirst("X-User-Id")).isEqualTo("user-42");
        assertThat(headers.getFirst("X-Tenant-Id")).isEqualTo("scm");
    }

    @Test
    void passesThroughWhenNoSecurityContext() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/procurement/po").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        HttpHeaders forwarded = chain.captured.getRequest().getHeaders();
        assertThat(forwarded.getFirst("X-User-Id")).isNull();
        assertThat(forwarded.getFirst("X-Tenant-Id")).isNull();
        assertThat(forwarded.getFirst("X-Token-Type")).isNull();
    }

    private HttpHeaders runAndCaptureHeaders(Jwt jwt) {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/procurement/po").build();
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
