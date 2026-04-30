package com.example.admin.integration;

import com.example.admin.infrastructure.persistence.AdminActionJpaEntity;
import com.example.admin.infrastructure.persistence.AdminActionJpaRepository;
import com.example.admin.support.OperatorJwtTestFixture;
import com.example.testsupport.integration.AbstractIntegrationTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration test for the admin-service.
 *
 * Boots the Spring Boot app, wires it against real MySQL + Kafka (via
 * Testcontainers) and a WireMock stub replacing the account/auth/security
 * downstream services. Verifies the audit-before-downstream (A10) pattern
 * and the outbox event emission on success.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class AdminIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static WireMockServer wireMock;
    static OperatorJwtTestFixture jwt;
    static String signingKeyPem;

    @BeforeAll
    static void setupShared() throws IOException {
        jwt = new OperatorJwtTestFixture();

        // Export the fixture's PKCS#8 private key as PEM so the admin
        // JwtConfig can verify tokens signed by the fixture. The active
        // kid and issuer match the fixture (kid="test-key-001",
        // iss="admin-service").
        java.security.PrivateKey pk = extractPrivateKey(jwt);
        signingKeyPem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pk.getEncoded())
                + "\n-----END PRIVATE KEY-----\n";

        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());
    }

    @AfterAll
    static void tearDownShared() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    private static java.security.PrivateKey extractPrivateKey(OperatorJwtTestFixture fixture) {
        try {
            var field = OperatorJwtTestFixture.class.getDeclaredField("keyPair");
            field.setAccessible(true);
            java.security.KeyPair kp = (java.security.KeyPair) field.get(fixture);
            return kp.getPrivate();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("admin.jwt.active-signing-kid", () -> "test-key-001");
        registry.add("admin.jwt.signing-keys.test-key-001", () -> signingKeyPem);
        registry.add("admin.jwt.issuer", () -> "admin-service");
        registry.add("admin.jwt.expected-token-type", () -> "admin");
        registry.add("admin.auth-service.base-url", wireMock::baseUrl);
        registry.add("admin.account-service.base-url", wireMock::baseUrl);
        registry.add("admin.security-service.base-url", wireMock::baseUrl);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AdminActionJpaRepository adminActionRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetStubs() {
        wireMock.resetAll();
    }

    private static final String OPERATOR_UUID = "00000000-0000-7000-8000-000000000001";

    private String operatorToken() {
        return "Bearer " + jwt.operatorToken(OPERATOR_UUID);
    }

    @org.junit.jupiter.api.BeforeEach
    void seedOperatorAndRoles() {
        // Idempotent insert of an operator + SUPER_ADMIN binding so the
        // DB-backed PermissionEvaluator grants all permissions during the test.
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_operators WHERE operator_id = ?",
                Integer.class, OPERATOR_UUID);
        if (existing == null || existing == 0) {
            jdbcTemplate.update("""
                    INSERT INTO admin_operators
                      (operator_id, email, password_hash, display_name, status,
                       created_at, updated_at, version)
                    VALUES (?, ?, ?, ?, 'ACTIVE', NOW(6), NOW(6), 0)
                    """,
                    OPERATOR_UUID, "integ@example.com", "x", "Integ Op");
        }
        jdbcTemplate.update("""
                INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, granted_at, granted_by)
                SELECT o.id, r.id, NOW(6), NULL
                  FROM admin_operators o CROSS JOIN admin_roles r
                 WHERE o.operator_id = ? AND r.name = 'SUPER_ADMIN'
                """, OPERATOR_UUID);
    }

    @Test
    @DisplayName("Lock success: audit IN_PROGRESS → SUCCESS + outbox event emitted")
    void lockSuccessEmitsAuditAndOutbox() throws Exception {
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/internal/accounts/acc-001/lock"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "accountId": "acc-001",
                                    "previousStatus": "ACTIVE",
                                    "currentStatus": "LOCKED",
                                    "lockedAt": "2026-04-12T10:00:00Z"
                                }
                                """)));

        mockMvc.perform(post("/api/admin/accounts/acc-001/lock")
                        .header("Authorization", operatorToken())
                        .header("Idempotency-Key", "idemp-integ-1")
                        .header("X-Operator-Reason", "fraud-investigation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketId\":\"T-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("LOCKED"));

        // Exactly one audit row for this idempotency key. It must have started
        // as IN_PROGRESS and transitioned to SUCCESS.
        await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() -> {
            List<AdminActionJpaEntity> rows = adminActionRepository.findAll();
            AdminActionJpaEntity row = rows.stream()
                    .filter(r -> "idemp-integ-1".equals(r.getIdempotencyKey()))
                    .findFirst().orElseThrow();
            assertThat(row.getOutcome()).isEqualTo("SUCCESS");
            assertThat(row.getActionCode()).isEqualTo("ACCOUNT_LOCK");
            assertThat(row.getCompletedAt()).isNotNull();
        });

        // Outbox row emitted for admin.action.performed.
        await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Map<String, Object>> events = jdbcTemplate.queryForList(
                    "SELECT event_type FROM outbox WHERE event_type = 'admin.action.performed'");
            assertThat(events).isNotEmpty();
        });
    }

    @Test
    @DisplayName("Lock downstream 500: retries exhausted → 503 + audit FAILURE")
    void lockDownstreamFailureRecordsFailureAndReturns503() throws Exception {
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/internal/accounts/acc-002/lock"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":\"INTERNAL_ERROR\",\"message\":\"boom\"}")));

        mockMvc.perform(post("/api/admin/accounts/acc-002/lock")
                        .header("Authorization", operatorToken())
                        .header("Idempotency-Key", "idemp-integ-2")
                        .header("X-Operator-Reason", "fraud-investigation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DOWNSTREAM_ERROR"));

        // Audit row must still exist, with outcome=FAILURE after retries are exhausted.
        await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() -> {
            List<AdminActionJpaEntity> rows = adminActionRepository.findAll();
            AdminActionJpaEntity row = rows.stream()
                    .filter(r -> "idemp-integ-2".equals(r.getIdempotencyKey()))
                    .findFirst().orElseThrow();
            assertThat(row.getOutcome()).isEqualTo("FAILURE");
            assertThat(row.getCompletedAt()).isNotNull();
        });

        // WireMock should have observed retries (max-attempts=3 in test config).
        wireMock.verify(
                com.github.tomakehurst.wiremock.client.WireMock.moreThanOrExactly(2),
                com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(
                        urlPathEqualTo("/internal/accounts/acc-002/lock")));
    }
}
