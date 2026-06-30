package com.example.gateway.integration;

import com.example.gateway.testsupport.JwksMockServer;
import com.example.gateway.testsupport.JwtTestHelper;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
 * <p>{@code @Tag("integration")}: excluded from the Docker-free {@code test} run; CI
 * executes it against a real Redis (Testcontainers).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
@Testcontainers
@ActiveProfiles("integration-test")
@DisplayName("Cross-tenant rate-limit 격리 통합 테스트 (M7)")
class CrossTenantRateLimitIsolationIntegrationTest {

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

    private String tokenForTenant(String tenantId) {
        // Carries roles=[CUSTOMER] so the token passes AccountTypeEnforcementFilter's
        // role-based admission (ADR-MONO-035 4b-2a) and actually reaches the rate limiter;
        // a bare signTokenWithIssuerAndTenant token (no roles) 403s at admission before the
        // limiter runs, masking the 429. The distinct tenant_id keys the per-tenant bucket.
        return jwtHelper.signCustomerTokenForTenant(tenantId);
    }

    @Test
    @DisplayName("AC-1: tenant A 의 burst(429)가 tenant B 의 버킷을 소모하지 않는다")
    void tenantABurst_doesNotConsumeTenantBBucket() {
        // Clean slate.
        redisTemplate.keys("rate:ecommerce-gw:*")
                .flatMap(redisTemplate::delete)
                .collectList()
                .block();

        String tokenA = tokenForTenant("tenant-a");
        String tokenB = tokenForTenant("tenant-b");

        // tenant A — first request drains the burst=1 bucket (not 429).
        webTestClient.get().uri("/api/products/42")
                .header("Authorization", "Bearer " + tokenA)
                .exchange()
                .expectStatus().value(status ->
                        org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(429));

        // tenant A — second request immediately → 429.
        webTestClient.get().uri("/api/products/42")
                .header("Authorization", "Bearer " + tokenA)
                .exchange()
                .expectStatus().isEqualTo(429);

        // tenant B — first request on the SAME route from the SAME client → still allowed
        // (independent (tenant_id, route_id) bucket). This is the isolation assertion.
        webTestClient.get().uri("/api/products/42")
                .header("Authorization", "Bearer " + tokenB)
                .exchange()
                .expectStatus().value(status ->
                        org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(429));
    }
}
