package com.example.fanplatform.gateway.integration;

import com.example.fanplatform.gateway.testsupport.JwksMockServer;
import com.example.fanplatform.gateway.testsupport.JwtTestHelper;
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
 *   <li>Downstream MockWebServer that stands in for community-service /
 *       artist-service. The gateway routes /api/v1/community/** to it.</li>
 * </ul>
 *
 * <p>Tests subclass this and use {@link WebTestClient} bound to the random
 * gateway port to drive HTTP traffic.
 *
 * <h2>🔴 Why the shared infra starts in a static initializer, not {@code @BeforeAll}</h2>
 *
 * It used to start in a {@code @BeforeAll}, and every subclass of this base failed
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
 * pattern this repo already adopted for batch-worker).
 *
 * <p>🔵 This was invisible for as long as it existed because
 * {@code gateway-service:integrationTest} has no CI lane — {@code check} excludes
 * the {@code integration} tag by design, and the fan-platform integration workflow
 * lists community/artist/membership/notification only. Found while adding
 * TASK-FAN-BE-049 AC-6; the missing lane is tracked separately.
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
     * 🔴 Resets the shared {@code downstream} queues before every test — TASK-MONO-541.
     *
     * <p>One MockWebServer is shared by every subclass and cannot be per-class: the
     * route URI is wired through {@code @DynamicPropertySource} and Spring caches one
     * context for the whole suite, so a per-class server would leave the cached route
     * pointing at a dead port. Sharing it means its two queues are suite-global state.
     *
     * <p>That state leaks. {@code GatewayRateLimitIntegrationTest} enqueues 50 responses
     * on purpose and consumes only the handful the limiter lets through, so ~45 stale
     * {@code 200 {}} responses sit in the queue and are served to whatever runs next.
     * Measured: run one class alone → 10/10 green; run the suite → 9 of those same 10
     * fail with {@code expected:<201 CREATED> but was:<200 OK>} and mismatched recorded
     * paths. **Isolation passing is not the suite passing**, and this class is where the
     * difference lives.
     *
     * <p>{@code setDispatcher(new QueueDispatcher())} installs a fresh, empty response
     * queue (there is no clear() on the old one); the drain loop empties the recorded
     * request side. Safe because no subclass installs a Dispatcher of its own — they all
     * use {@code enqueue}.
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
        registry.add("fanplatform.oauth2.allowed-issuers", () -> JwtTestHelper.SAS_ISSUER);
        registry.add("fanplatform.oauth2.required-tenant-id", () -> "fan-platform");
        // Override the placeholder route so /api/v1/community/** lands on the
        // downstream MockWebServer instead of the unreachable
        // http://community-service:8080. spring.cloud.gateway.routes is a list,
        // and Spring's relaxed binding accepts indexed property keys.
        // RewritePath filter included so integration tests reflect the production
        // configuration (TASK-FAN-BE-005 fix).
        registry.add("spring.cloud.gateway.routes[0].id", () -> "community-service");
        registry.add("spring.cloud.gateway.routes[0].uri",
                () -> "http://" + downstream.getHostName() + ":" + downstream.getPort());
        registry.add("spring.cloud.gateway.routes[0].predicates[0]",
                () -> "Path=/api/v1/community/**");
        registry.add("spring.cloud.gateway.routes[0].filters[0]",
                () -> "RewritePath=/api/v1/community(?<segment>(?:/.*)?), /api/community${segment}");
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
