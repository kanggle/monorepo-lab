package com.example.admin.integration;

import com.example.admin.support.OperatorJwtTestFixture;
import com.example.testsupport.integration.AbstractIntegrationTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * TASK-MONO-773 S3 (OD-3 — onboarding-api.md § 신원당 상한 «경합») — closes TASK-MONO-784 S5's recorded cap+1 race:
 * two concurrent self-service creations by an operator that owns {@code cap − 1} tenants. The first-pass check runs
 * BEFORE the tenant is created (a remote call), so both requests pass it; WireMock delays the tenant create so the
 * two checks deterministically overlap. Before S3 both then write → {@code cap + 1} owned tenants. With the operator
 * row locked ({@code SELECT … FOR UPDATE}) and re-counted inside the membership transaction, exactly one is
 * {@code 201} and the other {@code 422 OPERATOR_OWNED_TENANT_LIMIT_EXCEEDED} with its just-created tenant compensated
 * by SUSPEND (ADR-044 D3).
 *
 * <p>A separate context from {@link SelfServiceOnboardingTrustGateIntegrationTest} only because it pins the cap
 * explicitly to the S3 value (3) — that class reads the default. Skipped without Docker — CI Linux is authoritative.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
@org.springframework.test.annotation.DirtiesContext(
        classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class SelfServiceOnboardingCapRaceIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static final String OIDC_ISSUER = "http://localhost:8081";
    private static final String CONSOLE_CLIENT = "platform-console-web";
    private static final String JWKS_KID = "auth-test-kid-773race";
    /** Long enough that both requests pass the pre-creation check before either one writes. */
    private static final int TENANT_CREATE_DELAY_MS = 1500;

    static WireMockServer wireMock;
    static String adminSigningKeyPem;
    static KeyPair authKeyPair;

    @BeforeAll
    static void setupShared() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        authKeyPair = gen.generateKeyPair();
        OperatorJwtTestFixture jwt = new OperatorJwtTestFixture();
        java.security.PrivateKey adminPk;
        try {
            var f = OperatorJwtTestFixture.class.getDeclaredField("keyPair");
            f.setAccessible(true);
            adminPk = ((KeyPair) f.get(jwt)).getPrivate();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        adminSigningKeyPem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(adminPk.getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void tearDownShared() {
        if (wireMock != null) wireMock.stop();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("admin.jwt.active-signing-kid", () -> "test-key-001");
        registry.add("admin.jwt.signing-keys.test-key-001", () -> adminSigningKeyPem);
        registry.add("admin.jwt.issuer", () -> "admin-service");
        registry.add("admin.jwt.expected-token-type", () -> "admin");
        registry.add("admin.oidc.jwks-uri", () -> wireMock.baseUrl() + "/internal/auth/jwks");
        registry.add("admin.oidc.issuer", () -> OIDC_ISSUER);
        registry.add("admin.oidc.audience", () -> CONSOLE_CLIENT);
        registry.add("admin.account-service.base-url", wireMock::baseUrl);
        registry.add("admin.auth-service.base-url", wireMock::baseUrl);
        registry.add("admin.security-service.base-url", wireMock::baseUrl);
        registry.add("iam.internal-client.token-uri", () -> wireMock.baseUrl() + "/oauth2/token");
        registry.add("admin.onboarding.max-owned-tenants-per-operator", () -> "3");
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void stubs() {
        wireMock.resetAll();
        wireMock.stubFor(WireMock.post(urlEqualTo("/oauth2/token"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"test-jwt\",\"expires_in\":300,\"token_type\":\"Bearer\"}")));
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/auth/jwks"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(jwksJson((RSAPublicKey) authKeyPair.getPublic()))));
        wireMock.stubFor(WireMock.post(urlEqualTo("/internal/tenants"))
                .willReturn(aResponse().withStatus(201).withFixedDelay(TENANT_CREATE_DELAY_MS)
                        .withHeader("Content-Type", "application/json")
                        .withBody(tenantJson("s3-race", "ACTIVE"))));
        wireMock.stubFor(WireMock.patch(urlPathMatching("/internal/tenants/[^/]+"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(tenantJson("s3-race", "SUSPENDED"))));
    }

    @Test
    @DisplayName("🔴 OD-3 경합: 상한−1(소유 2)인 행으로 동시 두 생성 → 정확히 하나 201 · 하나 422 · TENANT_ADMIN 테넌트 수 = 상한 · 진 쪽 테넌트는 SUSPEND 보상 · 진 쪽 새 테넌트에 행 0")
    void twoConcurrentCreatesAtCapMinusOne_exactlyOneWins() throws Exception {
        String sfx = UUID.randomUUID().toString().substring(0, 8);
        String account = "acc-race-" + sfx;
        String email = "race-" + sfx + "@example.com";
        long id = seedFacet(account, email, "s3-rh-" + sfx);
        ownTenant(id, "s3-ro-" + sfx);
        stubVerified(account, email);
        assertThat(ownedTenants(id)).isEqualTo(2);

        List<String> slugs = List.of("s3-ra-" + sfx, "s3-rb-" + sfx);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Map<String, Integer> statusBySlug = new LinkedHashMap<>();
        try {
            List<Future<MvcResult>> results = new ArrayList<>();
            for (String slug : slugs) {
                results.add(pool.submit(() -> {
                    go.await();
                    return mockMvc.perform(post("/api/admin/onboarding/organizations")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"subjectToken\":\"" + userOidcToken(account) + "\",\"tenantId\":\""
                                            + slug + "\",\"organizationName\":\"S3 Race\"}"))
                            .andReturn();
                }));
            }
            go.countDown();
            for (int i = 0; i < slugs.size(); i++) {
                statusBySlug.put(slugs.get(i), results.get(i).get(60, TimeUnit.SECONDS).getResponse().getStatus());
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(statusBySlug.values()).as("exactly one wins the race").containsExactlyInAnyOrder(201, 422);
        assertThat(ownedTenants(id)).as("the cap holds under concurrency").isEqualTo(3);
        assertThat(wireMock.findAll(com.github.tomakehurst.wiremock.client.WireMock
                .postRequestedFor(urlEqualTo("/internal/tenants")))).as("both passed the first check").hasSize(2);

        String loser = statusBySlug.entrySet().stream().filter(e -> e.getValue() == 422)
                .map(Map.Entry::getKey).findFirst().orElseThrow();
        assertThat(wireMock.findAll(patchRequestedFor(urlPathEqualTo("/internal/tenants/" + loser))
                .withRequestBody(matchingJsonPath("$.status", equalTo("SUSPENDED")))))
                .as("the loser's just-created tenant is compensated by SUSPEND").hasSize(1);
        assertThat(count("SELECT COUNT(*) FROM admin_operator_roles WHERE operator_id = ? AND tenant_id = ?", id, loser))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM operator_tenant_assignment WHERE operator_id = ? AND tenant_id = ?",
                id, loser)).isZero();
        assertThat(count("SELECT COUNT(*) FROM operator_domain_set_grant WHERE operator_id = ? AND tenant_id = ?",
                id, loser)).isZero();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static String jwksJson(RSAPublicKey key) {
        return "{\"keys\":[{\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"RS256\",\"kid\":\"" + JWKS_KID
                + "\",\"n\":\"" + b64Url(key.getModulus().toByteArray()) + "\",\"e\":\""
                + b64Url(key.getPublicExponent().toByteArray()) + "\"}]}";
    }

    private static String b64Url(byte[] bytes) {
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] s = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, s, 0, s.length);
            bytes = s;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String userOidcToken(String sub) {
        Instant now = Instant.now();
        return Jwts.builder().header().keyId(JWKS_KID).and()
                .subject(sub).issuer(OIDC_ISSUER).audience().add(CONSOLE_CLIENT).and()
                .issuedAt(Date.from(now)).expiration(Date.from(now.plus(15, ChronoUnit.MINUTES)))
                .signWith(authKeyPair.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private static String tenantJson(String tenantId, String status) {
        return """
                {"tenantId":"%s","displayName":"S3 %s","tenantType":"B2B_ENTERPRISE","status":"%s",
                 "createdAt":"2026-10-15T00:00:00Z","updatedAt":"2026-10-15T00:00:00Z"}
                """.formatted(tenantId, tenantId, status);
    }

    private void stubVerified(String accountId, String email) {
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/accounts/" + accountId))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"" + accountId + "\",\"email\":\"" + email
                                + "\",\"status\":\"ACTIVE\",\"createdAt\":\"2026-10-15T00:00:00Z\","
                                + "\"profile\":{\"displayName\":\"S3 Race\",\"phoneMasked\":null}}")));
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/internal/accounts/" + accountId + "/verified-email:match"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"accountId\":\"" + accountId + "\",\"emailVerifiedAt\":\"2026-10-15T00:00:00Z\"}")));
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private int ownedTenants(long id) {
        return count("""
                SELECT COUNT(DISTINCT x.tenant_id) FROM admin_operator_roles x JOIN admin_roles r ON r.id = x.role_id
                 WHERE x.operator_id = ? AND r.name = 'TENANT_ADMIN' AND x.tenant_id <> '*'""", id);
    }

    private long seedFacet(String accountId, String email, String home) {
        jdbc.update("""
                INSERT INTO admin_operators
                  (operator_id, tenant_id, email, password_hash, display_name,
                   status, oidc_subject, created_at, updated_at, version)
                VALUES (?, ?, ?, NULL, 'S3 race facet', 'ACTIVE', ?, NOW(6), NOW(6), 0)
                """, UUID.randomUUID().toString(), home, email, accountId);
        Long id = jdbc.queryForObject("SELECT id FROM admin_operators WHERE oidc_subject = ?", Long.class, accountId);
        ownTenant(id, home);
        return id;
    }

    private void ownTenant(long id, String tenantId) {
        jdbc.update("""
                INSERT INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
                SELECT ?, r.id, ?, NOW(6), NULL FROM admin_roles r WHERE r.name = 'TENANT_ADMIN'
                """, id, tenantId);
        jdbc.update("""
                INSERT IGNORE INTO operator_tenant_assignment (operator_id, tenant_id, granted_at, granted_by)
                VALUES (?, ?, NOW(6), NULL)""", id, tenantId);
    }
}
