package com.example.apigateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.apigateway.testfixtures.RecordingGatewayFilterChain;
import java.lang.reflect.Constructor;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

@DisplayName("IdentityHeaderStripFilter — baseline 은 바닥이며, 오직 더할 수만 있다")
class IdentityHeaderStripFilterTest {

    @Test
    @DisplayName("no-arg 는 baseline 8개를 정확히 제거한다")
    void baselineStripsTheEightSharedHeaders() {
        IdentityHeaderStripFilter filter = new IdentityHeaderStripFilter();

        assertThat(filter.strippedHeaders()).containsExactlyInAnyOrder(
                "X-User-Id", "X-User-Email", "X-User-Role", "X-Actor-Id",
                "X-Account-Id", "X-Tenant-Id", "X-Roles", "X-Account-Type");
    }

    @Test
    @DisplayName("추가 헤더는 baseline 에 합집합으로 얹힌다 (scm: X-Token-Type, X-Scopes)")
    void additionalHeadersUnionWithBaseline() {
        IdentityHeaderStripFilter filter =
                new IdentityHeaderStripFilter(Set.of("X-Token-Type", "X-Scopes"));

        assertThat(filter.strippedHeaders())
                .containsAll(IdentityHeaderStripFilter.BASELINE_HEADERS)
                .contains("X-Token-Type", "X-Scopes")
                .hasSize(10);
    }

    /**
     * The add-only asymmetry (ADR-MONO-048 § D3) is not a convention to be remembered — it is
     * a property of the type. Narrowing the strip set is the defect TASK-BE-501/502 closed
     * (wms was not stripping the {@code X-Tenant-Id} its own spec named, so a forged one
     * crossed the edge). An API that could express "strip fewer" would reopen that hole while
     * looking like a setting rather than like a vulnerability.
     */
    @Test
    @DisplayName("baseline 을 빼거나 교체하는 API 는 존재하지 않는다 — 구멍의 재개방이 '설정' 처럼 보이지 않도록")
    void thereIsNoApiThatCanNarrowTheBaseline() {
        for (Constructor<?> c : IdentityHeaderStripFilter.class.getConstructors()) {
            assertThat(c.getParameterCount())
                    .as("a constructor taking the full set could replace the baseline; "
                            + "only an additions-set may be accepted")
                    .isLessThanOrEqualTo(1);
        }
        assertThat(new IdentityHeaderStripFilter(Set.of()).strippedHeaders())
                .as("an empty additions-set still strips the whole baseline")
                .isEqualTo(IdentityHeaderStripFilter.BASELINE_HEADERS);

        assertThat(IdentityHeaderStripFilter.class.getMethods())
                .as("no mutator may remove a header after construction")
                .noneMatch(m -> m.getName().startsWith("set") || m.getName().startsWith("remove"));
    }

    @Test
    @DisplayName("실제로 요청에서 제거하고, 비-신원 헤더는 건드리지 않는다")
    void removesConfiguredHeadersAndNothingElse() {
        IdentityHeaderStripFilter filter = new IdentityHeaderStripFilter(Set.of("X-Scopes"));
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/things")
                .header("X-User-Id", "forged")
                .header("X-Tenant-Id", "victim-tenant")
                .header("X-Scopes", "forged.scope")
                .header("Authorization", "Bearer xyz")
                .header("X-Request-Id", "req-1")
                .build();
        RecordingGatewayFilterChain chain = new RecordingGatewayFilterChain();

        filter.filter(MockServerWebExchange.from(request), chain).block();

        HttpHeaders forwarded = chain.capturedExchange().getRequest().getHeaders();
        assertThat(forwarded.getFirst("X-User-Id")).isNull();
        assertThat(forwarded.getFirst("X-Tenant-Id")).isNull();
        assertThat(forwarded.getFirst("X-Scopes")).isNull();
        assertThat(forwarded.getFirst("Authorization")).isEqualTo("Bearer xyz");
        assertThat(forwarded.getFirst("X-Request-Id")).isEqualTo("req-1");
    }

    /**
     * On a route with no JWT — an HMAC-authenticated webhook, a public endpoint — enrichment
     * is a no-op and cannot act as a backstop. Stripping is the entire defence there.
     */
    @Test
    @DisplayName("JWT 없는 라우트(웹훅)에서도 위조 헤더를 제거한다 — 여기선 strip 이 유일한 방어다")
    void stripsForgedHeadersOnRoutesThatCarryNoJwt() {
        RecordingGatewayFilterChain chain = new RecordingGatewayFilterChain();
        MockServerHttpRequest request = MockServerHttpRequest.post("/webhooks/erp/inbound")
                .header("X-Actor-Id", "victim-operator-uuid")
                .header("X-Tenant-Id", "victim-tenant")
                .build();

        new IdentityHeaderStripFilter().filter(MockServerWebExchange.from(request), chain).block();

        HttpHeaders forwarded = chain.capturedExchange().getRequest().getHeaders();
        assertThat(forwarded.getFirst("X-Actor-Id")).isNull();
        assertThat(forwarded.getFirst("X-Tenant-Id")).isNull();
    }

    @Test
    @DisplayName("최고 우선순위로 실행 — 헤더를 신뢰하기 전에 지워야 한다")
    void runsAtHighestPrecedence() {
        assertThat(new IdentityHeaderStripFilter().getOrder())
                .isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }
}
