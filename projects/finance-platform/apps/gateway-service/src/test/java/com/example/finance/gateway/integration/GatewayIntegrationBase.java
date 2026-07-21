package com.example.finance.gateway.integration;

import com.example.finance.gateway.testsupport.JwksMockServer;
import com.example.finance.gateway.testsupport.JwtTestHelper;
import com.redis.testcontainers.RedisContainer;
import java.io.IOException;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
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
 * Shared full-context infrastructure for finance's {@code @Tag("integration")} suite — the
 * first integration tests this gateway has ever had (TASK-MONO-458). Every subclass drives HTTP
 * traffic through the <em>real</em> Spring Cloud Gateway route + filter chain via
 * {@link WebTestClient} bound to a random port; nothing here instantiates a filter or reads a
 * route config in isolation (those live in the Docker-free unit tests) — that is AC-2.
 *
 * <p>Stands up three collaborators:
 * <ul>
 *   <li>Redis 7 Testcontainer — the backend for the {@code RequestRateLimiter} finance wires on
 *       both routes.</li>
 *   <li>A JWKS {@link JwksMockServer} publishing the test public key; finance's reactive resource
 *       server fetches it to verify RS256 signatures.</li>
 *   <li>A downstream {@link MockWebServer} standing in for {@code account-service}. finance
 *       forwards {@code /api/finance/accounts/**} to it <strong>1:1</strong> — finance
 *       deliberately has no {@code RewritePath} (TASK-MONO-357 § Routes), so the downstream sees
 *       the same path the client sent. This is the sharpest divergence from scm/iam, whose
 *       gateways strip a {@code /api/v1/} external prefix.</li>
 * </ul>
 *
 * <p>The JWKS startup probe is switched off for the suite: it is an orthogonal boot-time concern
 * with its own coverage, and leaving it on would couple every request-path assertion to a
 * network fetch that races context refresh. The request-path chain under test is unaffected.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
public abstract class GatewayIntegrationBase {

    // All infra is started in a static initializer, NOT @BeforeAll, and this is load-bearing.
    // With @TestInstance(PER_CLASS) the SpringExtension injects the test instance — and therefore
    // refreshes the ApplicationContext — BEFORE @BeforeAll methods run. The @DynamicPropertySource
    // suppliers below are evaluated during that refresh (the jwk-set-uri @Value, the Redis host),
    // so the collaborators they read must already exist. A @BeforeAll would run too late and the
    // context would see a null jwks / an unstarted container. Static init runs on first active use
    // of the class, which under @Testcontainers(disabledWithoutDocker=true) only happens when the
    // class is NOT disabled — so Docker-absent still yields a clean SKIP rather than a start attempt.
    protected static final RedisContainer REDIS;
    protected static final JwtTestHelper jwt;
    protected static final JwksMockServer jwks;
    protected static final MockWebServer downstream;

    static {
        try {
            REDIS = new RedisContainer(DockerImageName.parse("redis:7-alpine"));
            REDIS.start();
            jwt = new JwtTestHelper();
            jwks = new JwksMockServer(jwt);
            downstream = new MockWebServer();
            downstream.start();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @LocalServerPort
    protected int gatewayPort;

    @Autowired
    protected WebTestClient webTestClient;

    @AfterAll
    static void stopSharedInfra() throws IOException {
        if (downstream != null) downstream.shutdown();
        if (jwks != null) jwks.close();
        if (REDIS != null) REDIS.stop();
    }

    @DynamicPropertySource
    static void wireProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        // Point the resource server at the mock JWKS so signatures verify against the test key.
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> jwks.hostJwksUrl());
        // finance's own property prefix (financeplatform.*), verbatim from application.yml.
        registry.add("financeplatform.oauth2.allowed-issuers",
                () -> JwtTestHelper.SAS_ISSUER + "," + JwtTestHelper.LEGACY_ISSUER);
        registry.add("financeplatform.oauth2.required-tenant-id", () -> "finance");

        // Orthogonal boot-time concern (see class Javadoc): keep it out of the request-path suite.
        registry.add("gateway.jwks.startup-probe.enabled", () -> "false");

        // Override the placeholder account-service route so /api/finance/accounts/** lands on the
        // downstream MockWebServer instead of the unreachable http://account-service:8080. finance
        // has NO RewritePath — the route forwards 1:1, so the downstream receives the sent path.
        registry.add("spring.cloud.gateway.routes[0].id", () -> "account-service");
        registry.add("spring.cloud.gateway.routes[0].uri",
                () -> "http://" + downstream.getHostName() + ":" + downstream.getPort());
        registry.add("spring.cloud.gateway.routes[0].predicates[0]",
                () -> "Path=/api/finance/accounts/**");
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
