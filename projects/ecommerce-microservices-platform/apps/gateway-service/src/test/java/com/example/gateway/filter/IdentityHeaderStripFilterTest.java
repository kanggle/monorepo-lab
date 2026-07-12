package com.example.gateway.filter;

import com.example.apigateway.filter.IdentityHeaderStripFilter;
import com.example.gateway.config.GatewayIdentityConfig;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class IdentityHeaderStripFilterTest {

    // The bean this gateway actually registers — remove a header from GatewayIdentityConfig
    // and these assertions go red (TASK-MONO-356).
    private final IdentityHeaderStripFilter filter =
            new GatewayIdentityConfig().identityHeaderStripFilter();

    /**
     * {@code X-Seller-Scope} is the seller data-scope axis (ADR-MONO-030 Step 3 § 3.3). Three
     * services — order, product, settlement — read it <em>from the inbound request</em> in a
     * {@code SellerScopeContextFilter} and hand it to their repositories as
     * {@code AND EXISTS(... seller_id = :sellerScope)}. That filter's javadoc calls it "the
     * gateway-injected header" and says "the gateway only forwards this header on the OPERATOR
     * plane". Until TASK-MONO-356, <strong>neither was true</strong>: this gateway neither
     * stripped nor injected it, and it routes {@code /api/admin/orders/**},
     * {@code /api/admin/products/**} and {@code /api/admin/settlements/**} — so a client-supplied
     * value reached those filters intact.
     *
     * <p>It was not exploitable, by luck of sequencing: the confinement it feeds is inert
     * (nothing produces the header yet — the claim plumbing is ADR-MONO-030 Step 4), and while
     * inert, an absent header means "unrestricted" (the documented net-zero / fail-OPEN invariant
     * of ADR-MONO-025), so forging it can only narrow one's own view.
     *
     * <p><strong>The day Step 4 injects it, the hole opens</strong> — a seller confined to their
     * own {@code seller_id} could send their own value and, with nothing stripping it, escape
     * into the full-tenant view. This assertion is what stops the activation from shipping the
     * hole with it.
     */
    @Test
    void stripsTheSellerScopeHeaderThatThreeServicesTrustFromTheRequest() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/admin/orders")
                .header("X-Seller-Scope", "*")
                .header("X-Tenant-Id", "victim-tenant")
                .build();
        CapturingChain chain = new CapturingChain();

        filter.filter(MockServerWebExchange.from(request), chain).block();

        assertThat(chain.captured.getRequest().getHeaders().getFirst("X-Seller-Scope"))
                .as("a client may not choose its own seller data-scope")
                .isNull();
    }

    @Test
    void stripsAllIdentityHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/orders/123")
                .header("X-User-Id", "client-injected-id")
                .header("X-User-Email", "forged@example.com")
                .header("X-User-Role", "ECOMMERCE_OPERATOR")
                .header("X-Actor-Id", "forged-actor")
                .header("X-Account-Type", "OPERATOR")
                .header("X-Tenant-Id", "forged-tenant")
                .header("Authorization", "Bearer xyz")
                .header("X-Request-Id", "req-123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        ServerHttpRequest forwarded = chain.captured.getRequest();
        assertThat(forwarded.getHeaders().get("X-User-Id")).isNull();
        assertThat(forwarded.getHeaders().get("X-User-Email")).isNull();
        assertThat(forwarded.getHeaders().get("X-User-Role")).isNull();
        assertThat(forwarded.getHeaders().get("X-Actor-Id")).isNull();
        assertThat(forwarded.getHeaders().get("X-Account-Type")).isNull();
        assertThat(forwarded.getHeaders().get("X-Tenant-Id")).isNull();
        // Non-identity headers remain untouched
        assertThat(forwarded.getHeaders().getFirst("Authorization")).isEqualTo("Bearer xyz");
        assertThat(forwarded.getHeaders().getFirst("X-Request-Id")).isEqualTo("req-123");
    }

    /**
     * The four headers {@code platform/api-gateway-policy.md} § Identity Header Handling
     * names by hand. Asserting them as a group (rather than only inside the happy-path
     * test above) keeps the policy citation attached to the assertion: if the policy
     * grows a fifth header, this is the test that should fail.
     */
    @Test
    void stripsEveryHeaderNamedByThePlatformPolicy() {
        List<String> policyHeaders = List.of("X-User-Id", "X-User-Role", "X-User-Email", "X-Actor-Id");

        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get("/api/orders/1");
        policyHeaders.forEach(h -> builder.header(h, "forged"));
        CapturingChain chain = new CapturingChain();

        filter.filter(MockServerWebExchange.from(builder.build()), chain).block();

        HttpHeaders forwarded = chain.captured.getRequest().getHeaders();
        // getFirst is case-insensitive, so this holds regardless of how the header was cased.
        policyHeaders.forEach(h -> assertThat(forwarded.getFirst(h)).as(h).isNull());
    }

    /**
     * The regression this filter exists to prevent (TASK-BE-501).
     * <p>
     * {@code X-Actor-Id} is the header wms trusts as the audit actor in 12 controllers.
     * ecommerce's {@link JwtHeaderEnrichmentFilter} never sets it, so stripping is the
     * <em>only</em> thing standing between a client-supplied value and the backend —
     * and on a public route there is no JWT at all, so enrichment is a no-op and cannot
     * be argued as a backstop. A forged actor id reaching a backend that logs it is
     * audit-log forgery.
     */
    @Test
    void stripsForgedActorIdOnAPublicRouteWhereEnrichmentCannotBackstopIt() {
        // GET /api/products/** is permitAll in SecurityConfig — no JWT, no enrichment.
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/products/42")
                .header("X-Actor-Id", "victim-operator-uuid")
                .build();
        CapturingChain chain = new CapturingChain();

        filter.filter(MockServerWebExchange.from(request), chain).block();

        assertThat(chain.captured.getRequest().getHeaders().get("X-Actor-Id")).isNull();
    }

    @Test
    void stripsIdentityHeadersEvenWhenSomeAbsent() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/products/42")
                .header("X-User-Id", "evil-user")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        ServerHttpRequest forwarded = chain.captured.getRequest();
        assertThat(forwarded.getHeaders().get("X-User-Id")).isNull();
        assertThat(forwarded.getHeaders().get("X-User-Email")).isNull();
        assertThat(forwarded.getHeaders().get("X-User-Role")).isNull();
        assertThat(forwarded.getHeaders().get("X-Account-Type")).isNull();
    }

    @Test
    void passesRequestWithNoIdentityHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/search/products?q=shoes")
                .header("Accept", "application/json")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.captured).isNotNull();
        assertThat(chain.captured.getRequest().getHeaders().getFirst("Accept"))
                .isEqualTo("application/json");
    }

    @Test
    void runsAtHighestPrecedence() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
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
