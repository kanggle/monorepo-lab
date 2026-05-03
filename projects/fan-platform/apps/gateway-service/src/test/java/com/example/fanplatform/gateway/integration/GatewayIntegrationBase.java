package com.example.fanplatform.gateway.integration;

import com.example.fanplatform.gateway.testsupport.JwksMockServer;
import com.example.fanplatform.gateway.testsupport.JwtTestHelper;
import com.redis.testcontainers.RedisContainer;
import java.io.IOException;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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

    @BeforeAll
    static void startSharedInfra() throws IOException {
        REDIS.start();
        jwt = new JwtTestHelper();
        jwks = new JwksMockServer(jwt);
        downstream = new MockWebServer();
        downstream.start();
    }

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
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> jwks.hostJwksUrl());
        registry.add("fanplatform.oauth2.allowed-issuers",
                () -> JwtTestHelper.SAS_ISSUER + "," + JwtTestHelper.LEGACY_ISSUER);
        registry.add("fanplatform.oauth2.required-tenant-id", () -> "fan-platform");
        // Override the placeholder route so /api/v1/community/** lands on the
        // downstream MockWebServer instead of the unreachable
        // http://community-service:8080. spring.cloud.gateway.routes is a list,
        // and Spring's relaxed binding accepts indexed property keys.
        registry.add("spring.cloud.gateway.routes[0].id", () -> "community-service");
        registry.add("spring.cloud.gateway.routes[0].uri",
                () -> "http://" + downstream.getHostName() + ":" + downstream.getPort());
        registry.add("spring.cloud.gateway.routes[0].predicates[0]",
                () -> "Path=/api/v1/community/**");
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
