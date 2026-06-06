package com.example.gateway.filter;

import com.example.gateway.ratelimit.TokenBucketRateLimiter;
import com.example.gateway.ratelimit.TokenBucketRateLimiter.RateLimitResult;
import com.example.gateway.route.RouteConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitFilter 단위 테스트")
class RateLimitFilterUnitTest {

    @Mock
    private TokenBucketRateLimiter rateLimiter;

    @Mock
    private RouteConfig routeConfig;

    @Mock
    private GatewayFilterChain chain;

    private RateLimitFilter filter;
    private ObjectMapper objectMapper;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        meterRegistry = new SimpleMeterRegistry();
        filter = new RateLimitFilter(rateLimiter, routeConfig, objectMapper, meterRegistry);
    }

    // -----------------------------------------------------------------------
    // Existing behaviour tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("rate limit 이내 요청은 정상 통과")
    void filter_underLimit_passesThrough() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/login")
                .remoteAddress(new java.net.InetSocketAddress("192.168.1.100", 12345))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(routeConfig.resolveRateLimitScope("/api/auth/login")).willReturn("login");
        given(rateLimiter.isAllowed(eq("login"), anyString()))
                .willReturn(Mono.just(RateLimitResult.allowed()));
        given(rateLimiter.isAllowed(eq("global"), anyString()))
                .willReturn(Mono.just(RateLimitResult.allowed()));
        given(chain.filter(any())).willReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    @DisplayName("scope rate limit 초과 시 429 + Retry-After 반환")
    void filter_scopeOverLimit_returns429WithRetryAfter() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/login")
                .remoteAddress(new java.net.InetSocketAddress("192.168.1.100", 12345))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(routeConfig.resolveRateLimitScope("/api/auth/login")).willReturn("login");
        given(rateLimiter.isAllowed(eq("login"), anyString()))
                .willReturn(Mono.just(RateLimitResult.rejected(60)));

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("60");
    }

    @Test
    @DisplayName("global rate limit 초과 시 429 반환")
    void filter_globalOverLimit_returns429() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/accounts/me")
                .remoteAddress(new java.net.InetSocketAddress("192.168.1.100", 12345))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(routeConfig.resolveRateLimitScope("/api/accounts/me")).willReturn(null);
        given(rateLimiter.isAllowed(eq("global"), anyString()))
                .willReturn(Mono.just(RateLimitResult.rejected(1)));

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("refresh scope는 JWT sub 클레임의 account_id를 rate-limit 식별자로 사용 (tenant_id 포함)")
    void filter_refreshScope_extractsAccountIdFromJwtSub() {
        // Build a minimal JWT with sub = "account-42" and tenant_id = "fan-platform"
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"account-42\",\"tenant_id\":\"fan-platform\",\"exp\":9999999999}"
                        .getBytes(StandardCharsets.UTF_8));
        String fakeJwt = header + "." + payload + ".fake-signature";

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/refresh")
                .header("Authorization", "Bearer " + fakeJwt)
                .remoteAddress(new java.net.InetSocketAddress("10.0.0.1", 12345))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(routeConfig.resolveRateLimitScope("/api/auth/refresh")).willReturn("refresh");
        // identifier is now "tenant_id:account_id" = "fan-platform:account-42"
        given(rateLimiter.isAllowed(eq("refresh"), eq("fan-platform:account-42")))
                .willReturn(Mono.just(RateLimitResult.allowed()));
        given(rateLimiter.isAllowed(eq("global"), anyString()))
                .willReturn(Mono.just(RateLimitResult.allowed()));
        given(chain.filter(any())).willReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(rateLimiter).isAllowed("refresh", "fan-platform:account-42");
    }

    // -----------------------------------------------------------------------
    // TASK-BE-230: tenant_id in rate limit key tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("login scope — JWT에 tenant_id=fan-platform → 키 패턴 fan-platform:{subnet}")
    void filter_loginScope_tenantIdIncludedInKey() {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"account-1\",\"tenant_id\":\"fan-platform\",\"exp\":9999999999}"
                        .getBytes(StandardCharsets.UTF_8));
        String fakeJwt = header + "." + payload + ".fake-sig";

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/login")
                .header("Authorization", "Bearer " + fakeJwt)
                .remoteAddress(new java.net.InetSocketAddress("10.0.0.1", 12345))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(routeConfig.resolveRateLimitScope("/api/auth/login")).willReturn("login");
        // subnet of 10.0.0.1 = 10.0.0.0/24 → key = "fan-platform:10.0.0.0/24"
        given(rateLimiter.isAllowed(eq("login"), eq("fan-platform:10.0.0.0/24")))
                .willReturn(Mono.just(RateLimitResult.allowed()));
        given(rateLimiter.isAllowed(eq("global"), anyString()))
                .willReturn(Mono.just(RateLimitResult.allowed()));
        given(chain.filter(any())).willReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(rateLimiter).isAllowed("login", "fan-platform:10.0.0.0/24");
    }

    @Test
    @DisplayName("signup scope — JWT에 tenant_id=wms → 키 패턴 wms:{ip}")
    void filter_signupScope_tenantIdIncludedInKey() {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"account-2\",\"tenant_id\":\"wms\",\"exp\":9999999999}"
                        .getBytes(StandardCharsets.UTF_8));
        String fakeJwt = header + "." + payload + ".fake-sig";

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/accounts/signup")
                .header("Authorization", "Bearer " + fakeJwt)
                .remoteAddress(new java.net.InetSocketAddress("10.0.0.5", 12345))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(routeConfig.resolveRateLimitScope("/api/accounts/signup")).willReturn("signup");
        given(rateLimiter.isAllowed(eq("signup"), eq("wms:10.0.0.5")))
                .willReturn(Mono.just(RateLimitResult.allowed()));
        given(rateLimiter.isAllowed(eq("global"), anyString()))
                .willReturn(Mono.just(RateLimitResult.allowed()));
        given(chain.filter(any())).willReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(rateLimiter).isAllowed("signup", "wms:10.0.0.5");
    }

    @Test
    @DisplayName("JWT 없는 공개 경로 — tenant_id=anonymous로 처리")
    void filter_noJwt_loginScope_usesAnonymousTenant() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/login")
                .remoteAddress(new java.net.InetSocketAddress("10.0.0.1", 12345))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(routeConfig.resolveRateLimitScope("/api/auth/login")).willReturn("login");
        given(rateLimiter.isAllowed(eq("login"), eq("anonymous:10.0.0.0/24")))
                .willReturn(Mono.just(RateLimitResult.allowed()));
        given(rateLimiter.isAllowed(eq("global"), anyString()))
                .willReturn(Mono.just(RateLimitResult.allowed()));
        given(chain.filter(any())).willReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(rateLimiter).isAllowed("login", "anonymous:10.0.0.0/24");
    }

    // -----------------------------------------------------------------------
    // Micrometer counter tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("scope 허용 + global 허용 시 scope=login/result=allowed, scope=global/result=allowed 카운터 증가")
    void counter_scopeAllowed_globalAllowed_incrementsBoth() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/login")
                .remoteAddress(new java.net.InetSocketAddress("192.168.1.1", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(routeConfig.resolveRateLimitScope("/api/auth/login")).willReturn("login");
        given(rateLimiter.isAllowed(eq("login"), anyString()))
                .willReturn(Mono.just(RateLimitResult.allowed()));
        given(rateLimiter.isAllowed(eq("global"), anyString()))
                .willReturn(Mono.just(RateLimitResult.allowed()));
        given(chain.filter(any())).willReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(counterValue("login", "allowed")).isEqualTo(1.0);
        assertThat(counterValue("global", "allowed")).isEqualTo(1.0);
        assertThat(counterValue("login", "rejected")).isZero();
        assertThat(counterValue("global", "rejected")).isZero();
    }

    @Test
    @DisplayName("scope 거부 시 scope=login/result=rejected 카운터 증가, global 카운터 미증가")
    void counter_scopeRejected_incrementsScopeRejected_skipsGlobal() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/login")
                .remoteAddress(new java.net.InetSocketAddress("192.168.1.1", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(routeConfig.resolveRateLimitScope("/api/auth/login")).willReturn("login");
        given(rateLimiter.isAllowed(eq("login"), anyString()))
                .willReturn(Mono.just(RateLimitResult.rejected(60)));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(counterValue("login", "rejected")).isEqualTo(1.0);
        assertThat(counterValue("login", "allowed")).isZero();
        assertThat(counterValue("global", "allowed")).isZero();
        assertThat(counterValue("global", "rejected")).isZero();
    }

    @Test
    @DisplayName("scope 허용 후 global 거부 시 scope=login/result=allowed, scope=global/result=rejected 카운터 증가")
    void counter_scopeAllowed_globalRejected_incrementsCorrectly() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/login")
                .remoteAddress(new java.net.InetSocketAddress("192.168.1.1", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(routeConfig.resolveRateLimitScope("/api/auth/login")).willReturn("login");
        given(rateLimiter.isAllowed(eq("login"), anyString()))
                .willReturn(Mono.just(RateLimitResult.allowed()));
        given(rateLimiter.isAllowed(eq("global"), anyString()))
                .willReturn(Mono.just(RateLimitResult.rejected(30)));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(counterValue("login", "allowed")).isEqualTo(1.0);
        assertThat(counterValue("global", "rejected")).isEqualTo(1.0);
        assertThat(counterValue("login", "rejected")).isZero();
        assertThat(counterValue("global", "allowed")).isZero();
    }

    @Test
    @DisplayName("scope=null(non-rate-limited path): scope 카운터 없이 global 카운터만 증가")
    void counter_nullScope_onlyGlobalCounterFires() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/accounts/me")
                .remoteAddress(new java.net.InetSocketAddress("10.0.0.1", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(routeConfig.resolveRateLimitScope("/api/accounts/me")).willReturn(null);
        given(rateLimiter.isAllowed(eq("global"), anyString()))
                .willReturn(Mono.just(RateLimitResult.allowed()));
        given(chain.filter(any())).willReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(counterValue("global", "allowed")).isEqualTo(1.0);
        // No scope-specific counter registered at all
        assertThat(meterRegistry.find("gateway_ratelimit_total")
                .tag("scope", "login").counter()).isNull();
        assertThat(meterRegistry.find("gateway_ratelimit_total")
                .tag("scope", "signup").counter()).isNull();
    }

    @Test
    @DisplayName("scope=null + global 거부 시 global/result=rejected 카운터 증가")
    void counter_nullScope_globalRejected_incrementsGlobalRejected() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/accounts/me")
                .remoteAddress(new java.net.InetSocketAddress("10.0.0.1", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(routeConfig.resolveRateLimitScope("/api/accounts/me")).willReturn(null);
        given(rateLimiter.isAllowed(eq("global"), anyString()))
                .willReturn(Mono.just(RateLimitResult.rejected(1)));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(counterValue("global", "rejected")).isEqualTo(1.0);
        assertThat(counterValue("global", "allowed")).isZero();
    }

    @Test
    @DisplayName("Redis 오류 + fail-open 시 global/result=allowed 카운터 증가")
    void counter_redisFailOpen_incrementsGlobalAllowed() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/accounts/me")
                .remoteAddress(new java.net.InetSocketAddress("10.0.0.1", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(routeConfig.resolveRateLimitScope("/api/accounts/me")).willReturn(null);
        // Simulate Redis failure returning fail-open result
        given(rateLimiter.isAllowed(eq("global"), anyString()))
                .willReturn(Mono.just(RateLimitResult.allowed()));
        given(chain.filter(any())).willReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(counterValue("global", "allowed")).isEqualTo(1.0);
        assertThat(rejectedCounterValue("global")).isZero();
    }

    @Test
    @DisplayName("Redis 오류 + fail-closed 시 global/result=rejected 카운터 및 rejected_total 카운터 증가")
    void counter_redisFailClosed_incrementsGlobalRejected() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/accounts/me")
                .remoteAddress(new java.net.InetSocketAddress("10.0.0.1", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(routeConfig.resolveRateLimitScope("/api/accounts/me")).willReturn(null);
        // Simulate Redis failure returning fail-closed result
        given(rateLimiter.isAllowed(eq("global"), anyString()))
                .willReturn(Mono.just(RateLimitResult.rejected(1)));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(counterValue("global", "rejected")).isEqualTo(1.0);
        assertThat(rejectedCounterValue("global")).isEqualTo(1.0);
        assertThat(counterValue("global", "allowed")).isZero();
    }

    @Test
    @DisplayName("scope 거부 시 gateway_ratelimit_rejected_total{scope=login} 카운터 증가")
    void counter_scopeRejected_incrementsRejectedTotal() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/login")
                .remoteAddress(new java.net.InetSocketAddress("192.168.1.1", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(routeConfig.resolveRateLimitScope("/api/auth/login")).willReturn("login");
        given(rateLimiter.isAllowed(eq("login"), anyString()))
                .willReturn(Mono.just(RateLimitResult.rejected(60)));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(rejectedCounterValue("login")).isEqualTo(1.0);
        assertThat(rejectedCounterValue("global")).isZero();
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private double counterValue(String scope, String result) {
        Counter counter = meterRegistry.find("gateway_ratelimit_total")
                .tag("scope", scope)
                .tag("result", result)
                .counter();
        return counter == null ? 0.0 : counter.count();
    }

    private double rejectedCounterValue(String scope) {
        Counter counter = meterRegistry.find("gateway_ratelimit_rejected_total")
                .tag("scope", scope)
                .counter();
        return counter == null ? 0.0 : counter.count();
    }
}
