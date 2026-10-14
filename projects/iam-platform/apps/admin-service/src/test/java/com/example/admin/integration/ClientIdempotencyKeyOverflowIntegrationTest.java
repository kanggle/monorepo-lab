package com.example.admin.integration;

import com.example.admin.application.AccountAdminUseCase;
import com.example.admin.application.BulkLockAccountCommand;
import com.example.admin.application.BulkLockAccountResult;
import com.example.admin.application.BulkLockAccountUseCase;
import com.example.admin.application.LockAccountCommand;
import com.example.admin.application.OperatorContext;
import com.example.admin.application.exception.AuditFailureException;
import com.example.admin.presentation.ClientIdempotencyKey;
import com.example.admin.support.OperatorJwtTestFixture;
import com.example.testsupport.integration.AbstractIntegrationTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.security.KeyPair;
import java.util.Base64;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-635 — the client-sent {@code Idempotency-Key} header reaches {@code admin_actions.idempotency_key
 * VARCHAR(100)} unchecked. Real MySQL (Testcontainers), real {@code AdminActionAuditWriter}.
 *
 * <ul>
 *   <li><b>AC-0a</b> {@link #useCaseLevel_lockWithOverLengthKey_throwsAuditFailure()} — calls
 *       {@link AccountAdminUseCase#lock} directly (bypassing the controller's new {@code @Size} bound — the
 *       shape of Failure Scenario 2, "an internal path that calls the use case directly with its own key") with a
 *       101-character key → {@link AuditFailureException}. This is the mechanism the HTTP-level 500
 *       {@code AUDIT_FAILURE} came from, and it survives this ticket's fix (the fix is a controller-boundary
 *       guard, not a use-case one) — a permanent regression guard for any future caller that skips the
 *       boundary.</li>
 *   <li><b>AC-0b</b> {@link #useCaseLevel_bulkLockPerRowKeyAt101_rowOutcomeIsAuditFailure()} — the exact
 *       pre-634-fix shape: a 64-character {@code BulkLockAccountCommand#idempotencyKey} + a 36-character
 *       accountId ⇒ {@code header + ":" + accountId} = 101 ⇒ that row's outcome {@code FAILURE} /
 *       {@code AUDIT_FAILURE} ({@code BulkLockAccountUseCase#processOne}'s own catch clause, not a thrown
 *       exception — the batch itself does not abort).</li>
 *   <li><b>AC-2a</b> {@link #httpLevel_lockWithOverLengthKey_is400NotAuditFailure()} — the same 101-character
 *       key through {@code POST .../lock} now answers {@code 400 VALIDATION_ERROR} before the use case (and
 *       therefore the audit INSERT) is ever reached.</li>
 *   <li><b>AC-2b</b> {@link #httpLevel_bulkLockHeaderAt64Chars_is400ContractChange()} — bulk-lock's header bound
 *       moved 64 → 63 (contract change); a 64-character header that used to be accepted (and then overflow at
 *       the per-row concatenation) is now {@code 400 VALIDATION_ERROR} at the boundary.</li>
 *   <li><b>AC-2c</b> {@link #httpLevel_bulkLockAtTheNewBoundary_rowCompletes()} — 63-character header + a
 *       36-character accountId (the widest combination the new bounds allow) reaches account-service and
 *       completes {@code LOCKED} — the row is no longer {@code AUDIT_FAILURE}.</li>
 * </ul>
 *
 * <p>Skipped when Docker is unavailable (AbstractIntegrationTest) — CI Linux is authoritative.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
@org.springframework.test.annotation.DirtiesContext(
        classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class ClientIdempotencyKeyOverflowIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static WireMockServer wireMock;
    static OperatorJwtTestFixture jwt;
    static String adminSigningKeyPem;

    private static final String OPERATOR_UUID = "00000000-0000-7000-8000-00000063501";
    private static final String TARGET_ACCOUNT = "00000000-0000-7000-8000-000000635a1";
    // Exactly 36 characters, like a real accounts.id UUID (ClientIdempotencyKey.BULK_LOCK_ACCOUNT_ID_MAX).
    private static final String ACCOUNT_ID_36 = "00000000-0000-7000-8000-00000000a001";

    @BeforeAll
    static void setupShared() {
        jwt = new OperatorJwtTestFixture();
        java.security.PrivateKey pk = extractPrivateKey(jwt);
        adminSigningKeyPem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pk.getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());
    }

    @AfterAll
    static void tearDownShared() {
        if (wireMock != null) wireMock.stop();
    }

    private static java.security.PrivateKey extractPrivateKey(OperatorJwtTestFixture fixture) {
        try {
            var f = OperatorJwtTestFixture.class.getDeclaredField("keyPair");
            f.setAccessible(true);
            return ((KeyPair) f.get(fixture)).getPrivate();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("admin.jwt.active-signing-kid", () -> "test-key-001");
        registry.add("admin.jwt.signing-keys.test-key-001", () -> adminSigningKeyPem);
        registry.add("admin.jwt.issuer", () -> "admin-service");
        registry.add("admin.jwt.expected-token-type", () -> "admin");
        registry.add("admin.account-service.base-url", wireMock::baseUrl);
        registry.add("admin.auth-service.base-url", wireMock::baseUrl);
        registry.add("admin.security-service.base-url", wireMock::baseUrl);
        registry.add("iam.internal-client.token-uri", () -> wireMock.baseUrl() + "/oauth2/token");
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired AccountAdminUseCase accountAdminUseCase;
    @Autowired BulkLockAccountUseCase bulkLockAccountUseCase;

    @BeforeEach
    void seed() {
        wireMock.resetAll();
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/oauth2/token"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"test-jwt\",\"expires_in\":300,\"token_type\":\"Bearer\"}")));

        seedOperator(OPERATOR_UUID, "*", "SUPER_ADMIN");
    }

    private OperatorContext operator() {
        return new OperatorContext(OPERATOR_UUID, "jti-635");
    }

    // ---- AC-0: the underlying mechanism, bypassing the controller boundary entirely ----

    @Test
    @DisplayName("AC-0a: AccountAdminUseCase#lock with a 101-char key -> AuditFailureException "
            + "(the mechanism behind the pre-fix 500 AUDIT_FAILURE; survives the controller-boundary fix)")
    void useCaseLevel_lockWithOverLengthKey_throwsAuditFailure() {
        String overLengthKey = "k".repeat(ClientIdempotencyKey.MAX_LENGTH + 1);
        LockAccountCommand cmd = new LockAccountCommand(
                TARGET_ACCOUNT, "635-it reason", null, overLengthKey, operator(), "*");

        assertThatThrownBy(() -> accountAdminUseCase.lock(cmd))
                .isInstanceOf(AuditFailureException.class);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_actions WHERE idempotency_key = ?",
                Integer.class, overLengthKey)).as("the overflowing INSERT never lands a row").isZero();
    }

    @Test
    @DisplayName("AC-0b: BulkLockAccountUseCase per-row key at 64+1+36=101 -> that row FAILURE/AUDIT_FAILURE "
            + "(the batch itself still answers; this is BulkLockAccountUseCase#processOne's own catch, not a "
            + "thrown exception) — exercised at the use-case layer, matching the task's \"internal path\" caveat")
    void useCaseLevel_bulkLockPerRowKeyAt101_rowOutcomeIsAuditFailure() {
        String header64 = "h".repeat(64); // the pre-635 bulk-lock header bound
        BulkLockAccountCommand cmd = new BulkLockAccountCommand(
                List.of(ACCOUNT_ID_36), "635-it reason, long enough", null, header64, operator(), "*");

        BulkLockAccountResult result = bulkLockAccountUseCase.execute(cmd);

        assertThat(result.results()).hasSize(1);
        BulkLockAccountResult.Item row = result.results().get(0);
        assertThat(row.outcome()).isEqualTo(BulkLockAccountUseCase.OUTCOME_FAILURE);
        assertThat(row.errorCode()).isEqualTo("AUDIT_FAILURE");
    }

    // ---- AC-2: the HTTP-level fix ----

    @Test
    @DisplayName("AC-2a: POST lock with a 101-char Idempotency-Key -> 400 VALIDATION_ERROR, not 500 AUDIT_FAILURE")
    void httpLevel_lockWithOverLengthKey_is400NotAuditFailure() throws Exception {
        mockMvc.perform(post("/api/admin/accounts/" + TARGET_ACCOUNT + "/lock")
                        .header("Authorization", "Bearer " + jwt.operatorToken(OPERATOR_UUID))
                        .header("X-Operator-Reason", "635-it")
                        .header("Idempotency-Key", "k".repeat(101))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_actions WHERE target_id = ? AND action_code = 'ACCOUNT_LOCK'",
                Integer.class, TARGET_ACCOUNT)).as("rejected at the boundary -- no audit row at all").isZero();
    }

    @Test
    @DisplayName("AC-2b: bulk-lock header at 64 chars (the pre-635 bound) -> 400 VALIDATION_ERROR (contract change)")
    void httpLevel_bulkLockHeaderAt64Chars_is400ContractChange() throws Exception {
        mockMvc.perform(post("/api/admin/accounts/bulk-lock")
                        .header("Authorization", "Bearer " + jwt.operatorToken(OPERATOR_UUID))
                        .header("X-Operator-Reason", "635-it")
                        .header("Idempotency-Key", "k".repeat(64))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountIds\":[\"" + ACCOUNT_ID_36 + "\"],\"reason\":\"635-it reason\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("AC-2c: bulk-lock at the new boundary (63-char header + 36-char accountId) -> row LOCKED")
    void httpLevel_bulkLockAtTheNewBoundary_rowCompletes() throws Exception {
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/internal/accounts/" + ACCOUNT_ID_36 + "/lock"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"accountId\":\"" + ACCOUNT_ID_36
                                + "\",\"previousStatus\":\"ACTIVE\",\"currentStatus\":\"LOCKED\"}")));

        mockMvc.perform(post("/api/admin/accounts/bulk-lock")
                        .header("Authorization", "Bearer " + jwt.operatorToken(OPERATOR_UUID))
                        .header("X-Operator-Reason", "635-it")
                        .header("Idempotency-Key", "k".repeat(ClientIdempotencyKey.BULK_LOCK_HEADER_MAX))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountIds\":[\"" + ACCOUNT_ID_36 + "\"],\"reason\":\"635-it reason\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].accountId").value(ACCOUNT_ID_36))
                .andExpect(jsonPath("$.results[0].outcome").value("LOCKED"));
    }

    /** Inserts the operator (idempotent) and binds EXACTLY {@code roleName}, granted at the operator's home tenant. */
    private void seedOperator(String uuid, String tenantId, String roleName) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_operators WHERE operator_id = ?", Integer.class, uuid);
        if (existing == null || existing == 0) {
            jdbcTemplate.update("""
                    INSERT INTO admin_operators
                      (operator_id, tenant_id, email, password_hash, display_name,
                       status, created_at, updated_at, version)
                    VALUES (?, ?, ?, NULL, ?, 'ACTIVE', NOW(6), NOW(6), 0)
                    """, uuid, tenantId, uuid + "@example.com", "635 " + roleName);
        }
        jdbcTemplate.update("""
                DELETE ar FROM admin_operator_roles ar
                  JOIN admin_operators o ON o.id = ar.operator_id
                 WHERE o.operator_id = ?
                """, uuid);
        jdbcTemplate.update("""
                INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
                SELECT o.id, r.id, o.tenant_id, NOW(6), NULL
                  FROM admin_operators o CROSS JOIN admin_roles r
                 WHERE o.operator_id = ? AND r.name = ?
                """, uuid, roleName);
    }
}
