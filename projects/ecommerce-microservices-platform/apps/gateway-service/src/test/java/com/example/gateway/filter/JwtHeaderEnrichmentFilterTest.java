package com.example.gateway.filter;

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

    // -----------------------------------------------------------------------
    // Header injection
    // -----------------------------------------------------------------------

    @Test
    void enrichesSubjectEmailAndRoleClaimIntoHeaders() {
        Jwt jwt = jwtBuilder()
                .subject("user-42")
                .claim("email", "user@example.com")
                .claim("role", "BUYER")
                .claim("account_type", "CONSUMER")
                .build();

        HttpHeaders headers = runAndCaptureHeaders(jwt);

        assertThat(headers.getFirst("X-User-Id")).isEqualTo("user-42");
        assertThat(headers.getFirst("X-User-Email")).isEqualTo("user@example.com");
        assertThat(headers.getFirst("X-User-Role")).isEqualTo("BUYER");
        assertThat(headers.getFirst("X-Account-Type")).isEqualTo("CONSUMER");
    }

    @Test
    void joinsRolesArrayWithCommas() {
        Jwt jwt = jwtBuilder()
                .subject("user-7")
                .claim("roles", List.of("BUYER", "SELLER"))
                .build();

        HttpHeaders headers = runAndCaptureHeaders(jwt);

        assertThat(headers.getFirst("X-User-Role")).isEqualTo("BUYER,SELLER");
    }

    @Test
    void rolesArrayTakesPrecedenceOverRoleString() {
        Jwt jwt = jwtBuilder()
                .subject("user-101")
                .claim("role", "LEGACY_ROLE")
                .claim("roles", List.of("BUYER", "SELLER"))
                .build();

        HttpHeaders headers = runAndCaptureHeaders(jwt);

        assertThat(headers.getFirst("X-User-Role")).isEqualTo("BUYER,SELLER");
    }

    @Test
    void emitsEmptyRoleHeaderWhenNeitherRoleNorRolesClaim() {
        Jwt jwt = jwtBuilder()
                .subject("user-99")
                .claim("email", "noroles@example.com")
                .build();

        HttpHeaders headers = runAndCaptureHeaders(jwt);

        assertThat(headers.containsKey("X-User-Role")).isTrue();
        assertThat(headers.getFirst("X-User-Role")).isEmpty();
        assertThat(headers.getFirst("X-User-Id")).isEqualTo("user-99");
    }

    @Test
    void emitsEmptyRoleHeaderWhenRoleClaimIsBlank() {
        Jwt jwt = jwtBuilder()
                .subject("user-100")
                .claim("role", "   ")
                .build();

        HttpHeaders headers = runAndCaptureHeaders(jwt);

        assertThat(headers.containsKey("X-User-Role")).isTrue();
        assertThat(headers.getFirst("X-User-Role")).isEmpty();
    }

    @Test
    void omitsEmailHeaderWhenClaimAbsent() {
        Jwt jwt = jwtBuilder()
                .subject("user-50")
                .build();

        HttpHeaders headers = runAndCaptureHeaders(jwt);

        assertThat(headers.getFirst("X-User-Id")).isEqualTo("user-50");
        assertThat(headers.getFirst("X-User-Email")).isNull();
    }

    @Test
    void omitsAccountTypeHeaderWhenClaimAbsent() {
        Jwt jwt = jwtBuilder()
                .subject("user-42")
                .build();

        HttpHeaders headers = runAndCaptureHeaders(jwt);

        assertThat(headers.getFirst("X-Account-Type")).isNull();
    }

    @Test
    void injectsAccountTypeHeader() {
        Jwt jwt = jwtBuilder()
                .subject("user-42")
                .claim("account_type", "CONSUMER")
                .build();

        HttpHeaders headers = runAndCaptureHeaders(jwt);

        assertThat(headers.getFirst("X-Account-Type")).isEqualTo("CONSUMER");
    }

    // -----------------------------------------------------------------------
    // No-op when no security context
    // -----------------------------------------------------------------------

    @Test
    void passesThroughWhenNoSecurityContext() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/products/42").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        HttpHeaders forwarded = chain.captured.getRequest().getHeaders();
        assertThat(forwarded.getFirst("X-User-Id")).isNull();
        assertThat(forwarded.getFirst("X-User-Email")).isNull();
        assertThat(forwarded.getFirst("X-User-Role")).isNull();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private HttpHeaders runAndCaptureHeaders(Jwt jwt) {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/orders/123").build();
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
