package com.example.apigateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.apigateway.security.JwtClaims;
import com.example.apigateway.testfixtures.RecordingGatewayFilterChain;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * The enrichment mechanism. <em>Which</em> headers each gateway injects is asserted in that
 * gateway's own suite, against the bean it actually registers; what is proven here is that
 * the three {@link JwtHeaderMapping.Presence} modes mean what the domains relied on them to
 * mean. The modes are not decoration — the domains genuinely differed
 * ({@code sub}/{@code email} written when non-null, {@code tenant_id}/{@code scope} only when
 * non-blank, roles always) and collapsing them would be a behaviour change.
 */
@DisplayName("JwtHeaderEnrichmentFilter — 매핑 존재 규칙 (ALWAYS / SKIP_IF_NULL / SKIP_IF_BLANK)")
class JwtHeaderEnrichmentFilterTest {

    @Test
    @DisplayName("SKIP_IF_NULL — 값이 없으면 헤더를 쓰지 않지만, 빈 문자열은 쓴다")
    void skipIfNullWritesBlankButNotNull() {
        HttpHeaders absent = enrich(
                List.of(JwtHeaderMapping.skipIfNull("X-User-Email", JwtClaims::email)),
                jwt(Map.of()));
        assertThat(absent.containsKey("X-User-Email")).isFalse();

        HttpHeaders blank = enrich(
                List.of(JwtHeaderMapping.skipIfNull("X-User-Email", JwtClaims::email)),
                jwt(Map.of("email", "")));
        assertThat(blank.containsKey("X-User-Email")).isTrue();
        assertThat(blank.getFirst("X-User-Email")).isEmpty();
    }

    @Test
    @DisplayName("SKIP_IF_BLANK — 공백 문자열도 쓰지 않는다 (tenant_id / scope 가 의존하던 규칙)")
    void skipIfBlankAlsoSkipsBlank() {
        HttpHeaders blank = enrich(
                List.of(JwtHeaderMapping.skipIfBlank("X-Tenant-Id", JwtClaims::tenantId)),
                jwt(Map.of("tenant_id", "   ")));
        assertThat(blank.containsKey("X-Tenant-Id")).isFalse();

        HttpHeaders present = enrich(
                List.of(JwtHeaderMapping.skipIfBlank("X-Tenant-Id", JwtClaims::tenantId)),
                jwt(Map.of("tenant_id", "wms")));
        assertThat(present.getFirst("X-Tenant-Id")).isEqualTo("wms");
    }

    /**
     * The empty-role header is a security contract, not a formality: a downstream service that
     * sees {@code ""} must deny, whereas one that sees nothing at all may fall through to a
     * default — silently, and open.
     */
    @Test
    @DisplayName("ALWAYS — 역할이 없어도 빈 헤더를 반드시 쓴다 (부재는 다운스트림에서 기본값 통과를 유발한다)")
    void alwaysWritesEvenWhenValueIsEmpty() {
        HttpHeaders headers = enrich(
                List.of(JwtHeaderMapping.always("X-User-Role", JwtClaims::role)),
                jwt(Map.of()));

        assertThat(headers.containsKey("X-User-Role")).isTrue();
        assertThat(headers.getFirst("X-User-Role")).isEmpty();
    }

    @Test
    @DisplayName("ALWAYS 의 추출자가 null 을 내면 빈 문자열로 쓴다 — 헤더가 사라지지는 않는다")
    void alwaysCoercesNullToEmptyRatherThanDroppingTheHeader() {
        HttpHeaders headers = enrich(
                List.of(JwtHeaderMapping.always("X-Custom", j -> null)), jwt(Map.of()));

        assertThat(headers.containsKey("X-Custom")).isTrue();
        assertThat(headers.getFirst("X-Custom")).isEmpty();
    }

    @Test
    @DisplayName("추출자는 도메인 코드다 — 라이브러리는 그것이 무엇을 계산하는지 알 필요가 없다")
    void extractorIsDomainSuppliedCode() {
        HttpHeaders headers = enrich(
                List.of(JwtHeaderMapping.always("X-Token-Type",
                        j -> j.getClaimAsString("email") == null ? "client_credentials" : "user")),
                jwt(Map.of("scope", "read")));

        assertThat(headers.getFirst("X-Token-Type")).isEqualTo("client_credentials");
    }

    @Test
    @DisplayName("매핑 순서대로 적용된다")
    void appliesMappingsInOrder() {
        JwtHeaderEnrichmentFilter filter = new JwtHeaderEnrichmentFilter(List.of(
                JwtHeaderMapping.skipIfNull("X-User-Id", JwtClaims::subject),
                JwtHeaderMapping.always("X-User-Role", JwtClaims::role)));

        assertThat(filter.mappings()).extracting(JwtHeaderMapping::header)
                .containsExactly("X-User-Id", "X-User-Role");
    }

    /**
     * A public route carries no JWT, so nothing is written — and nothing can be forged either,
     * because {@link IdentityHeaderStripFilter} already ran. Enrichment is not the defence
     * there; strip is.
     */
    @Test
    @DisplayName("보안 컨텍스트가 없으면 아무 헤더도 쓰지 않는다 (public 라우트 = no-op)")
    void writesNothingWithoutASecurityContext() {
        JwtHeaderEnrichmentFilter filter = new JwtHeaderEnrichmentFilter(
                List.of(JwtHeaderMapping.always("X-User-Role", JwtClaims::role)));
        RecordingGatewayFilterChain chain = new RecordingGatewayFilterChain();

        filter.filter(MockServerWebExchange.from(MockServerHttpRequest.get("/public").build()), chain)
                .block();

        assertThat(chain.capturedExchange().getRequest().getHeaders().containsKey("X-User-Role"))
                .isFalse();
    }

    @Test
    @DisplayName("strip 이후, 라우팅 이전에 실행된다")
    void runsAfterAuthenticationButBeforeRouting() {
        JwtHeaderEnrichmentFilter enrich = new JwtHeaderEnrichmentFilter(List.of());
        assertThat(enrich.getOrder()).isEqualTo(-1);
        assertThat(new IdentityHeaderStripFilter().getOrder())
                .as("a header may not be trusted before the client's copy of it is removed")
                .isLessThan(enrich.getOrder());
    }

    // --- helpers ---

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

    private static HttpHeaders enrich(List<JwtHeaderMapping> mappings, Jwt jwt) {
        RecordingGatewayFilterChain chain = new RecordingGatewayFilterChain();
        new JwtHeaderEnrichmentFilter(mappings)
                .filter(MockServerWebExchange.from(MockServerHttpRequest.get("/api/x").build()), chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                        new JwtAuthenticationToken(jwt)))
                .block();
        return chain.capturedExchange().getRequest().getHeaders();
    }
}
