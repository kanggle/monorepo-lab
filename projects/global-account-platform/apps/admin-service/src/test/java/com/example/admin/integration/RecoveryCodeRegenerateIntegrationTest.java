package com.example.admin.integration;

import com.example.admin.application.TotpEnrollmentService;
import com.example.admin.infrastructure.persistence.AdminActionJpaRepository;
import com.example.admin.support.OperatorJwtTestFixture;
import com.example.testsupport.integration.AbstractIntegrationTest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gap.security.password.PasswordHasher;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-116 — integration coverage for the operator recovery-code
 * regeneration flow ({@code POST /api/admin/auth/2fa/recovery-codes/regenerate}).
 *
 * <p>Boots the full Spring context against real MySQL + Redis (Testcontainers)
 * and exercises five scenarios required by the task spec:
 * <ol>
 *   <li>Valid operator JWT + enrolled TOTP → 200, 10 plaintext codes returned,
 *       {@code admin_operator_totp.recovery_codes_hashed} contains 10 fresh
 *       Argon2id hashes that differ from the previous set.</li>
 *   <li>Previous (pre-regeneration) recovery codes are rejected with 401
 *       {@code INVALID_RECOVERY_CODE} on subsequent login attempts (AC2).</li>
 *   <li>New recovery codes are accepted by the login flow (AC3).</li>
 *   <li>Operator without an {@code admin_operator_totp} row → 404
 *       {@code TOTP_NOT_ENROLLED}.</li>
 *   <li>No JWT → 401.</li>
 * </ol>
 *
 * <p>Extends {@link AbstractIntegrationTest} so MySQL + Kafka are shared
 * JVM-wide (TASK-BE-076). Redis remains declared on this class because it is
 * not part of the shared base.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class RecoveryCodeRegenerateIntegrationTest extends AbstractIntegrationTest {

    // MySQL + Kafka inherited from AbstractIntegrationTest. Redis is service-specific.
    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofMinutes(3));

    static OperatorJwtTestFixture jwt;
    static String signingKeyPem;

    @BeforeAll
    static void setupShared() throws IOException {
        jwt = new OperatorJwtTestFixture();
        java.security.PrivateKey pk = extractPrivateKey(jwt);
        signingKeyPem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pk.getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
    }

    @AfterAll
    static void tearDownShared() {
        // no-op; containers managed by @Testcontainers + base class
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
        // MySQL + Kafka registered by AbstractIntegrationTest.
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("admin.jwt.active-signing-kid", () -> "test-key-001");
        registry.add("admin.jwt.signing-keys.test-key-001", () -> signingKeyPem);
        registry.add("admin.jwt.issuer", () -> "admin-service");
        registry.add("admin.jwt.expected-token-type", () -> "admin");
        registry.add("admin.auth-service.base-url", () -> "http://localhost:18085");
        registry.add("admin.account-service.base-url", () -> "http://localhost:18085");
        registry.add("admin.security-service.base-url", () -> "http://localhost:18085");
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired AdminActionJpaRepository adminActionRepository;
    @Autowired TotpEnrollmentService totpEnrollmentService;
    @Autowired PasswordHasher passwordHasher;
    @Autowired ObjectMapper objectMapper;

    private static final String OPERATOR_PASSWORD = "OldPass1!";

    private String operatorToken(String operatorUuid) {
        return "Bearer " + jwt.operatorToken(operatorUuid);
    }

    /**
     * Idempotent insert of an operator row + SUPER_ADMIN binding (require_2fa=TRUE
     * per V0013 seed). Subsequent runs in the same JVM-shared MySQL container
     * tolerate the duplicate and only ensure the password hash matches.
     */
    @BeforeEach
    void seedOperatorsBaseline() {
        // No global seed — each scenario seeds its own operator row to avoid
        // cross-test contamination of admin_operator_totp.
    }

    private void seedOperatorWithRole(String operatorUuid, String email,
                                       String displayName, String passwordHash,
                                       String roleName) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_operators WHERE operator_id = ?",
                Integer.class, operatorUuid);
        if (existing == null || existing == 0) {
            jdbcTemplate.update("""
                    INSERT INTO admin_operators
                      (operator_id, tenant_id, email, password_hash, display_name, status,
                       created_at, updated_at, version)
                    VALUES (?, 'fan-platform', ?, ?, ?, 'ACTIVE', NOW(6), NOW(6), 0)
                    """,
                    operatorUuid, email, passwordHash, displayName);
        } else {
            jdbcTemplate.update(
                    "UPDATE admin_operators SET password_hash = ?, updated_at = NOW(6) WHERE operator_id = ?",
                    passwordHash, operatorUuid);
        }
        jdbcTemplate.update("""
                INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
                SELECT o.id, r.id, o.tenant_id, NOW(6), NULL
                  FROM admin_operators o CROSS JOIN admin_roles r
                 WHERE o.operator_id = ? AND r.name = ?
                """, operatorUuid, roleName);
    }

    private List<String> readStoredHashes(String operatorUuid) {
        String json = jdbcTemplate.queryForObject("""
                SELECT t.recovery_codes_hashed FROM admin_operator_totp t
                JOIN admin_operators o ON o.id = t.operator_id
                WHERE o.operator_id = ?
                """, String.class, operatorUuid);
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String uniqueOperatorUuid(String suffix) {
        // Use UUID v7-style placeholder; the non-hex bytes are tolerated by the
        // VARCHAR-typed operator_id column and keep test fixtures distinct.
        return "00000000-0000-7000-8000-1160000000" + suffix;
    }

    @Test
    @DisplayName("AC1: valid operator JWT + enrolled TOTP → 200, 10 codes returned, DB hashes refreshed")
    void regenerate_success_returns200_and_persistsNewHashes() throws Exception {
        String operatorUuid = uniqueOperatorUuid("01");
        seedOperatorWithRole(operatorUuid, "regen-ok-" + System.currentTimeMillis() + "@example.com",
                "Regen OK", passwordHasher.hash(OPERATOR_PASSWORD), "SUPER_ADMIN");

        // Pre-condition: enroll TOTP so a row exists with a known set of recovery hashes.
        TotpEnrollmentService.EnrollmentResult enrolled = totpEnrollmentService.enroll(operatorUuid);
        List<String> beforeHashes = readStoredHashes(operatorUuid);
        assertThat(beforeHashes).hasSize(10);
        assertThat(enrolled.recoveryCodes()).hasSize(10);

        MvcResult result = mockMvc.perform(post("/api/admin/auth/2fa/recovery-codes/regenerate")
                        .header("Authorization", operatorToken(operatorUuid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveryCodes.length()").value(10))
                .andReturn();

        // Response body: exactly 10 codes, all in XXXX-XXXX-XXXX format.
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"recoveryCodes\"");
        assertThat(objectMapper.readTree(body).get("recoveryCodes"))
                .allMatch(node -> node.asText().matches("[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}"));

        // DB: the stored hashes must be 10 fresh entries that differ from the pre-regeneration set.
        List<String> afterHashes = readStoredHashes(operatorUuid);
        assertThat(afterHashes).hasSize(10);
        assertThat(afterHashes).doesNotContainAnyElementsOf(beforeHashes);

        // Audit row for OPERATOR_2FA_RECOVERY_REGENERATE was emitted with SUCCESS.
        Long successAudits = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM admin_actions
                 WHERE action_code = 'OPERATOR_2FA_RECOVERY_REGENERATE'
                   AND target_id  = ?
                   AND outcome    = 'SUCCESS'
                """, Long.class, operatorUuid);
        assertThat(successAudits).isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("AC2 + AC3: previous code rejected with 401 INVALID_RECOVERY_CODE; new code accepted by login")
    void regenerate_invalidatesPreviousCodes_andNewCodesAreAccepted() throws Exception {
        String operatorUuid = uniqueOperatorUuid("02");
        seedOperatorWithRole(operatorUuid, "regen-rotate-" + System.currentTimeMillis() + "@example.com",
                "Regen Rotate", passwordHasher.hash(OPERATOR_PASSWORD), "SUPER_ADMIN");

        // Initial enrollment captures the original recovery codes.
        TotpEnrollmentService.EnrollmentResult initial = totpEnrollmentService.enroll(operatorUuid);
        String oldRecoveryCode = initial.recoveryCodes().get(0);

        // Mark TOTP as previously verified so the login flow does not bounce
        // through the EnrollmentRequired bootstrap branch (AdminLoginService
        // requires last_used_at != null to allow code submission).
        jdbcTemplate.update("""
                UPDATE admin_operator_totp t
                JOIN admin_operators o ON o.id = t.operator_id
                SET t.last_used_at = NOW(6)
                WHERE o.operator_id = ?
                """, operatorUuid);

        // Regenerate codes — the previous set must now be invalid.
        MvcResult regen = mockMvc.perform(post("/api/admin/auth/2fa/recovery-codes/regenerate")
                        .header("Authorization", operatorToken(operatorUuid)))
                .andExpect(status().isOk())
                .andReturn();
        var newCodesJson = objectMapper.readTree(regen.getResponse().getContentAsString())
                .get("recoveryCodes");
        assertThat(newCodesJson).hasSize(10);
        String newRecoveryCode = newCodesJson.get(0).asText();
        assertThat(newRecoveryCode).isNotEqualTo(oldRecoveryCode);

        // AC2: login attempt with the OLD code → 401 INVALID_RECOVERY_CODE.
        String oldLoginBody = """
                {"operatorId":"%s","password":"%s","recoveryCode":"%s"}
                """.formatted(operatorUuid, OPERATOR_PASSWORD, oldRecoveryCode);
        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oldLoginBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_RECOVERY_CODE"));

        // AC3: login attempt with a NEW code → 200, access + refresh tokens issued.
        String newLoginBody = """
                {"operatorId":"%s","password":"%s","recoveryCode":"%s"}
                """.formatted(operatorUuid, OPERATOR_PASSWORD, newRecoveryCode);
        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newLoginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists());
    }

    @Test
    @DisplayName("AC4: operator without admin_operator_totp row → 404 TOTP_NOT_ENROLLED")
    void regenerate_returns404_whenTotpNotEnrolled() throws Exception {
        String operatorUuid = uniqueOperatorUuid("03");
        seedOperatorWithRole(operatorUuid, "regen-noenroll-" + System.currentTimeMillis() + "@example.com",
                "Regen NoEnroll", passwordHasher.hash(OPERATOR_PASSWORD), "SUPPORT_LOCK");

        // Defensive cleanup: ensure no totp row leaked from a prior test on the
        // shared container before this UUID was used.
        jdbcTemplate.update("""
                DELETE t FROM admin_operator_totp t
                JOIN admin_operators o ON o.id = t.operator_id
                WHERE o.operator_id = ?
                """, operatorUuid);

        mockMvc.perform(post("/api/admin/auth/2fa/recovery-codes/regenerate")
                        .header("Authorization", operatorToken(operatorUuid)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TOTP_NOT_ENROLLED"));

        // FAILURE audit row was emitted with the TOTP_NOT_ENROLLED detail.
        Long failureAudits = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM admin_actions
                 WHERE action_code = 'OPERATOR_2FA_RECOVERY_REGENERATE'
                   AND target_id  = ?
                   AND outcome    = 'FAILURE'
                """, Long.class, operatorUuid);
        assertThat(failureAudits).isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("AC5: missing Authorization header → 401")
    void regenerate_returns401_whenJwtAbsent() throws Exception {
        mockMvc.perform(post("/api/admin/auth/2fa/recovery-codes/regenerate"))
                .andExpect(status().isUnauthorized());
    }
}
