package com.example.gateway.filter;

import com.example.gateway.config.GatewayMetrics;
import com.example.gateway.security.RouteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("JwtAuthenticationFilter 단위 테스트")
class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-jwt-secret-key-for-unit-test-32chars!!";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private JwtAuthenticationFilter filter;
    private final RouteService routeService = new RouteService();
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        filter = new JwtAuthenticationFilter(SECRET, new ObjectMapper(), new GatewayMetrics(meterRegistry), routeService);
    }

    private String validToken(String userId, String email) {
        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(KEY)
                .compact();
    }

    private String tokenWithoutSubject(String email) {
        return Jwts.builder()
                .claim("email", email)
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(KEY)
                .compact();
    }

    private String tokenWithRole(String userId, String email, String role) {
        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim("role", role)
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(KEY)
                .compact();
    }

    private String expiredToken(String userId, String email) {
        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .expiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(KEY)
                .compact();
    }

    private String tokenWithoutEmail(String userId) {
        return Jwts.builder()
                .subject(userId)
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(KEY)
                .compact();
    }

    @Test
    @DisplayName("인증 헤더 없이 보호된 경로 요청 시 401을 반환한다")
    void filter_noAuthHeader_protectedRoute_returns401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders/123").build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("잘못된 JWT로 보호된 경로 요청 시 401을 반환한다")
    void filter_invalidToken_protectedRoute_returns401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders/123")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.here")
                        .build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("유효한 JWT로 보호된 경로 요청 시 필터를 통과하고 헤더가 주입된다")
    void filter_validToken_protectedRoute_passesAndInjectsHeaders() {
        String token = validToken("user-123", "user@example.com");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders/123")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(argThat(ex -> {
            String userId = ex.getRequest().getHeaders().getFirst("X-User-Id");
            String email = ex.getRequest().getHeaders().getFirst("X-User-Email");
            return "user-123".equals(userId) && "user@example.com".equals(email);
        }));
    }

    @Test
    @DisplayName("공개 경로 POST /api/auth/login은 토큰 없이 필터를 통과한다")
    void filter_publicRoute_login_passesWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login").build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("공개 경로 POST /api/auth/signup은 토큰 없이 필터를 통과한다")
    void filter_publicRoute_signup_passesWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/signup").build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    @Test
    @DisplayName("공개 경로 GET /api/products/**는 토큰 없이 필터를 통과한다")
    void filter_publicRoute_products_passesWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/products/42").build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    @Test
    @DisplayName("공개 경로 GET /api/search/**는 토큰 없이 필터를 통과한다")
    void filter_publicRoute_search_passesWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/search/products?q=shoes").build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    @Test
    @DisplayName("Bearer 접두사 없는 Authorization 헤더는 401을 반환한다")
    void filter_authHeaderWithoutBearerPrefix_returns401() {
        String token = validToken("user-123", "user@example.com");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders/123")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("JWT 시크릿이 32바이트 미만이면 IllegalArgumentException이 발생한다")
    void constructor_shortSecret_throwsException() {
        String shortSecret = "too-short-secret";

        assertThatThrownBy(() -> new JwtAuthenticationFilter(
                shortSecret, new ObjectMapper(), new GatewayMetrics(new SimpleMeterRegistry()), routeService))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    @DisplayName("JWT 시크릿이 null이면 IllegalArgumentException이 발생한다")
    void constructor_nullSecret_throwsException() {
        assertThatThrownBy(() -> new JwtAuthenticationFilter(
                null, new ObjectMapper(), new GatewayMetrics(new SimpleMeterRegistry()), routeService))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    @DisplayName("JWT에서 userId(subject)가 null이면 401을 반환한다")
    void filter_nullUserId_returns401() {
        String token = tokenWithoutSubject("user@example.com");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders/123")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("JWT에서 userId(subject)가 빈 문자열이면 401을 반환한다")
    void filter_emptyUserId_returns401() {
        String token = validToken("", "user@example.com");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders/123")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("JWT에서 userId(subject)가 공백 문자열이면 401을 반환한다")
    void filter_blankUserId_returns401() {
        String token = validToken("   ", "user@example.com");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders/123")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("JWT에서 email claim이 null이면 401을 반환한다")
    void filter_nullEmail_returns401() {
        String token = tokenWithoutEmail("user-123");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders/123")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("JWT에서 email claim이 빈 문자열이면 401을 반환한다")
    void filter_emptyEmail_returns401() {
        String token = validToken("user-123", "");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders/123")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("공개 경로 요청 시 gateway_requests_routed_total 메트릭이 올바른 target service로 기록된다")
    void filter_publicRoute_incrementsRequestsRoutedMetric() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/products/42").build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        double count = meterRegistry.counter("gateway_requests_routed_total", "target", "product-service").count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    @DisplayName("공개 경로 GET /api/search/** 요청 시 search-service 메트릭이 기록된다")
    void filter_publicRoute_search_incrementsRequestsRoutedMetric() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/search/products?q=shoes").build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        double count = meterRegistry.counter("gateway_requests_routed_total", "target", "search-service").count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    @DisplayName("공개 경로 POST /api/auth/login 요청 시 auth-service 메트릭이 기록된다")
    void filter_publicRoute_login_incrementsRequestsRoutedMetric() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login").build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        double count = meterRegistry.counter("gateway_requests_routed_total", "target", "auth-service").count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    @DisplayName("인증 경로 요청 시에도 메트릭이 기록된다")
    void filter_authenticatedRoute_incrementsRequestsRoutedMetric() {
        String token = validToken("user-123", "user@example.com");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders/123")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        double count = meterRegistry.counter("gateway_requests_routed_total", "target", "order-service").count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    @DisplayName("GET /api/products에 POST 메서드는 보호된 경로로 처리된다")
    void filter_productPathWithPost_treatedAsProtected() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/products").build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("인증 경로에서 외부 X-User-Id 헤더가 JWT subject로 덮어쓰기된다")
    void filter_spoofedUserId_authenticatedRoute_overwrittenByJwt() {
        String token = validToken("real-user-123", "real@example.com");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders/123")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-User-Id", "spoofed-user-999")
                        .build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(argThat(ex -> {
            var headers = ex.getRequest().getHeaders();
            return headers.get("X-User-Id").size() == 1
                    && "real-user-123".equals(headers.getFirst("X-User-Id"));
        }));
    }

    @Test
    @DisplayName("인증 경로에서 외부 X-User-Email 헤더가 JWT email로 덮어쓰기된다")
    void filter_spoofedEmail_authenticatedRoute_overwrittenByJwt() {
        String token = validToken("user-123", "real@example.com");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders/123")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-User-Email", "spoofed@evil.com")
                        .build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(argThat(ex -> {
            var headers = ex.getRequest().getHeaders();
            return headers.get("X-User-Email").size() == 1
                    && "real@example.com".equals(headers.getFirst("X-User-Email"));
        }));
    }

    @Test
    @DisplayName("공개 경로에서 외부 X-User-Id 헤더가 다운스트림에 전달되지 않는다")
    void filter_spoofedUserId_publicRoute_strippedBeforeDownstream() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/products/42")
                        .header("X-User-Id", "spoofed-user-999")
                        .build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(argThat(ex -> {
            var headers = ex.getRequest().getHeaders();
            return !headers.containsKey("X-User-Id");
        }));
    }

    @Test
    @DisplayName("공개 경로에서 외부 X-User-Email 헤더가 다운스트림에 전달되지 않는다")
    void filter_spoofedEmail_publicRoute_strippedBeforeDownstream() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/products/42")
                        .header("X-User-Email", "spoofed@evil.com")
                        .build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(argThat(ex -> {
            var headers = ex.getRequest().getHeaders();
            return !headers.containsKey("X-User-Email");
        }));
    }

    @Test
    @DisplayName("정상 인증 흐름에서 외부 스푸핑 헤더 없이 JWT 헤더가 정상 주입된다")
    void filter_noSpoofedHeaders_authenticatedRoute_injectsJwtHeaders() {
        String token = validToken("user-123", "user@example.com");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders/123")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(argThat(ex -> {
            var headers = ex.getRequest().getHeaders();
            return headers.get("X-User-Id").size() == 1
                    && "user-123".equals(headers.getFirst("X-User-Id"))
                    && headers.get("X-User-Email").size() == 1
                    && "user@example.com".equals(headers.getFirst("X-User-Email"));
        }));
    }

    @Test
    @DisplayName("만료된 JWT로 보호된 경로 요청 시 401을 반환하고 expired 메트릭이 기록된다")
    void filter_expiredToken_protectedRoute_returns401AndIncrementsExpiredMetric() {
        String token = expiredToken("user-123", "user@example.com");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders/123")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
        double count = meterRegistry.counter("gateway_jwt_validation_failure_total", "reason", "expired").count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    @DisplayName("JWT에 role claim이 있으면 X-User-Role 헤더가 주입된다")
    void filter_tokenWithRole_injectsRoleHeader() {
        String token = tokenWithRole("user-123", "user@example.com", "ADMIN");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders/123")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(argThat(ex -> {
            var headers = ex.getRequest().getHeaders();
            return "ADMIN".equals(headers.getFirst("X-User-Role"));
        }));
    }

    @Test
    @DisplayName("JWT에 role claim이 없으면 X-User-Role 헤더가 주입되지 않는다")
    void filter_tokenWithoutRole_doesNotInjectRoleHeader() {
        String token = validToken("user-123", "user@example.com");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders/123")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(argThat(ex -> {
            var headers = ex.getRequest().getHeaders();
            return !headers.containsKey("X-User-Role");
        }));
    }

    @Test
    @DisplayName("공개 경로에서 외부 X-User-Role 헤더가 다운스트림에 전달되지 않는다")
    void filter_spoofedRole_publicRoute_strippedBeforeDownstream() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/products/42")
                        .header("X-User-Role", "ADMIN")
                        .build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(argThat(ex -> {
            var headers = ex.getRequest().getHeaders();
            return !headers.containsKey("X-User-Role");
        }));
    }

    @Test
    @DisplayName("인증 경로에서 외부 X-User-Role 헤더가 JWT role로 덮어쓰기된다")
    void filter_spoofedRole_authenticatedRoute_overwrittenByJwt() {
        String token = tokenWithRole("user-123", "user@example.com", "USER");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders/123")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-User-Role", "ADMIN")
                        .build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(argThat(ex -> {
            var headers = ex.getRequest().getHeaders();
            return headers.get("X-User-Role").size() == 1
                    && "USER".equals(headers.getFirst("X-User-Role"));
        }));
    }

    @Test
    @DisplayName("필터 순서는 -100이다")
    void getOrder_returnsMinusOneHundred() {
        assertThat(filter.getOrder()).isEqualTo(-100);
    }

    @Test
    @DisplayName("공개 경로 POST /api/auth/refresh는 토큰 없이 필터를 통과한다")
    void filter_publicRoute_refresh_passesWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/refresh").build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    @Test
    @DisplayName("인증 헤더 없이 보호된 경로 요청 시 missing 메트릭이 기록된다")
    void filter_noAuthHeader_protectedRoute_incrementsMissingMetric() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders/123").build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        double count = meterRegistry.counter("gateway_jwt_validation_failure_total", "reason", "missing").count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    @DisplayName("잘못된 JWT로 보호된 경로 요청 시 invalid 메트릭이 기록된다")
    void filter_invalidToken_protectedRoute_incrementsInvalidMetric() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders/123")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.here")
                        .build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        double count = meterRegistry.counter("gateway_jwt_validation_failure_total", "reason", "invalid").count();
        assertThat(count).isEqualTo(1.0);
    }
}
