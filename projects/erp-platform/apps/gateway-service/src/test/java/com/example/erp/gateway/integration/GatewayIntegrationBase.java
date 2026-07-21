package com.example.erp.gateway.integration;

import com.example.erp.gateway.testsupport.JwksMockServer;
import com.example.erp.gateway.testsupport.JwtTestHelper;
import com.redis.testcontainers.RedisContainer;
import java.io.IOException;
import java.io.UncheckedIOException;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
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
 * Shared infrastructure for erp gateway {@code @Tag("integration")} tests (TASK-MONO-458).
 *
 * <p>Every subclass drives an HTTP request through the <em>real</em> Spring Cloud Gateway route
 * and the full library filter chain — {@code SecurityConfig}'s resource-server, the tenant gate,
 * {@code RoleAdmissionFilter}, the identity strip/enrich pair and the fail-open rate limiter —
 * via {@link WebTestClient} bound to the random gateway port. This is the coverage erp's unit
 * tests cannot give: they construct the library filters directly and never route a request
 * (AC-2 no-bypass).
 *
 * <h2>How erp's edge coverage differs from iam's — and why (AC-5)</h2>
 *
 * <ul>
 *   <li><strong>Filter provenance.</strong> iam's gateway owns its filters in-service and its
 *       IT ({@code GatewayIntegrationText}) exercises hand-written code. erp composes the shared
 *       {@code libs/java-gateway} filters ({@code SecurityConfig}, {@code RoleAdmissionFilter},
 *       {@code IdentityHeaderStripFilter}, {@code JwtHeaderEnrichmentFilter}) plus the
 *       {@code java-security} {@code TenantClaimValidator}; this suite pins the <em>wiring</em>
 *       erp actually declares, so a mis-wired edge — not a filter bug — turns it red.</li>
 *   <li><strong>Routing shape.</strong> iam and scm rewrite an external {@code /api/v1/} prefix
 *       to an internal path. erp deliberately does <em>not</em> ({@code application.yml} § Routes:
 *       Traefik already routed {@code /api/erp/**} 1:1). So erp asserts path <em>preservation</em>
 *       through the route, where iam/scm assert path rewrite.</li>
 *   <li><strong>Tenant vocabulary.</strong> erp requires {@code tenant_id=erp} (plus the
 *       {@code "*"} SUPER_ADMIN wildcard and entitlement dual-accept), against iam's issuer.</li>
 *   <li><strong>Role admission.</strong> erp wires rule-6 admission (role OR scope); iam's
 *       gateway predates that leg. erp therefore asserts both the admitted and the 403 legs.</li>
 * </ul>
 *
 * <p>Docker-gated ({@link Testcontainers}{@code (disabledWithoutDocker = true)}): a host without a
 * Docker daemon cleanly SKIPs rather than failing, matching scm/fan/wms.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
public abstract class GatewayIntegrationBase {

    // Singleton shared infrastructure (Testcontainers "singleton containers" idiom), started once
    // in a static initializer and NEVER torn down per class — the JVM shutdown / Ryuk reaps the
    // container, and the MockWebServers die with the JVM.
    //
    // Why not @BeforeAll/@AfterAll: the two IT classes share this base's single inherited
    // @DynamicPropertySource method, so Spring gives them ONE cached ApplicationContext. A
    // per-class @AfterAll that shut the servers down (and a per-class @BeforeAll that started new
    // ones on new ports) left the reused context pointing at a dead port — every routed request
    // then 500'd with "Connection refused". Starting once and leaving it up removes that race, and
    // the static block runs at class-init (after JUnit's disabledWithoutDocker gate has already
    // skipped the class when Docker is absent), so the clean-SKIP is preserved.

    protected static final RedisContainer REDIS = new RedisContainer(
            DockerImageName.parse("redis:7-alpine"));

    protected static final JwtTestHelper jwt = new JwtTestHelper();
    protected static final JwksMockServer jwks;
    protected static final MockWebServer downstream;

    /** Header the downstream stub echoes the received request path back in (path-preservation assert). */
    protected static final String RECEIVED_PATH_HEADER = "X-Downstream-Received-Path";

    static {
        try {
            REDIS.start();
            jwks = new JwksMockServer(jwt);
            downstream = new MockWebServer();
            // A STATELESS dispatcher, not enqueue(): the downstream is a JVM-wide singleton shared
            // by every IT, so a per-test enqueue() FIFO would let one test's leftover responses be
            // consumed by another (the rate-limit burst leaves ~50 unconsumed). The dispatcher
            // returns a deterministic 200 for any routed request and echoes the received path in a
            // response header, so each test asserts on its OWN response — never a shared queue.
            downstream.setDispatcher(new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    String path = request.getPath() == null ? "" : request.getPath();
                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setHeader(RECEIVED_PATH_HEADER, path)
                            .setBody("{\"employees\":[],\"ok\":true}");
                }
            });
            downstream.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to start gateway IT infrastructure", e);
        }
    }

    @LocalServerPort
    protected int gatewayPort;

    @Autowired
    protected WebTestClient webTestClient;

    @DynamicPropertySource
    static void wireProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> jwks.hostJwksUrl());
        registry.add("erpplatform.oauth2.allowed-issuers",
                () -> JwtTestHelper.SAS_ISSUER + "," + JwtTestHelper.LEGACY_ISSUER);
        registry.add("erpplatform.oauth2.required-tenant-id", () -> "erp");
        // The JWKS startup probe would otherwise fail the context if it fired before the
        // MockWebServer is reachable; the jwk-set-uri override above already points it at the
        // live mock, but disabling it keeps the boot deterministic under Testcontainers.
        registry.add("gateway.jwks.startup-probe.enabled", () -> "false");

        // Point erp's route[0] (masterdata-service) at the downstream MockWebServer. erp does
        // NOT rewrite the path (application.yml § Routes: the 1:1 forward Traefik was doing), so
        // there is intentionally no RewritePath filter here — the downstream must receive the
        // original /api/erp/masterdata/** path unchanged. A small burstCapacity makes the
        // rate-limit test deterministic.
        registry.add("spring.cloud.gateway.routes[0].id", () -> "masterdata-service");
        registry.add("spring.cloud.gateway.routes[0].uri",
                () -> "http://" + downstream.getHostName() + ":" + downstream.getPort());
        registry.add("spring.cloud.gateway.routes[0].predicates[0]",
                () -> "Path=/api/erp/masterdata/**");
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
