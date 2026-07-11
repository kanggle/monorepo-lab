package com.example.erp.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.apigateway.filter.IdentityHeaderStripFilter;
import com.example.apigateway.filter.JwtHeaderEnrichmentFilter;
import com.example.apigateway.filter.JwtHeaderMapping;
import com.example.apigateway.filter.RequestIdFilter;
import com.example.apigateway.filter.RetryAfterFilter;
import com.example.erp.gateway.config.GatewayIdentityConfig;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
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

/**
 * erp's edge-filter policy, asserted against the beans this gateway actually registers.
 *
 * <p>The filters themselves are the library's and are tested there. What is erp's — and what
 * this file pins — is <em>which</em> headers get stripped and injected. Remove a mapping from
 * {@link GatewayIdentityConfig} and these assertions go red.
 */
@DisplayName("erp 엣지 필터 — strip 집합 · 주입 헤더 · 순서 불변식")
class GatewayEdgeFilterTest {

    private final GatewayIdentityConfig config = new GatewayIdentityConfig();
    private final IdentityHeaderStripFilter strip = config.identityHeaderStripFilter();
    private final JwtHeaderEnrichmentFilter enrich = config.jwtHeaderEnrichmentFilter();

    /**
     * Nothing in erp reads these headers today (census: zero {@code X-*} identity headers
     * consumed across the four erp services). That is exactly why the strip has to
     * exist now — the first reader must not inherit a forged value. TASK-MONO-356's
     * {@code X-Seller-Scope} is what happens when it doesn't.
     */
    @Test
    @DisplayName("클라이언트가 실어보낸 신원 헤더를 전부 제거한다 (읽는 곳이 아직 없어도)")
    void stripsEveryClientSuppliedIdentityHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/erp/masterdata/employees/1")
                .header("X-User-Id", "forged-user")
                .header("X-User-Email", "forged@example.com")
                .header("X-User-Role", "ERP_ADMIN")
                .header("X-Actor-Id", "forged-actor")
                .header("X-Account-Id", "forged-account")
                .header("X-Tenant-Id", "victim-tenant")
                .header("X-Roles", "ADMIN")
                .header("X-Account-Type", "OPERATOR")
                .header("Authorization", "Bearer xyz")
                .build();
        CapturingChain chain = new CapturingChain();

        strip.filter(MockServerWebExchange.from(request), chain).block();

        HttpHeaders forwarded = chain.captured.getRequest().getHeaders();
        IdentityHeaderStripFilter.BASELINE_HEADERS.forEach(
                h -> assertThat(forwarded.getFirst(h)).as(h).isNull());
        assertThat(forwarded.getFirst("Authorization"))
                .as("non-identity headers are untouched")
                .isEqualTo("Bearer xyz");
    }

    @Test
    @DisplayName("검증된 클레임에서 신원 헤더를 주입한다")
    void injectsVerifiedIdentityHeaders() {
        HttpHeaders headers = enrich(jwt(Map.of(
                "email", "ops@example.com", "role", "ERP_ADMIN", "tenant_id", "erp")));

        assertThat(headers.getFirst("X-User-Id")).isEqualTo("user-1");
        assertThat(headers.getFirst("X-Actor-Id")).isEqualTo("user-1");
        assertThat(headers.getFirst("X-User-Email")).isEqualTo("ops@example.com");
        assertThat(headers.getFirst("X-User-Role")).isEqualTo("ERP_ADMIN");
        assertThat(headers.getFirst("X-Tenant-Id")).isEqualTo("erp");
    }

    /**
     * An empty value means "no authorized role" and must deny. An absent header would let a
     * downstream service that forgot the null case fall through to a default — silently, open.
     */
    @Test
    @DisplayName("역할이 없어도 X-User-Role 을 빈 값으로 반드시 쓴다 (부재는 기본값 통과를 유발한다)")
    void alwaysWritesTheRoleHeaderEvenWhenEmpty() {
        HttpHeaders headers = enrich(jwt(Map.of("tenant_id", "erp")));

        assertThat(headers.containsKey("X-User-Role")).isTrue();
        assertThat(headers.getFirst("X-User-Role")).isEmpty();
    }

    @Test
    @DisplayName("erp 는 X-Roles / X-Scopes / X-Token-Type 을 주입하지 않는다")
    void doesNotInjectHeadersOtherDomainsUse() {
        HttpHeaders headers = enrich(jwt(Map.of("tenant_id", "erp", "role", "ERP_ADMIN")));

        assertThat(headers.getFirst("X-Roles")).isNull();
        assertThat(headers.getFirst("X-Scopes")).isNull();
        assertThat(headers.getFirst("X-Token-Type")).isNull();
        assertThat(headers.getFirst("X-Account-Type")).isNull();
    }

    @Test
    @DisplayName("매핑 목록이 erp 정책 그대로다")
    void mappingsMatchThePolicy() {
        assertThat(enrich.mappings()).extracting(JwtHeaderMapping::header)
                .containsExactly("X-User-Id", "X-Actor-Id", "X-User-Email", "X-User-Role",
                        "X-Tenant-Id");
    }

    /**
     * The invariant spans the library/service boundary — strip and enrich are one mechanism, and
     * reversing them is an impersonation vector — so it can only be asserted from here.
     */
    @Test
    @DisplayName("순서 불변식: strip → enrich → requestId → retryAfter")
    void identityStripRunsBeforeEverythingElse() {
        assertThat(strip.getOrder())
                .as("a header may not be trusted before the client's copy of it is removed")
                .isLessThan(enrich.getOrder())
                .isLessThan(new RequestIdFilter().getOrder())
                .isLessThan(new RetryAfterFilter().getOrder());
        assertThat(new RequestIdFilter().getOrder()).isLessThan(new RetryAfterFilter().getOrder());
    }

    // --- helpers ---

    private HttpHeaders enrich(Jwt jwt) {
        CapturingChain chain = new CapturingChain();
        enrich.filter(
                        MockServerWebExchange.from(
                                MockServerHttpRequest.get("/api/erp/masterdata/employees/1").build()),
                        chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                        new JwtAuthenticationToken(jwt)))
                .block();
        return chain.captured.getRequest().getHeaders();
    }

    private static Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder b = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("http://iam.local")
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        claims.forEach(b::claim);
        return b.build();
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
