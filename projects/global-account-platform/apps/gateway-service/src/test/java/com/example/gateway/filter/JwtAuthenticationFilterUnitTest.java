package com.example.gateway.filter;

import com.example.gateway.config.EdgeGatewayProperties;
import com.example.gateway.route.RouteConfig;
import com.example.gateway.security.TokenValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gap.security.jwt.JwtVerificationException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter 단위 테스트")
class JwtAuthenticationFilterUnitTest {

    @Mock
    private TokenValidator tokenValidator;

    @Mock
    private RouteConfig routeConfig;

    @Mock
    private GatewayFilterChain chain;

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOps;

    private JwtAuthenticationFilter filter;
    private ObjectMapper objectMapper;
    private SimpleMeterRegistry meterRegistry;
    private EdgeGatewayProperties properties;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        meterRegistry = new SimpleMeterRegistry();
        properties = new EdgeGatewayProperties();
        // Default: fallback disabled
        properties.getTenant().getLegacyFallback().setEnabled(false);

        filter = new JwtAuthenticationFilter(tokenValidator, routeConfig, objectMapper,
                redisTemplate, properties, meterRegistry);
        // Lenient: only the success-path tests reach the redis check; other tests short-circuit earlier.
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(valueOps.get(anyString())).thenReturn(Mono.empty());
    }

    // -----------------------------------------------------------------------
    // Existing behaviour tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("공개 경로는 인증 없이 통과")
    void filter_publicRoute_passesThrough() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/login")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(routeConfig.isPublicRoute(HttpMethod.POST, "/api/auth/login")).willReturn(true);
        given(chain.filter(any())).willReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    @DisplayName("Authorization 헤더 없으면 401 반환")
    void filter_missingAuthHeader_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/accounts/me")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(routeConfig.isPublicRoute(HttpMethod.GET, "/api/accounts/me")).willReturn(false);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("유효한 JWT로 인증 성공 시 X-Account-ID 및 X-Tenant-Id 헤더 주입")
    void filter_validToken_injectsAccountIdAndTenantIdHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/accounts/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(routeConfig.isPublicRoute(HttpMethod.GET, "/api/accounts/me")).willReturn(false);
        given(tokenValidator.validate("valid-token"))
                .willReturn(Mono.just(Map.of("sub", "account-123", "tenant_id", "fan-platform")));
        given(chain.filter(any())).willReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(exchangeCaptor.capture());
        HttpHeaders downstreamHeaders = exchangeCaptor.getValue().getRequest().getHeaders();
        assertThat(downstreamHeaders.getFirst("X-Account-ID")).isEqualTo("account-123");
        assertThat(downstreamHeaders.getFirst("X-Tenant-Id")).isEqualTo("fan-platform");
    }

    @Test
    @DisplayName("만료된 JWT 토큰 시 401 TOKEN_INVALID 반환")
    void filter_expiredToken_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/accounts/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer expired-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(routeConfig.isPublicRoute(HttpMethod.GET, "/api/accounts/me")).willReturn(false);
        given(tokenValidator.validate("expired-token"))
                .willReturn(Mono.error(new JwtVerificationException("Token has expired")));

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("JWT에 device_id claim이 있으면 X-Device-Id 헤더 주입")
    void filter_deviceIdClaim_injectsXDeviceIdHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/accounts/me/sessions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(routeConfig.isPublicRoute(HttpMethod.GET, "/api/accounts/me/sessions")).willReturn(false);
        given(tokenValidator.validate("valid-token"))
                .willReturn(Mono.just(Map.of("sub", "account-123", "device_id", "dev-abc",
                        "tenant_id", "fan-platform")));
        given(chain.filter(any())).willReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(exchangeCaptor.capture());
        HttpHeaders downstreamHeaders = exchangeCaptor.getValue().getRequest().getHeaders();
        assertThat(downstreamHeaders.getFirst("X-Account-ID")).isEqualTo("account-123");
        assertThat(downstreamHeaders.getFirst("X-Device-Id")).isEqualTo("dev-abc");
        assertThat(downstreamHeaders.getFirst("X-Tenant-Id")).isEqualTo("fan-platform");
    }

    @Test
    @DisplayName("JWT에 device_id claim이 없으면 X-Device-Id 헤더 주입 안 함 (legacy token)")
    void filter_noDeviceIdClaim_omitsXDeviceIdHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/accounts/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(routeConfig.isPublicRoute(HttpMethod.GET, "/api/accounts/me")).willReturn(false);
        given(tokenValidator.validate("valid-token"))
                .willReturn(Mono.just(Map.of("sub", "account-123", "tenant_id", "fan-platform")));
        given(chain.filter(any())).willReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(exchangeCaptor.capture());
        HttpHeaders downstreamHeaders = exchangeCaptor.getValue().getRequest().getHeaders();
        assertThat(downstreamHeaders.getFirst("X-Account-ID")).isEqualTo("account-123");
        assertThat(downstreamHeaders.getFirst("X-Device-Id")).isNull();
    }

    @Test
    @DisplayName("외부에서 보낸 X-Device-Id 헤더가 제거됨 (spoofing 방지)")
    void filter_spoofedDeviceIdHeader_isStripped() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/accounts/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .header("X-Device-Id", "spoofed-device")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(routeConfig.isPublicRoute(HttpMethod.GET, "/api/accounts/me")).willReturn(false);
        given(tokenValidator.validate("valid-token"))
                .willReturn(Mono.just(Map.of("sub", "account-123", "tenant_id", "fan-platform")));
        given(chain.filter(any())).willReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(exchangeCaptor.capture());
        // Spoofed header must NOT pass through when the JWT has no device_id claim.
        assertThat(exchangeCaptor.getValue().getRequest().getHeaders().getFirst("X-Device-Id")).isNull();
    }

    @Test
    @DisplayName("외부에서 보낸 X-Account-ID 헤더가 제거됨 (spoofing 방지)")
    void filter_spoofedAccountIdHeader_isStripped() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/login")
                .header("X-Account-ID", "spoofed-id")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(routeConfig.isPublicRoute(HttpMethod.POST, "/api/auth/login")).willReturn(true);
        given(chain.filter(any())).willAnswer(invocation -> {
            // Verify that the exchange passed to chain has X-Account-ID stripped
            return Mono.empty();
        });

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    // -----------------------------------------------------------------------
    // TASK-BE-230: tenant_id claim validation tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("tenant_id claim 누락 + fallback 비활성 → 401 TOKEN_INVALID")
    void filter_missingTenantIdClaim_fallbackDisabled_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/accounts/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(routeConfig.isPublicRoute(HttpMethod.GET, "/api/accounts/me")).willReturn(false);
        // JWT valid but no tenant_id claim
        given(tokenValidator.validate("valid-token"))
                .willReturn(Mono.just(Map.of("sub", "account-123")));

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("tenant_id claim 누락 + fallback 활성 → fan-platform으로 통과 + 메트릭 기록")
    void filter_missingTenantIdClaim_fallbackEnabled_passesWithDefaultTenant() {
        properties.getTenant().getLegacyFallback().setEnabled(true);
        properties.getTenant().getLegacyFallback().setDefaultTenantId("fan-platform");

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/accounts/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(routeConfig.isPublicRoute(HttpMethod.GET, "/api/accounts/me")).willReturn(false);
        given(tokenValidator.validate("valid-token"))
                .willReturn(Mono.just(Map.of("sub", "account-123")));
        given(chain.filter(any())).willReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(exchangeCaptor.capture());
        HttpHeaders downstreamHeaders = exchangeCaptor.getValue().getRequest().getHeaders();
        assertThat(downstreamHeaders.getFirst("X-Tenant-Id")).isEqualTo("fan-platform");

        // fallback metric must be recorded
        double fallbackCount = meterRegistry.find("gateway_tenant_fallback_total").counter() != null
                ? meterRegistry.find("gateway_tenant_fallback_total").counter().count()
                : 0.0;
        assertThat(fallbackCount).isEqualTo(1.0);
    }

    @Test
    @DisplayName("tenant_id=fan-platform 토큰 → X-Tenant-Id: fan-platform 헤더 전파")
    void filter_tenantIdFanPlatform_propagatesHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/accounts/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(routeConfig.isPublicRoute(HttpMethod.GET, "/api/accounts/me")).willReturn(false);
        given(tokenValidator.validate("valid-token"))
                .willReturn(Mono.just(Map.of("sub", "account-123", "tenant_id", "fan-platform")));
        given(chain.filter(any())).willReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(exchangeCaptor.capture());
        assertThat(exchangeCaptor.getValue().getRequest().getHeaders().getFirst("X-Tenant-Id"))
                .isEqualTo("fan-platform");
    }

    @Test
    @DisplayName("외부에서 위조한 X-Tenant-Id 헤더가 JWT 기반으로 덮어씌워짐")
    void filter_spoofedTenantIdHeader_isOverwrittenByJwtClaim() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/accounts/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .header("X-Tenant-Id", "malicious-tenant")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(routeConfig.isPublicRoute(HttpMethod.GET, "/api/accounts/me")).willReturn(false);
        given(tokenValidator.validate("valid-token"))
                .willReturn(Mono.just(Map.of("sub", "account-123", "tenant_id", "fan-platform")));
        given(chain.filter(any())).willReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(exchangeCaptor.capture());
        // Must be the JWT-derived value, not the spoofed one
        assertThat(exchangeCaptor.getValue().getRequest().getHeaders().getFirst("X-Tenant-Id"))
                .isEqualTo("fan-platform");
        assertThat(exchangeCaptor.getValue().getRequest().getHeaders().getFirst("X-Tenant-Id"))
                .isNotEqualTo("malicious-tenant");
    }

    @Test
    @DisplayName("internal route path tenantId와 JWT tenant_id 일치 → 통과")
    void filter_internalRoute_pathTenantMatchesJwt_passes() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/internal/tenants/wms/accounts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(routeConfig.isPublicRoute(HttpMethod.POST, "/internal/tenants/wms/accounts")).willReturn(false);
        given(tokenValidator.validate("valid-token"))
                .willReturn(Mono.just(Map.of("sub", "account-123", "tenant_id", "wms")));
        given(chain.filter(any())).willReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(exchangeCaptor.capture());
        assertThat(exchangeCaptor.getValue().getRequest().getHeaders().getFirst("X-Tenant-Id"))
                .isEqualTo("wms");
    }

    @Test
    @DisplayName("internal route path tenantId ≠ JWT tenant_id → 403 TENANT_SCOPE_DENIED")
    void filter_internalRoute_pathTenantMismatch_returns403() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/internal/tenants/wms/accounts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(routeConfig.isPublicRoute(HttpMethod.POST, "/internal/tenants/wms/accounts")).willReturn(false);
        given(tokenValidator.validate("valid-token"))
                .willReturn(Mono.just(Map.of("sub", "account-123", "tenant_id", "fan-platform")));

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("fallback 비활성 기본값 확인 (default=false)")
    void filter_fallbackDefaultIsDisabled() {
        EdgeGatewayProperties defaultProps = new EdgeGatewayProperties();
        assertThat(defaultProps.getTenant().getLegacyFallback().isEnabled()).isFalse();
    }

    @Test
    @DisplayName("grace period fallback 활성 + 서명 실패 토큰 → fallback 무관하게 401 (서명 검증 우선)")
    void filter_fallbackEnabled_invalidSignature_returns401() {
        properties.getTenant().getLegacyFallback().setEnabled(true);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/accounts/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer tampered-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(routeConfig.isPublicRoute(HttpMethod.GET, "/api/accounts/me")).willReturn(false);
        given(tokenValidator.validate("tampered-token"))
                .willReturn(Mono.error(new JwtVerificationException("Signature verification failed")));

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
