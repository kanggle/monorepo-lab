package com.example.admin.integration;

import com.example.admin.infrastructure.persistence.AdminActionJpaEntity;
import com.example.admin.infrastructure.persistence.AdminActionJpaRepository;
import com.example.admin.support.OperatorJwtTestFixture;
import com.example.testsupport.integration.AbstractIntegrationTest;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-083 — integration coverage for operator management. Boots the full
 * Spring context against a real MySQL + Redis (Testcontainers) and exercises
 * create → list → patch roles → patch status against the SQL schema seeded by
 * Flyway (V0006 + V0022).
 *
 * <p>TASK-BE-084: extends {@link AbstractIntegrationTest} so MySQL + Kafka are
 * shared JVM-wide (per platform/testing-strategy.md). Redis remains declared
 * on this class because it is not part of the shared base.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class OperatorAdminIntegrationTest extends AbstractIntegrationTest {

    // MySQL + Kafka inherited from AbstractIntegrationTest (TASK-BE-076/078).
    // Redis remains service-specific.
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
        // no-op; containers managed by @Testcontainers
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
        // MySQL + Kafka registered by AbstractIntegrationTest (TASK-BE-084).
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

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AdminActionJpaRepository adminActionRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PasswordHasher passwordHasher;

    private static final String SUPER_ADMIN_UUID = "00000000-0000-7000-8000-000000000010";

    private String superAdminToken() {
        return "Bearer " + jwt.operatorToken(SUPER_ADMIN_UUID);
    }

    @BeforeEach
    void seedSuperAdmin() {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_operators WHERE operator_id = ?",
                Integer.class, SUPER_ADMIN_UUID);
        if (existing == null || existing == 0) {
            // TASK-BE-249: include tenant_id='*' for SUPER_ADMIN
            jdbcTemplate.update("""
                    INSERT INTO admin_operators
                      (operator_id, tenant_id, email, password_hash, display_name, status,
                       created_at, updated_at, version)
                    VALUES (?, '*', ?, ?, ?, 'ACTIVE', NOW(6), NOW(6), 0)
                    """,
                    SUPER_ADMIN_UUID, "super@example.com", "x", "Super Admin");
        }
        jdbcTemplate.update("""
                INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, granted_at, granted_by, tenant_id)
                SELECT o.id, r.id, NOW(6), NULL, o.tenant_id
                  FROM admin_operators o CROSS JOIN admin_roles r
                 WHERE o.operator_id = ? AND r.name = 'SUPER_ADMIN'
                """, SUPER_ADMIN_UUID);
    }

    @Test
    @DisplayName("operator.manage permission is seeded for SUPER_ADMIN by V0022")
    void operatorManagePermission_isSeededForSuperAdmin() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM admin_role_permissions p
                JOIN admin_roles r ON r.id = p.role_id
                WHERE r.name = 'SUPER_ADMIN' AND p.permission_key = 'operator.manage'
                """, Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("POST /operators → 201, list reflects new row, PATCH roles replaces bindings")
    void createThenListThenPatchRoles() throws Exception {
        // --- create
        String createdEmail = "fresh-" + System.currentTimeMillis() + "@example.com";
        String body = """
                {
                  "email": "%s",
                  "displayName": "Fresh Op",
                  "password": "StrongPass1!",
                  "roles": ["SUPPORT_LOCK"],
                  "tenantId": "fan-platform"
                }
                """.formatted(createdEmail);

        String response = mockMvc.perform(post("/api/admin/operators")
                        .header("Authorization", superAdminToken())
                        .header("X-Operator-Reason", "provisioning")
                        .header("Idempotency-Key", "idemp-op-new-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(createdEmail))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.roles[0]").value("SUPPORT_LOCK"))
                .andExpect(jsonPath("$.totpEnrolled").value(false))
                .andExpect(jsonPath("$.operatorId").exists())
                .andReturn().getResponse().getContentAsString();

        // OPERATOR_CREATE audit row exists.
        List<AdminActionJpaEntity> createRows = adminActionRepository.findAll().stream()
                .filter(r -> "OPERATOR_CREATE".equals(r.getActionCode()))
                .toList();
        assertThat(createRows).isNotEmpty();

        // Extract the fresh operatorId for downstream patch calls.
        String newOperatorId = extract(response, "\"operatorId\":\"", "\"");
        assertThat(newOperatorId).isNotBlank();

        // Response must never leak sensitive fields.
        assertThat(response).doesNotContain("password_hash");
        assertThat(response).doesNotContain("totp_secret_encrypted");

        // --- list (without filter) reflects the new operator somewhere in the
        // first page (we seeded only SUPER_ADMIN + the new one so size 50 suffices).
        mockMvc.perform(get("/api/admin/operators?size=50")
                        .header("Authorization", superAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.content[?(@.operatorId == '" + newOperatorId + "')]")
                        .exists());

        // --- patch roles: replace with SUPPORT_READONLY + SECURITY_ANALYST
        mockMvc.perform(patch("/api/admin/operators/" + newOperatorId + "/roles")
                        .header("Authorization", superAdminToken())
                        .header("X-Operator-Reason", "rotation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles": ["SUPPORT_READONLY", "SECURITY_ANALYST"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operatorId").value(newOperatorId))
                .andExpect(jsonPath("$.roles.length()").value(2));

        // DB reflects full replacement: SUPPORT_LOCK binding removed, two new.
        Integer lockBindings = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM admin_operator_roles b
                JOIN admin_operators o ON o.id = b.operator_id
                JOIN admin_roles r ON r.id = b.role_id
                WHERE o.operator_id = ? AND r.name = 'SUPPORT_LOCK'
                """, Integer.class, newOperatorId);
        assertThat(lockBindings).isEqualTo(0);

        Integer newBindings = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM admin_operator_roles b
                JOIN admin_operators o ON o.id = b.operator_id
                JOIN admin_roles r ON r.id = b.role_id
                WHERE o.operator_id = ? AND r.name IN ('SUPPORT_READONLY', 'SECURITY_ANALYST')
                """, Integer.class, newOperatorId);
        assertThat(newBindings).isEqualTo(2);

        // OPERATOR_ROLE_CHANGE audit row emitted.
        assertThat(adminActionRepository.findAll().stream()
                .anyMatch(r -> "OPERATOR_ROLE_CHANGE".equals(r.getActionCode())))
                .isTrue();
    }

    @Test
    @DisplayName("POST /operators rejects duplicate email with 409 OPERATOR_EMAIL_CONFLICT")
    void duplicateEmail_returns_409() throws Exception {
        String duplicateEmail = "dup-" + System.currentTimeMillis() + "@example.com";
        String body = """
                {
                  "email": "%s",
                  "displayName": "First",
                  "password": "StrongPass1!",
                  "roles": [],
                  "tenantId": "fan-platform"
                }
                """.formatted(duplicateEmail);

        mockMvc.perform(post("/api/admin/operators")
                        .header("Authorization", superAdminToken())
                        .header("X-Operator-Reason", "provisioning")
                        .header("Idempotency-Key", "idemp-dup-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // Second call with the same email → 409.
        mockMvc.perform(post("/api/admin/operators")
                        .header("Authorization", superAdminToken())
                        .header("X-Operator-Reason", "provisioning")
                        .header("Idempotency-Key", "idemp-dup-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPERATOR_EMAIL_CONFLICT"));
    }

    // -------------------------------------- PATCH /operators/me/password (TASK-BE-086)

    @Test
    @DisplayName("PATCH /operators/me/password: valid current + valid new → 204, hash rotated")
    void changeMyPassword_success_returns_204_and_rotates_hash() throws Exception {
        // Seed a dedicated operator with a real Argon2id hash of the known
        // current password so we do not disturb the shared SUPER_ADMIN fixture.
        String pwOperatorUuid = "00000000-0000-7000-8000-0000000000A1";
        String currentPassword = "OldPass1!";
        String originalHash = passwordHasher.hash(currentPassword);
        seedOperatorWithHash(pwOperatorUuid, "pw-change-ok@example.com",
                "PW Change OK", originalHash);

        String token = "Bearer " + jwt.operatorToken(pwOperatorUuid);

        mockMvc.perform(patch("/api/admin/operators/me/password")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"OldPass1!","newPassword":"NewPass2@"}
                                """))
                .andExpect(status().isNoContent());

        String storedHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM admin_operators WHERE operator_id = ?",
                String.class, pwOperatorUuid);
        assertThat(storedHash).isNotNull();
        assertThat(storedHash).isNotEqualTo(originalHash);
        assertThat(passwordHasher.verify("NewPass2@", storedHash)).isTrue();
        assertThat(passwordHasher.verify("OldPass1!", storedHash)).isFalse();
    }

    @Test
    @DisplayName("PATCH /operators/me/password: wrong current password → 400 CURRENT_PASSWORD_MISMATCH, hash unchanged")
    void changeMyPassword_currentMismatch_returns_400_and_preserves_hash() throws Exception {
        String pwOperatorUuid = "00000000-0000-7000-8000-0000000000A2";
        String currentPassword = "OldPass1!";
        String originalHash = passwordHasher.hash(currentPassword);
        seedOperatorWithHash(pwOperatorUuid, "pw-change-mismatch@example.com",
                "PW Change Mismatch", originalHash);

        String token = "Bearer " + jwt.operatorToken(pwOperatorUuid);

        mockMvc.perform(patch("/api/admin/operators/me/password")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"WrongOld1!","newPassword":"NewPass2@"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_MISMATCH"));

        // Failure scenario guarantee: stored hash must not be mutated.
        String storedHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM admin_operators WHERE operator_id = ?",
                String.class, pwOperatorUuid);
        assertThat(storedHash).isEqualTo(originalHash);
    }

    /**
     * Idempotent insert of a standalone operator with a caller-supplied
     * password hash. Used by the TASK-BE-086 password-change tests so each
     * case owns its own fixture row and does not race with the shared
     * {@code SUPER_ADMIN_UUID} seed.
     */
    private void seedOperatorWithHash(String operatorUuid, String email,
                                       String displayName, String passwordHash) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_operators WHERE operator_id = ?",
                Integer.class, operatorUuid);
        if (existing == null || existing == 0) {
            // TASK-BE-249: include tenant_id
            jdbcTemplate.update("""
                    INSERT INTO admin_operators
                      (operator_id, tenant_id, email, password_hash, display_name, status,
                       created_at, updated_at, version)
                    VALUES (?, 'fan-platform', ?, ?, ?, 'ACTIVE', NOW(6), NOW(6), 0)
                    """,
                    operatorUuid, email, passwordHash, displayName);
        } else {
            // Ensure the hash matches the caller's expectation on re-runs.
            jdbcTemplate.update(
                    "UPDATE admin_operators SET password_hash = ?, updated_at = NOW(6) WHERE operator_id = ?",
                    passwordHash, operatorUuid);
        }
    }

    private static String extract(String body, String prefix, String terminator) {
        int start = body.indexOf(prefix);
        if (start < 0) return null;
        start += prefix.length();
        int end = body.indexOf(terminator, start);
        if (end < 0) return null;
        return body.substring(start, end);
    }
}
