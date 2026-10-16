package com.example.gateway.filter;

import com.example.gateway.config.EdgeGatewayProperties;
import com.example.gateway.route.RouteConfig;
import com.example.gateway.security.TokenValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.security.jwt.JwtVerificationException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
    // TASK-MONO-737: the admin subtree keeps the operator's selected X-Tenant-Id
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TASK-MONO-737: /api/admin/** 는 콘솔이 고른 X-Tenant-Id 를 그대로 전달 (X-Account-ID 는 여전히 제거)")
    void filter_adminSubtree_keepsCallerTenant_stillStripsIdentityHeaders() {
        String path = "/api/admin/accounts/78740d21/lock";
        MockServerHttpRequest request = MockServerHttpRequest.post(path)
                .header("X-Tenant-Id", "ecommerce")
                .header("X-Account-ID", "spoofed-id")
                .header("X-Device-Id", "spoofed-device")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(routeConfig.isPublicRoute(HttpMethod.POST, path)).willReturn(true);
        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
        given(chain.filter(exchangeCaptor.capture())).willReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        HttpHeaders downstream = exchangeCaptor.getValue().getRequest().getHeaders();
        assertThat(downstream.get("X-Tenant-Id")).containsExactly("ecommerce");
        assertThat(downstream.getFirst("X-Account-ID")).isNull();
        assertThat(downstream.getFirst("X-Device-Id")).isNull();
    }

    @Test
    @DisplayName("TASK-MONO-737 대조군: /api/admin 밖의 public 경로는 X-Tenant-Id 를 계속 제거")
    void filter_publicRouteOutsideAdminSubtree_stillStripsTenant() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/login")
                .header("X-Tenant-Id", "ecommerce")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        given(routeConfig.isPublicRoute(HttpMethod.POST, "/api/auth/login")).willReturn(true);
        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
        given(chain.filter(exchangeCaptor.capture())).willReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchangeCaptor.getValue().getRequest().getHeaders().getFirst("X-Tenant-Id")).isNull();
    }

    @Test
    @DisplayName("TASK-MONO-737 경계: '/api/administrator' 같은 접두 유사 경로는 관리 서브트리가 아니다")
    void keepsCallerTenant_onlyTheAdminSubtree() {
        assertThat(JwtAuthenticationFilter.keepsCallerTenant("/api/admin/accounts")).isTrue();
        assertThat(JwtAuthenticationFilter.keepsCallerTenant("/api/administrator/x")).isFalse();
        assertThat(JwtAuthenticationFilter.keepsCallerTenant("/api/accounts/me")).isFalse();
        assertThat(JwtAuthenticationFilter.keepsCallerTenant("/internal/tenants/wms/accounts")).isFalse();
        assertThat(JwtAuthenticationFilter.keepsCallerTenant(null)).isFalse();
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

        // Lenient: a correct filter never calls the chain. Stubbed anyway so that a forwarding
        // (defective) filter completes normally and the red is the status assertion below,
        // not a NullPointerException from an unstubbed chain.
        lenient().when(chain.filter(any())).thenReturn(Mono.empty());

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

    // -----------------------------------------------------------------------
    // TASK-BE-638: the tenant LIFECYCLE paths are not tenant-scoped, so no tenant token
    // may reach them through this edge. Before the fix the scope regex
    // `^/internal/tenants/([^/]+)(/.*)?$` made the trailing segment optional, so
    // `PATCH /internal/tenants/{self}` only needed tenant_id == {self} (a tenant could
    // change its own status), and `POST` / `GET /internal/tenants` had no path tenant
    // at all, so no check ran. The only caller of these is admin-service, which calls
    // account-service directly (admin-to-account.md), never through this gateway.
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "{0} {1} (tenant_id={2}) → 403, not forwarded")
    @CsvSource({
            // AC-1 — the tenant's own token on its own lifecycle row
            "PATCH, /internal/tenants/wms, wms",
            "GET,   /internal/tenants/wms, wms",
            // AC-2 — create / list carry no path tenant
            "POST,  /internal/tenants, wms",
            "GET,   /internal/tenants, wms",
            // Edge case — the platform wildcard is refused too, not waved through
            "POST,  /internal/tenants, *",
            "PATCH, /internal/tenants/wms, *",
            // trailing slash must not reopen the bare path
            "GET,   /internal/tenants/, wms",
            "PATCH, /internal/tenants/wms/, wms",
    })
    @DisplayName("TASK-BE-638: 테넌트 라이프사이클 경로(서브리소스 없음)는 어떤 테넌트 토큰으로도 403")
    void filter_tenantLifecyclePath_isRefusedForAnyTenantToken(String method, String path, String tenantId) {
        MockServerWebExchange exchange = exchangeFor(HttpMethod.valueOf(method), path);

        given(routeConfig.isPublicRoute(any(), anyString())).willReturn(false);
        given(tokenValidator.validate("valid-token"))
                .willReturn(Mono.just(Map.of("sub", "product-service-client", "tenant_id", tenantId)));

        // Lenient: a correct filter never calls the chain. Stubbed anyway so that a forwarding
        // (defective) filter completes normally and the red is the status assertion below,
        // not a NullPointerException from an unstubbed chain.
        lenient().when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("TENANT_SCOPE_DENIED");
        verify(chain, never()).filter(any());
    }

    @ParameterizedTest(name = "{0} {1} → 403, not forwarded")
    @CsvSource({
            "PATCH, /internal/tenants/wms/..",
            "PATCH, /internal/tenants/wms/.",
            "PATCH, /internal/tenants/wms/%2e%2e",
            "PATCH, /internal/tenants/wms/%2E%2E",
            "PATCH, /internal/tenants/wms/accounts%2F..",
            "PATCH, /internal/tenants/wms;x=1/accounts",
            "PATCH, /internal/tenants/wms/accounts%5C..",
    })
    @DisplayName("TASK-BE-638: 내부 경로의 점 세그먼트·인코딩된 구분자·matrix 는 서브리소스로 위장할 수 없다")
    void filter_internalPathTraversalShapes_areRefused(String method, String rawPath) {
        MockServerWebExchange exchange = exchangeFor(HttpMethod.valueOf(method), rawPath);

        given(routeConfig.isPublicRoute(any(), anyString())).willReturn(false);
        given(tokenValidator.validate("valid-token"))
                .willReturn(Mono.just(Map.of("sub", "product-service-client", "tenant_id", "wms")));

        // Lenient: a correct filter never calls the chain. Stubbed anyway so that a forwarding
        // (defective) filter completes normally and the red is the status assertion below,
        // not a NullPointerException from an unstubbed chain.
        lenient().when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(chain, never()).filter(any());
    }

    @ParameterizedTest(name = "control: {0} {1} (tenant_id=wms) → forwarded")
    @CsvSource({
            "POST,  /internal/tenants/wms/accounts",
            "POST,  /internal/tenants/wms/accounts:bulk",
            "POST,  /internal/tenants/wms/identities:resolveOrCreate",
            "PATCH, /internal/tenants/wms/accounts/acc-1/status",
            "PATCH, /internal/tenants/wms/accounts/acc-1/roles:add",
            "PATCH, /internal/tenants/wms/accounts/acc-1/site-roles:grant",
            "GET,   /internal/tenants/wms/entitled-domains",
            "PUT,   /internal/tenants/wms/consumer-members/acc-1",
    })
    @DisplayName("TASK-BE-638 대조군: 테넌트 범위 서브리소스는 자기 토큰으로 그대로 통과")
    void filter_tenantScopedSubResource_stillForwarded(String method, String path) {
        MockServerWebExchange exchange = exchangeFor(HttpMethod.valueOf(method), path);

        given(routeConfig.isPublicRoute(any(), anyString())).willReturn(false);
        given(tokenValidator.validate("valid-token"))
                .willReturn(Mono.just(Map.of("sub", "product-service-client", "tenant_id", "wms")));
        given(chain.filter(any())).willReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(exchangeCaptor.capture());
        assertThat(exchangeCaptor.getValue().getRequest().getHeaders().getFirst("X-Tenant-Id"))
                .isEqualTo("wms");
    }

    /** Raw URI on purpose — the String overloads would re-encode {@code %2e} into {@code %252e}. */
    private static MockServerWebExchange exchangeFor(HttpMethod method, String rawPath) {
        MockServerHttpRequest request = MockServerHttpRequest
                .method(method, URI.create("http://gateway" + rawPath))
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .build();
        return MockServerWebExchange.from(request);
    }
}
