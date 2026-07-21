package com.wms.gateway.integration;

import com.wms.gateway.testsupport.JwksMockServer;
import com.wms.gateway.testsupport.JwtTestHelper;
import com.redis.testcontainers.RedisContainer;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.QueueDispatcher;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared infrastructure for wms gateway {@code @Tag("integration")} full-context tests
 * (TASK-MONO-458 — the wms gateway previously had unit tests only; this base adds the
 * missing in-process, route-through-the-real-filter-chain layer).
 *
 * <h2>What boots</h2>
 * <ul>
 *   <li>The whole {@code GatewayServiceApplication} on a random port
 *       ({@code @SpringBootTest(RANDOM_PORT)}) — the real Spring Cloud Gateway route table,
 *       the shared {@code libs/java-gateway} {@code SecurityConfig} chain, and wms's own
 *       {@code IdentityHeaderStripFilter} / {@code JwtHeaderEnrichmentFilter} /
 *       {@code RoleAdmissionFilter} beans wired by {@code GatewayIdentityConfig}.</li>
 *   <li>A {@code redis:7-alpine} Testcontainer backing the {@code RequestRateLimiter} +
 *       wms's {@code FailOpenRateLimiter} / {@code accountKeyResolver}.</li>
 *   <li>A JWKS {@link MockWebServer} publishing the test RSA public key — the reactive
 *       Resource Server fetches it to verify JWT signatures (reuses wms's existing
 *       {@link JwksMockServer} / {@link JwtTestHelper} infra, issuer {@code "iam"},
 *       tenant {@code "wms"}).</li>
 *   <li>A downstream {@link MockWebServer} standing in for master-service. The gateway's
 *       {@code /api/v1/master/**} route is re-pointed at it so tests can observe exactly what
 *       crossed the edge (path preserved — wms has no RewritePath — and enriched identity
 *       headers).</li>
 * </ul>
 *
 * <h2>Parity note — how wms differs from iam / fan (AC-5)</h2>
 * These tests assert only what wms actually wires, and wms's edge is deliberately narrower
 * than its siblings:
 * <ul>
 *   <li><b>No RewritePath.</b> fan rewrites {@code /api/v1/<x>} → {@code /api/<x>} and its
 *       IT asserts the rewritten downstream path; wms forwards the path unchanged, so the
 *       downstream assertion here is path-<em>preservation</em>, not rewrite.</li>
 *   <li><b>Strict tenant gate, no {@code "*"} wildcard.</b> fan's IT has a
 *       {@code SUPER_ADMIN tenant_id=*} → 200 case; wms alone rejects that wildcard
 *       (ADR-MONO-048 § D5), so there is no wildcard-pass test here — a cross-tenant token
 *       is 403 {@code TENANT_FORBIDDEN}, full stop.</li>
 *   <li><b>Smallest injected-header set.</b> wms injects only
 *       {@code X-User-Id}/{@code X-Actor-Id}/{@code X-User-Email}/{@code X-User-Role}
 *       (ADR-MONO-035 4b-2a) and deliberately does <em>not</em> re-inject {@code X-Tenant-Id}
 *       or {@code X-Account-Type} even though it strips them — the enrichment IT asserts that
 *       absence, which iam/fan do not.</li>
 *   <li><b>rule-6 admission.</b> Like fan (post TASK-MONO-416) but unlike iam's monolith,
 *       wms carries a {@code RoleAdmissionFilter}; a role-less/scope-less token is 403
 *       {@code FORBIDDEN} (admission), distinct from 403 {@code TENANT_FORBIDDEN} (tenant).</li>
 * </ul>
 *
 * <p>Tests subclass this and drive HTTP through {@link WebTestClient} bound to the random
 * gateway port. {@code @Testcontainers(disabledWithoutDocker = true)} makes the whole suite
 * clean-SKIP (not ERROR) where no Docker daemon is present — matching wms's e2e {@code E2EBase}
 * and fan's integration base. CI Linux is the authoritative runner; local Windows
 * Testcontainers is flaky.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
public abstract class GatewayIntegrationBase {

    // Testcontainers singleton pattern: infra is started in a static initializer, NOT in
    // @BeforeAll. Under JUnit's PER_CLASS lifecycle, Spring injects @Autowired fields — and
    // therefore refreshes the ApplicationContext, which reads the JWKS URL below — *before*
    // @BeforeAll runs, so a @BeforeAll that started jwks would leave the decoder bean reading
    // a null. The static initializer runs on first active class use, which is after the
    // @Testcontainers(disabledWithoutDocker=true) condition has already gated on Docker, so it
    // never fires on a Docker-free host (the class is simply skipped). Ryuk reaps the
    // container at JVM exit.
    protected static final RedisContainer REDIS = new RedisContainer(
            DockerImageName.parse("redis:7-alpine"));

    protected static final JwtTestHelper jwt;
    protected static final JwksMockServer jwks;
    protected static final MockWebServer downstream;

    static {
        REDIS.start();
        jwt = new JwtTestHelper();
        try {
            jwks = new JwksMockServer(jwt);
            downstream = new MockWebServer();
            downstream.start();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Autowired
    protected WebTestClient webTestClient;

    /**
     * The downstream {@link MockWebServer} is a process-wide singleton (it must be static so
     * {@code @DynamicPropertySource} can point the route at it), so its enqueued-response and
     * recorded-request queues are shared by every test in every subclass. Without a reset, the
     * rate-limit test's ~54 unconsumed 200-responses would be dequeued FIFO by the next class's
     * requests (a routing assertion would then see the wrong body). Reset to a clean slate
     * before each test: a fresh response queue, and drain any unconsumed recorded requests so a
     * {@code takeRequest()} assertion never observes a previous test's call.
     */
    @BeforeEach
    void resetDownstream() {
        downstream.setDispatcher(new QueueDispatcher());
        RecordedRequest drained;
        do {
            try {
                drained = downstream.takeRequest(0, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        } while (drained != null);
    }

    @DynamicPropertySource
    static void wireProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        // Verify signatures against the in-JVM JWKS MockWebServer instead of GAP. The decoder
        // bean reads this key via @Value at construction (OAuth2ResourceServerConfig), so the
        // dynamic value is what the real chain uses.
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> jwks.hostJwksUrl());
        // Issuer allowlist + tenant gate — the values wms's tenantGate() really binds. The
        // JwtTestHelper signs with issuer "iam" and tenant_id "wms".
        registry.add("wms.oauth2.allowed-issuers",
                () -> JwtTestHelper.SAS_ISSUER + "," + JwtTestHelper.LEGACY_ISSUER);
        registry.add("wms.oauth2.required-tenant-id", () -> JwtTestHelper.DEFAULT_TENANT_ID);

        // Re-point the master-service route (routes[0] in application.yml) at the downstream
        // MockWebServer. wms applies NO RewritePath, so the path is forwarded verbatim. The
        // RequestRateLimiter is re-declared with a low burst (1/s replenish, 5 burst) so the
        // rate-limit IT can trip it deterministically; functional ITs use distinct JWT
        // subjects (distinct account buckets) and few requests, so they never trip it.
        registry.add("spring.cloud.gateway.routes[0].id", () -> "master-service");
        registry.add("spring.cloud.gateway.routes[0].uri",
                () -> "http://" + downstream.getHostName() + ":" + downstream.getPort());
        registry.add("spring.cloud.gateway.routes[0].predicates[0]",
                () -> "Path=/api/v1/master/**");
        registry.add("spring.cloud.gateway.routes[0].filters[0].name",
                () -> "RequestRateLimiter");
        registry.add("spring.cloud.gateway.routes[0].filters[0].args.redis-rate-limiter.replenishRate",
                () -> "1");
        registry.add("spring.cloud.gateway.routes[0].filters[0].args.redis-rate-limiter.burstCapacity",
                () -> "5");
        registry.add("spring.cloud.gateway.routes[0].filters[0].args.redis-rate-limiter.requestedTokens",
                () -> "1");
        registry.add("spring.cloud.gateway.routes[0].filters[0].args.key-resolver",
                () -> "#{@accountKeyResolver}");
    }
}
