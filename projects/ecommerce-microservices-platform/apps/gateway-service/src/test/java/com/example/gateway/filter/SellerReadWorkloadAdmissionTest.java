package com.example.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-MONO-759 (owner decision R1, 2026-10-03) — the ONE workload branch of
 * {@link AccountTypeEnforcementFilter}: {@code /internal/sellers/**} is admitted on scope +
 * tenant, not on a role.
 *
 * <p>The decision's cost is «공개 게이트웨이에 내부 경로가 하나 생깁니다», so the cells that
 * carry weight are the ones that pin how NARROW the opening is:
 * <ul>
 *   <li>the workload token (no {@code CUSTOMER} role) is still 403 on <b>every</b> other route
 *       this gateway carries — enumerated from {@code application.yml} itself, so a route added
 *       tomorrow is covered without editing this test;</li>
 *   <li>a {@code CUSTOMER} (or operator) token cannot use {@code /internal/sellers/**};</li>
 *   <li>the workload token cannot write, cannot be for another tenant, and cannot ride a
 *       non-normalised path out of the prefix.</li>
 * </ul>
 */
@DisplayName("AccountTypeEnforcementFilter — /internal/sellers/** workload branch (TASK-MONO-759)")
class SellerReadWorkloadAdmissionTest {

    private final AccountTypeEnforcementFilter filter =
            new AccountTypeEnforcementFilter(new ObjectMapper());

    // ------------------------------------------------------------------ the admitted shape

    @Nested
    @DisplayName("the seller-read workload token on /internal/sellers/**")
    class Admitted {

        @Test
        @DisplayName("GET with scope (JSON array) + tenant ecommerce → passes")
        void getPasses() {
            assertThat(admitted(HttpMethod.GET, "/internal/sellers/s-1", workload("ecommerce"))).isTrue();
        }

        @Test
        @DisplayName("HEAD passes too (a safe method)")
        void headPasses() {
            assertThat(admitted(HttpMethod.HEAD, "/internal/sellers/s-1", workload("ecommerce"))).isTrue();
        }

        @Test
        @DisplayName("scope as an RFC 6749 space-delimited string is read as well")
        void spaceDelimitedScope() {
            Jwt t = jwt(Map.of("tenant_id", "ecommerce", "scope", "openid store.seller.read"));
            assertThat(admitted(HttpMethod.GET, "/internal/sellers/s-1", t)).isTrue();
        }
    }

    @Nested
    @DisplayName("🔴 refused on /internal/sellers/** — 403, chain never called")
    class Refused {

        @Test
        @DisplayName("🔴 a CUSTOMER token (tenant ecommerce, no seller-read scope)")
        void customerToken() {
            Jwt customer = jwt(Map.of("tenant_id", "ecommerce", "roles", List.of("CUSTOMER"),
                    "scope", List.of("openid", "profile")));
            assertRefused(HttpMethod.GET, "/internal/sellers/s-1", customer);
        }

        @Test
        @DisplayName("an ECOMMERCE_OPERATOR token")
        void operatorToken() {
            Jwt operator = jwt(Map.of("tenant_id", "ecommerce", "roles", List.of("ECOMMERCE_OPERATOR")));
            assertRefused(HttpMethod.GET, "/internal/sellers/s-1", operator);
        }

        @Test
        @DisplayName("🔴 the right scope for another tenant · the wildcard · its own un-exchanged fan tenant · no tenant")
        void wrongTenant() {
            assertRefused(HttpMethod.GET, "/internal/sellers/s-1", workload("demo-corp"));
            assertRefused(HttpMethod.GET, "/internal/sellers/s-1", workload("*"));
            assertRefused(HttpMethod.GET, "/internal/sellers/s-1", workload("fan-platform"));
            assertRefused(HttpMethod.GET, "/internal/sellers/s-1",
                    jwt(Map.of("scope", List.of("store.seller.read"))));
        }

        @Test
        @DisplayName("🔴 AC-3 — the read credential cannot write: POST / PUT / PATCH / DELETE")
        void noWriteVerb() {
            for (HttpMethod m : List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)) {
                assertRefused(m, "/internal/sellers/s-1", workload("ecommerce"));
            }
        }

        @Test
        @DisplayName("🔴 a path that starts with the prefix but is not normalised")
        void nonNormalisedPaths() {
            for (String p : List.of(
                    "/internal/sellers/../../api/orders/1",
                    "/internal/sellers/./s-1",
                    "/internal/sellers//s-1",
                    "/internal/sellers/s-1;jsessionid=x",
                    "/internal/sellers/%2e%2e/x")) {
                assertRefused(HttpMethod.GET, p, workload("ecommerce"));
            }
        }

        @Test
        @DisplayName("a look-alike prefix is not the seller path — it falls back to the CUSTOMER rule and is refused")
        void lookAlikePrefix() {
            assertThat(AccountTypeEnforcementFilter.isSellerReadPath("/internal/sellers-x/1")).isFalse();
            assertRefused(HttpMethod.GET, "/internal/sellers-x/1", workload("ecommerce"));
        }
    }

    // ------------------------------------------------------------------ every other route

    /**
     * 🔴 The workload token carries no {@code CUSTOMER} role, so on every route other than the
     * internal one it must still be 403 — the opening is one prefix wide, not "machine tokens
     * are welcome now". Routes are read from the shipped {@code application.yml} predicates.
     */
    @Test
    @DisplayName("🔴 the CUSTOMER-less workload token is 403 on EVERY other route this gateway carries (all verbs)")
    void workloadTokenRefusedOnEveryNonInternalRoute() throws IOException {
        List<String> concretePaths = shippedRoutePathsExceptInternal();

        // Non-vacuity: the parse found the routes, including the seller operator plane that a
        // seller-read credential must above all not reach.
        assertThat(concretePaths).contains("/api/admin/sellers/x", "/api/orders/x", "/api/products/x");
        assertThat(concretePaths).hasSizeGreaterThanOrEqualTo(15);

        for (String path : concretePaths) {
            for (HttpMethod m : List.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PATCH, HttpMethod.DELETE)) {
                assertRefused(m, path, workload("ecommerce"));
            }
        }
    }

    @Test
    @DisplayName("🔵 control — a CUSTOMER token is still admitted on a consumer route (the old rule is intact)")
    void customerStillAdmittedOnConsumerRoute() {
        Jwt customer = jwt(Map.of("tenant_id", "ecommerce", "roles", List.of("CUSTOMER")));
        assertThat(admitted(HttpMethod.GET, "/api/orders/1", customer)).isTrue();
    }

    // ------------------------------------------------------------------ helpers

    private void assertRefused(HttpMethod method, String path, Jwt token) {
        MockServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.method(method, raw(path)).build());
        CapturingChain chain = new CapturingChain();
        run(exchange, chain, token);
        assertThat(chain.called).as("%s %s must not reach the route", method, path).isFalse();
        assertThat(exchange.getResponse().getStatusCode())
                .as("%s %s", method, path).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private boolean admitted(HttpMethod method, String path, Jwt token) {
        MockServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.method(method, raw(path)).build());
        CapturingChain chain = new CapturingChain();
        run(exchange, chain, token);
        return chain.called && exchange.getResponse().getStatusCode() != HttpStatus.FORBIDDEN;
    }

    /**
     * The path as a raw {@link java.net.URI}: {@code MockServerHttpRequest.method(m, String)} goes
     * through {@code UriComponentsBuilder}, which collapses {@code //} — a request a real server
     * would receive verbatim. Measured: the first run of {@code nonNormalisedPaths} passed the
     * {@code //} case to the chain only because the builder had already "fixed" it.
     */
    private static java.net.URI raw(String path) {
        return java.net.URI.create("http://gateway.test" + path);
    }

    private void run(MockServerWebExchange exchange, CapturingChain chain, Jwt jwt) {
        filter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(new JwtAuthenticationToken(jwt)))
                .block();
    }

    /** The exchanged artist-service-client token: no roles, the seller-read scope, the given tenant. */
    private static Jwt workload(String tenant) {
        return jwt(Map.of("tenant_id", tenant, "scope", List.of("store.seller.read"),
                "aud", List.of("artist-service-client")));
    }

    private static Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("artist-service-client")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claims(c -> {
                    c.put("iss", "test");
                    c.putAll(claims);
                })
                .build();
    }

    /** Every {@code - Path=} predicate in the shipped routes, made concrete ({@code **} → {@code x}). */
    private static List<String> shippedRoutePathsExceptInternal() throws IOException {
        Path yml = locate("src/main/resources/application.yml");
        String text = Files.readString(yml, StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("(?m)^\\s*-\\s*Path=(\\S+)\\s*$").matcher(text);
        List<String> out = new ArrayList<>();
        while (m.find()) {
            for (String pattern : m.group(1).split(",")) {
                if (pattern.startsWith("/internal/sellers")) {
                    continue;
                }
                out.add(pattern.replace("/**", "/x"));
            }
        }
        return out;
    }

    private static Path locate(String relative) {
        Path cur = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && cur != null; i++, cur = cur.getParent()) {
            Path c = cur.resolve(relative);
            if (Files.isRegularFile(c)) {
                return c;
            }
            Path module = cur.resolve("projects/ecommerce-microservices-platform/apps/gateway-service")
                    .resolve(relative);
            if (Files.isRegularFile(module)) {
                return module;
            }
        }
        throw new IllegalStateException("cannot locate " + relative);
    }

    private static final class CapturingChain implements GatewayFilterChain {
        boolean called = false;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.called = true;
            return Mono.empty();
        }
    }
}
