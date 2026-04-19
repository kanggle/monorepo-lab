package com.wms.gateway.filter;

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
    void enrichesHeadersFromJwtSubjectEmailAndRoleClaim() {
        Jwt jwt = jwtBuilder()
                .subject("user-42")
                .claim("email", "user@example.com")
                .claim("role", "MASTER_WRITE")
                .build();

        HttpHeaders headers = runAndCaptureHeaders(jwt);

        assertThat(headers.getFirst("X-User-Id")).isEqualTo("user-42");
        assertThat(headers.getFirst("X-Actor-Id")).isEqualTo("user-42");
        assertThat(headers.getFirst("X-User-Email")).isEqualTo("user@example.com");
        assertThat(headers.getFirst("X-User-Role")).isEqualTo("MASTER_WRITE");
    }

    @Test
    void joinsRolesArrayWithCommas() {
        Jwt jwt = jwtBuilder()
                .subject("user-7")
                .claim("roles", List.of("MASTER_READ", "MASTER_WRITE"))
                .build();

        HttpHeaders headers = runAndCaptureHeaders(jwt);

        assertThat(headers.getFirst("X-User-Role")).isEqualTo("MASTER_READ,MASTER_WRITE");
        assertThat(headers.getFirst("X-User-Email")).isNull();
    }

    @Test
    void passesThroughWhenNoSecurityContext() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/master/warehouses").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        HttpHeaders forwarded = chain.captured.getRequest().getHeaders();
        assertThat(forwarded.getFirst("X-User-Id")).isNull();
    }

    private HttpHeaders runAndCaptureHeaders(Jwt jwt) {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/master/warehouses").build();
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
