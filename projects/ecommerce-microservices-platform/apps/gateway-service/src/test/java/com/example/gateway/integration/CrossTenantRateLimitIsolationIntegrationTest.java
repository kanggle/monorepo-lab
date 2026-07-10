package com.example.gateway.integration;

import com.example.gateway.ratelimit.FailOpenRateLimiter;
import com.example.gateway.testsupport.JwksMockServer;
import com.example.gateway.testsupport.JwtTestHelper;
import com.redis.testcontainers.RedisContainer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Cross-tenant rate-limit isolation IT (TASK-BE-405 AC-1 / AC-7, M7). Proves that
 * tenant A's burst consumes only tenant A's bucket: A is rate-limited (429) while
 * tenant B — hitting the same route from the same client — is unaffected.
 *
 * <p>The {@code product-service} route's limiter is overridden to {@code burstCapacity=1}
 * / {@code replenishRate=1} via {@code @DynamicPropertySource} so the bucket drains in
 * one request, and its URI is repointed at the JWKS mock server so an allowed request
 * resolves to a 404 (not a downstream connection-refused 5xx) — the distinguishing
 * signal is "429 vs not-429" per tenant.
 *
 * <p><strong>Determinism against runner saturation (TASK-BE-497).</strong>
 * {@link FailOpenRateLimiter} <em>intentionally</em> fails open (returns "allowed",
 * {@code X-RateLimit-Remaining: -1}) when Redis is unreachable/slow — rate limiting is a
 * soft protection, and that design (TASK-BE-405) is not changed here. Under CI runner
 * saturation the limiter therefore opens, an "expected 429" request resolves to 404, and
 * the test used to flip to a <em>logic</em> failure ({@code 429 vs 404}). This test now
 * only asserts enforcement inside a cycle that <em>demonstrably enforced</em> — no
 * fail-open (counter delta 0) and the burst actually rejected ({@code a2 == 429}) — and
 * retries with fresh keys until such a cycle occurs (bounded). If Redis never enforces
 * within the window the failure surfaces as an explicit infrastructure signal, never as a
 * 429-vs-404 logic assertion. Production limiter code is byte-unchanged (AC-3).
 *
 * <p>{@code @Tag("integration")}: excluded from the Docker-free {@code test} run; CI
 * executes it against a real Redis (Testcontainers).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
@Testcontainers
@ActiveProfiles("integration-test")
@DisplayName("Cross-tenant rate-limit 격리 통합 테스트 (M7)")
class CrossTenantRateLimitIsolationIntegrationTest {

    /** Upper bound on retries while waiting for Redis to enter an enforcing state. */
    private static final Duration ENFORCING_WINDOW = Duration.ofSeconds(30);

    /**
     * If the two tenant-A requests are more than this far apart, the {@code replenishRate=1}
     * bucket may have refilled a token between them, so a non-429 second request is a
     * timing artifact (slow runner) rather than a broken limiter. Used only to classify a
     * timeout — a cycle where {@code a2 == 429} proves enforcement regardless of timing.
     */
    private static final long REPLENISH_GUARD_MILLIS = 900;

    private static final JwtTestHelper jwtHelper = new JwtTestHelper();
    private static final JwksMockServer jwksMockServer;

    static {
        try {
            jwksMockServer = new JwksMockServer(jwtHelper);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @AfterAll
    static void stopJwksMockServer() throws Exception {
        jwksMockServer.close();
    }

    @Container
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                jwksMockServer::hostJwksUrl);
        // Repoint the product-service route at the (reachable) JWKS mock and shrink its
        // bucket to 1 so the second request from the same tenant is rejected immediately.
        String reachableUri = "http://" + java.net.URI.create(jwksMockServer.hostJwksUrl()).getAuthority();
        registry.add("spring.cloud.gateway.routes[0].uri", () -> reachableUri);
        registry.add("spring.cloud.gateway.routes[0].id", () -> "product-service");
        registry.add("spring.cloud.gateway.routes[0].predicates[0]", () -> "Path=/api/products/**");
        registry.add("spring.cloud.gateway.routes[0].filters[0].name", () -> "RequestRateLimiter");
        registry.add("spring.cloud.gateway.routes[0].filters[0].args.redis-rate-limiter.replenishRate", () -> "1");
        registry.add("spring.cloud.gateway.routes[0].filters[0].args.redis-rate-limiter.burstCapacity", () -> "1");
        registry.add("spring.cloud.gateway.routes[0].filters[0].args.key-resolver",
                () -> "#{@tenantRouteKeyResolver}");
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    private String tokenForTenant(String tenantId) {
        // Carries roles=[CUSTOMER] so the token passes AccountTypeEnforcementFilter's
        // role-based admission (ADR-MONO-035 4b-2a) and actually reaches the rate limiter;
        // a bare signTokenWithIssuerAndTenant token (no roles) 403s at admission before the
        // limiter runs, masking the 429. The distinct tenant_id keys the per-tenant bucket.
        return jwtHelper.signCustomerTokenForTenant(tenantId);
    }

    /** One request's outcome: HTTP status + the {@code X-RateLimit-Remaining} header. */
    private record Probe(int status, String remaining) {}

    /**
     * One full A-A-B cycle over fresh buckets, plus the evidence needed to tell an
     * enforcing cycle from a fail-open or a refill-window one.
     *
     * @param failOpenDelta increment of {@code gateway_ratelimit_redis_unavailable_total}
     *                      across the three requests; {@code > 0} means Redis failed open
     *                      (infrastructure), so nothing about 429-vs-404 is a logic signal
     * @param a1ToA2Millis  wall-clock between the two tenant-A limiter hits; only consulted
     *                      when classifying a timeout (refill window vs broken limiter)
     */
    private record Cycle(Probe a1, Probe a2, Probe b1, double failOpenDelta, long a1ToA2Millis) {}

    private double redisUnavailableCount() {
        Counter c = meterRegistry.find(FailOpenRateLimiter.METRIC_REDIS_UNAVAILABLE).counter();
        return c == null ? 0.0 : c.count();
    }

    private void cleanRateKeys() {
        redisTemplate.keys("rate:ecommerce-gw:*")
                .flatMap(redisTemplate::delete)
                .collectList()
                .block(Duration.ofSeconds(5));
    }

    private Probe probe(String token) {
        EntityExchangeResult<byte[]> result = webTestClient.get().uri("/api/products/42")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectBody().returnResult();
        return new Probe(result.getStatus().value(),
                result.getResponseHeaders().getFirst(RedisRateLimiter.REMAINING_HEADER));
    }

    private Cycle runCycle(String tokenA, String tokenB) {
        cleanRateKeys();
        double before = redisUnavailableCount();
        long a1Start = System.nanoTime();
        Probe a1 = probe(tokenA);       // drains the burst=1 bucket (enforced → allowed)
        long a2Start = System.nanoTime();
        Probe a2 = probe(tokenA);       // bucket empty → 429 (unless it refilled or failed open)
        Probe b1 = probe(tokenB);       // independent (tenant_id, route_id) bucket → allowed
        double after = redisUnavailableCount();
        return new Cycle(a1, a2, b1, after - before, (a2Start - a1Start) / 1_000_000);
    }

    @Test
    @DisplayName("AC-1: tenant A 의 burst(429)가 tenant B 의 버킷을 소모하지 않는다")
    void tenantABurst_doesNotConsumeTenantBBucket() {
        String tokenA = tokenForTenant("tenant-a");
        String tokenB = tokenForTenant("tenant-b");

        // Retry with fresh keys until Redis demonstrably enforced this cycle: no fail-open
        // (delta 0) AND the burst was actually rejected (a2 == 429). This single gate
        // subsumes a warm-up and sidesteps the replenish window — a slow cycle whose bucket
        // refilled (a2 != 429) is simply discarded and retried. `.ignoreExceptions()` lets a
        // transient connection error under saturation retry instead of failing.
        AtomicReference<Cycle> last = new AtomicReference<>();
        Cycle enforced;
        try {
            enforced = Awaitility.await("rate limiter to enter an enforcing state")
                    .atMost(ENFORCING_WINDOW)
                    .pollDelay(Duration.ZERO)
                    .pollInterval(Duration.ofMillis(500))
                    .ignoreExceptions()
                    .until(() -> {
                        Cycle c = runCycle(tokenA, tokenB);
                        last.set(c);
                        return c;
                    }, c -> c.failOpenDelta() == 0.0 && c.a2().status() == 429);
        } catch (ConditionTimeoutException timeout) {
            throw classifyTimeout(last.get(), timeout);
        }

        // Inside a cycle Redis provably enforced. Now the actual AC-1 properties hold
        // deterministically — infrastructure has been excluded as a cause.
        Assertions.assertThat(enforced.a1().status())
                .as("tenant A first request is allowed (burst drain), not rate-limited")
                .isNotEqualTo(429);
        Assertions.assertThat(enforced.a1().remaining())
                .as("tenant A first request was enforced by Redis, not silently failed open "
                        + "(X-RateLimit-Remaining sentinel -1 means not enforced)")
                .isNotEqualTo("-1");
        Assertions.assertThat(enforced.a2().status())
                .as("tenant A second request exhausts the burst=1 bucket → 429")
                .isEqualTo(429);
        Assertions.assertThat(enforced.b1().status())
                .as("tenant B first request on the SAME route is unaffected by tenant A's "
                        + "burst — independent (tenant_id, route_id) bucket (the isolation property)")
                .isNotEqualTo(429);
    }

    /**
     * Turns a timeout into the right kind of failure. A fail-open or a refill-window cycle
     * is infrastructure and must not read as a 429-vs-404 logic defect; only a fast,
     * Redis-up cycle whose burst still failed to reject is a genuine enforcement failure.
     */
    private AssertionError classifyTimeout(Cycle last, ConditionTimeoutException timeout) {
        if (last == null) {
            return new AssertionError("Rate-limit isolation cycle did not complete a single "
                    + "attempt within " + ENFORCING_WINDOW + " (extreme runner saturation).", timeout);
        }
        if (last.failOpenDelta() > 0.0) {
            return new AssertionError(String.format(
                    "Redis rate limiter failed open on the last attempt within %s (%s delta=%.0f) "
                    + "— infrastructure saturation, not a rate-limit isolation defect. "
                    + "Statuses a1=%d a2=%d b1=%d.",
                    ENFORCING_WINDOW, FailOpenRateLimiter.METRIC_REDIS_UNAVAILABLE,
                    last.failOpenDelta(), last.a1().status(), last.a2().status(), last.b1().status()),
                    timeout);
        }
        if (last.a1ToA2Millis() >= REPLENISH_GUARD_MILLIS) {
            return new AssertionError(String.format(
                    "Rate limiter never rejected within %s, but the two tenant-A requests were "
                    + "%d ms apart (>= %d ms replenish window) — the burst refilled between them, "
                    + "a slow-runner timing artifact, not a rate-limit isolation defect.",
                    ENFORCING_WINDOW, last.a1ToA2Millis(), REPLENISH_GUARD_MILLIS), timeout);
        }
        // Redis was up (no fail-open) and the requests were adjacent, yet the burst did not
        // reject → a genuine enforcement failure. Assert so it reads as the real logic bug.
        Assertions.assertThat(last.a2().status())
                .as("tenant A second request must exhaust the burst=1 bucket and return 429 "
                        + "(Redis was up, no fail-open, requests adjacent — genuine enforcement failure)")
                .isEqualTo(429);
        return new AssertionError("Rate limiter did not reach an enforcing state within "
                + ENFORCING_WINDOW + ".", timeout);
    }
}
