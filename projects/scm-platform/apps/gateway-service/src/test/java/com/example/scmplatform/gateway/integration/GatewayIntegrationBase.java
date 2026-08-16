package com.example.scmplatform.gateway.integration;

import com.example.scmplatform.gateway.testsupport.JwksMockServer;
import com.example.scmplatform.gateway.testsupport.JwtTestHelper;
import com.redis.testcontainers.RedisContainer;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.QueueDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared infrastructure for {@code @Tag("integration")} integration tests:
 *
 * <ul>
 *   <li>Redis 7 Testcontainer for the rate-limit backend.</li>
 *   <li>JWKS MockWebServer publishing the test public key — the gateway's
 *       Resource Server fetches this to verify JWT signatures.</li>
 *   <li>Downstream MockWebServer that stands in for procurement-service /
 *       inventory-visibility-service. The gateway routes
 *       {@code /api/v1/procurement/**} to it.</li>
 * </ul>
 *
 * <p>Tests subclass this and use {@link WebTestClient} bound to the random
 * gateway port to drive HTTP traffic.
 *
 * <h2>🔴 Why the shared infra starts in a static initializer, not {@code @BeforeAll}</h2>
 *
 * It used to start in a {@code @BeforeAll}, and all five subclasses of this base failed
 * with {@code initializationError} — {@code NullPointerException: ... "jwks" is null}
 * — because {@code @DynamicPropertySource} suppliers are evaluated while the Spring
 * context is built, and under {@link TestInstance.Lifecycle#PER_CLASS} the test
 * instance (and therefore the context load) is created <em>before</em>
 * {@code @BeforeAll} runs. The fields the suppliers close over were still null.
 *
 * <p>A static initializer runs on class load, which is unconditionally before any of
 * that, so the ordering hazard disappears rather than being re-tuned. Teardown is
 * left to Ryuk and JVM exit instead of an {@code @AfterAll}: a managed stop here
 * tears the container down after the first subclass while Spring's context cache
 * keeps handing the next subclass a connection to it (the singleton-container
 * pattern this repo already adopted for batch-worker). That second hazard is not
 * hypothetical here the way it was for fan-platform's single-class suite — this
 * suite has five subclasses across two cached contexts
 * ({@link GatewayRouteRewriteTest} contributes its own {@code @DynamicPropertySource},
 * which gives it a distinct context cache key), so an {@code @AfterAll} would fire
 * with live consumers still bound to the container.
 *
 * <p>🔵 This was invisible for as long as it existed because
 * {@code gateway-service:integrationTest} had no CI lane — {@code check} excludes the
 * {@code integration} tag by design, and the scm-platform integration workflow listed
 * procurement/inventory-visibility/demand-planning/logistics only. Identical defect,
 * identical cause and identical invisibility to fan-platform's gateway
 * (TASK-FAN-BE-049); both were found by the population recount in TASK-MONO-541 AC-4
 * and this one is closed by TASK-MONO-542, which adds the lane in the same change.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
public abstract class GatewayIntegrationBase {

    protected static final RedisContainer REDIS = new RedisContainer(
            DockerImageName.parse("redis:7-alpine"));

    protected static JwtTestHelper jwt;
    protected static JwksMockServer jwks;
    protected static MockWebServer downstream;

    @LocalServerPort
    protected int gatewayPort;

    @Autowired
    protected WebTestClient webTestClient;

    static {
        // Runs on class load — before the test instance exists, and therefore before
        // any @DynamicPropertySource supplier below can be evaluated. See the class
        // javadoc for what @BeforeAll did instead.
        try {
            REDIS.start();
            jwt = new JwtTestHelper();
            jwks = new JwksMockServer(jwt);
            downstream = new MockWebServer();
            downstream.start();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * 🔴 Resets the shared {@code downstream} queues before every test.
     *
     * <p>One MockWebServer is shared by every subclass and cannot be per-class: the
     * route URIs are wired through {@code @DynamicPropertySource} and Spring caches the
     * context, so a per-class server would leave a cached route pointing at a dead port.
     * Sharing it means its two queues — enqueued responses, and recorded requests — are
     * suite-global state.
     *
     * <p>That state leaks, and this suite leaks harder than fan-platform's did.
     * {@link GatewayRateLimitIntegrationTest} enqueues 50 responses on purpose,
     * {@code break}s out of its loop at the first 429 (typically around the 6th
     * request), and never calls {@code takeRequest} at all — so it leaves roughly 44
     * stale {@code 200 {}} responses AND every request it made sitting in the recorded
     * queue. {@link GatewayRouteRewriteTest} then asserts on
     * {@code takeRequest().getPath()}, so it would read a sibling's request and compare
     * it against its own expected path.
     *
     * <p>{@code setDispatcher(new QueueDispatcher())} installs a fresh, empty response
     * queue (there is no clear() on the old one); the drain loop empties the recorded
     * request side. Safe because no subclass installs a Dispatcher of its own — verified,
     * not assumed: the only {@code setDispatcher} call in this module's test sources is
     * {@code JwksMockServer}'s, on its own separate server instance.
     *
     * <p>The precedent is TASK-MONO-541, where the fan gateway suite passed 10/10 in
     * isolation and failed 9-of-those-10 as a suite for exactly this reason.
     * <strong>Isolation passing is not the suite passing</strong>, so this guard shipped
     * with the harness fix rather than after CI rediscovered it.
     *
     * <p>🔵 Measured on this suite rather than inherited from fan-platform: disabling the
     * body of this method makes {@code GatewayRouteRewriteTest} fail on
     * {@code inventoryVisibilityRouteRewritesV1Prefix} and
     * {@code procurementRoutePreservesPathVariablesAndSegments} — precisely the tests that
     * assert on {@code takeRequest().getPath()}. Porting a fix is not measuring that the
     * destination needs it.
     */
    @BeforeEach
    void resetDownstreamQueues() throws InterruptedException {
        downstream.setDispatcher(new QueueDispatcher());
        while (downstream.takeRequest(1, TimeUnit.MILLISECONDS) != null) {
            // discard a sibling test's recorded request
        }
    }

    @DynamicPropertySource
    static void wireProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> jwks.hostJwksUrl());
        // TASK-MONO-367 (2026-08-01 sunset, LANDED): matches production post-sunset — SAS
        // issuer only, no trailing legacy `iam` entry (TASK-BE-398 retired the only flow
        // that minted it).
        registry.add("scmplatform.oauth2.allowed-issuers", () -> JwtTestHelper.SAS_ISSUER);
        registry.add("scmplatform.oauth2.required-tenant-id", () -> "scm");
        // Override the placeholder route so /api/v1/procurement/** lands on the
        // downstream MockWebServer instead of the unreachable
        // http://procurement-service:8080. spring.cloud.gateway.routes is a list,
        // and Spring's relaxed binding accepts indexed property keys.
        // RewritePath filter included so integration tests reflect the production
        // configuration.
        registry.add("spring.cloud.gateway.routes[0].id", () -> "procurement-service");
        registry.add("spring.cloud.gateway.routes[0].uri",
                () -> "http://" + downstream.getHostName() + ":" + downstream.getPort());
        registry.add("spring.cloud.gateway.routes[0].predicates[0]",
                () -> "Path=/api/v1/procurement/**");
        registry.add("spring.cloud.gateway.routes[0].filters[0]",
                () -> "RewritePath=/api/v1/procurement/(?<segment>.*), /api/procurement/${segment}");
        registry.add("spring.cloud.gateway.routes[0].filters[1].name",
                () -> "RequestRateLimiter");
        registry.add("spring.cloud.gateway.routes[0].filters[1].args.redis-rate-limiter.replenishRate",
                () -> "1");
        registry.add("spring.cloud.gateway.routes[0].filters[1].args.redis-rate-limiter.burstCapacity",
                () -> "5");
        registry.add("spring.cloud.gateway.routes[0].filters[1].args.redis-rate-limiter.requestedTokens",
                () -> "1");
        registry.add("spring.cloud.gateway.routes[0].filters[1].args.key-resolver",
                () -> "#{@accountKeyResolver}");
    }
}
