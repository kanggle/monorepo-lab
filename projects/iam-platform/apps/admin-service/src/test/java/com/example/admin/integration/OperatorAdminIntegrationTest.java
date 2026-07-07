package com.example.admin.integration;

import com.example.admin.infrastructure.persistence.AdminActionJpaEntity;
import com.example.admin.infrastructure.persistence.AdminActionJpaRepository;
import com.example.admin.infrastructure.client.AccountServiceClient;
import com.example.admin.support.OperatorJwtTestFixture;
import com.example.testsupport.integration.AbstractIntegrationTest;
import com.example.security.password.PasswordHasher;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
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

    // TASK-MONO-334: operator-create now probes account-service for the target
    // email's tenant account. account-service is not booted in this test (base-url
    // is a dead port), so mock the client: default the probe to "exists" so the
    // create/list/patch flows proceed; the 422 test overrides it to "absent".
    // (resolveOrCreateIdentity stays unstubbed → null → operator born unlinked,
    // matching the prior dead-port fail-soft behavior.)
    @MockitoBean
    AccountServiceClient accountServiceClient;

    private static final String SUPER_ADMIN_UUID = "00000000-0000-7000-8000-000000000010";

    private String superAdminToken() {
        return "Bearer " + jwt.operatorToken(SUPER_ADMIN_UUID);
    }

    @BeforeEach
    void stubAccountExists() {
        // Default: the target email HAS a signed-up account in the tenant (totalElements=1).
        when(accountServiceClient.search(anyString(), anyString()))
                .thenReturn(new AccountServiceClient.AccountSearchResponse(List.of(), 1, 0, 1, 1));
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
    @DisplayName("MONO-334: POST /operators with an email that has no tenant account → 422 OPERATOR_ACCOUNT_NOT_FOUND")
    void createOperator_accountNotFound_returns422() throws Exception {
        String ghostEmail = "ghost-" + System.currentTimeMillis() + "@example.com";
        // Override the default "exists" stub for this email → definitively absent.
        when(accountServiceClient.search("fan-platform", ghostEmail))
                .thenReturn(new AccountServiceClient.AccountSearchResponse(List.of(), 0, 0, 1, 0));

        String body = """
                {
                  "email": "%s",
                  "displayName": "Ghost Op",
                  "roles": ["SUPPORT_LOCK"],
                  "tenantId": "fan-platform"
                }
                """.formatted(ghostEmail);

        mockMvc.perform(post("/api/admin/operators")
                        .header("Authorization", superAdminToken())
                        .header("X-Operator-Reason", "provisioning")
                        .header("Idempotency-Key", "idemp-ghost-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("OPERATOR_ACCOUNT_NOT_FOUND"));

        // Fail-closed: no operator row was persisted for the ghost email.
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_operators WHERE email = ?", Integer.class, ghostEmail);
        assertThat(count).isZero();
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

    /**
     * TASK-BE-289 WI-2 — tenant-isolation regression (PROJECT.md mandate).
     *
     * <p>Before WI-2, {@code PatchOperatorRoleUseCase} bound roles via the legacy
     * 4-arg factory which hardcoded {@code tenant_id='fan-platform'}. For a
     * non-fan-platform operator that silently mis-scoped the binding (TASK-BE-288
     * review Finding 1). This asserts the patched bindings now carry the target
     * operator's own {@code tenant_id}, consistent with {@code CreateOperatorUseCase}.
     */
    @Test
    @DisplayName("TASK-BE-289 WI-2: patch-roles on a non-fan-platform operator stamps the operator's tenant_id (not legacy 'fan-platform')")
    void patchRoles_nonFanPlatformOperator_bindingTenantMatchesOperatorTenant() throws Exception {
        // SUPER_ADMIN (platform scope) provisions an operator into "tenant-x".
        String email = "tenantx-" + System.currentTimeMillis() + "@example.com";
        String createBody = """
                {
                  "email": "%s",
                  "displayName": "Tenant X Op",
                  "password": "StrongPass1!",
                  "roles": ["SUPPORT_LOCK"],
                  "tenantId": "tenant-x"
                }
                """.formatted(email);

        String response = mockMvc.perform(post("/api/admin/operators")
                        .header("Authorization", superAdminToken())
                        .header("X-Operator-Reason", "provisioning")
                        .header("Idempotency-Key", "idemp-tenantx-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String operatorUuid = extract(response, "\"operatorId\":\"", "\"");
        assertThat(operatorUuid).isNotBlank();

        String operatorTenant = jdbcTemplate.queryForObject(
                "SELECT tenant_id FROM admin_operators WHERE operator_id = ?",
                String.class, operatorUuid);
        assertThat(operatorTenant).isEqualTo("tenant-x");

        // The regression-prone path: replace the role set.
        mockMvc.perform(patch("/api/admin/operators/" + operatorUuid + "/roles")
                        .header("Authorization", superAdminToken())
                        .header("X-Operator-Reason", "rotation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles": ["SUPPORT_READONLY", "SECURITY_ANALYST"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(2));

        // Every persisted binding must mirror the operator's real tenant_id.
        List<String> bindingTenants = jdbcTemplate.queryForList("""
                SELECT b.tenant_id FROM admin_operator_roles b
                JOIN admin_operators o ON o.id = b.operator_id
                WHERE o.operator_id = ?
                """, String.class, operatorUuid);
        assertThat(bindingTenants)
                .isNotEmpty()
                .allMatch("tenant-x"::equals);
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

    // -------------------------------- GET /operators operatorContext (TASK-BE-308)

    @Test
    @DisplayName("TASK-BE-308: GET /operators emits operatorContext.defaultAccountId for operators with non-null finance_default_account_id")
    void listOperators_emitsOperatorContext_whenColumnSet() throws Exception {
        // Hermetic per-test target UUIDs (mirror BE-306 cycle 2 lesson — append-only
        // audit table tolerates by construction, but state-isolation via fresh UUIDs
        // is robust against the shared-fixture row from @BeforeEach).
        String withValueUuid = "00000000-0000-7000-8000-0000000000B1";
        String noValueUuid = "00000000-0000-7000-8000-0000000000B2";
        String accountId = "01928c4a-7e9f-7c00-9a40-d2b1f5e8a000";
        seedOperatorWithFinanceDefault(withValueUuid, "be308-with@example.com",
                "BE308 With", accountId);
        seedOperatorWithFinanceDefault(noValueUuid, "be308-without@example.com",
                "BE308 Without", null);

        String body = mockMvc.perform(get("/api/admin/operators?size=100")
                        .header("Authorization", superAdminToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // The non-NULL operator's item carries the carrier; the NULL operator's
        // item omits the entire operatorContext key.
        int withItemStart = body.indexOf("\"operatorId\":\"" + withValueUuid + "\"");
        assertThat(withItemStart).as("with-value item present").isGreaterThanOrEqualTo(0);
        int withItemEnd = findItemEnd(body, withItemStart);
        String withItem = body.substring(withItemStart, withItemEnd);
        assertThat(withItem)
                .as("with-value item carries operatorContext.defaultAccountId")
                .contains("\"operatorContext\":{\"defaultAccountId\":\"" + accountId + "\"}");

        int noValueStart = body.indexOf("\"operatorId\":\"" + noValueUuid + "\"");
        assertThat(noValueStart).as("no-value item present").isGreaterThanOrEqualTo(0);
        int noValueEnd = findItemEnd(body, noValueStart);
        String noValueItem = body.substring(noValueStart, noValueEnd);
        assertThat(noValueItem)
                .as("no-value item omits operatorContext entirely (@JsonInclude.NON_NULL)")
                .doesNotContain("operatorContext");
    }

    /**
     * Walks forward from an item's {@code "operatorId":"..."} occurrence to the
     * end of the enclosing JSON object so a substring assertion is scoped to
     * the matching item only (the list response carries multiple items;
     * {@code body.contains("operatorContext")} would otherwise match siblings).
     */
    private static int findItemEnd(String body, int itemStart) {
        int depth = 0;
        int cursor = body.lastIndexOf('{', itemStart);
        for (int i = cursor; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i + 1;
            }
        }
        return body.length();
    }

    /**
     * Idempotent insert of a standalone operator with a caller-supplied
     * {@code finance_default_account_id} value (nullable). Used by the
     * TASK-BE-308 list-projection tests so each case owns its own fixture
     * row and does not race with the shared {@code SUPER_ADMIN_UUID} seed.
     */
    private void seedOperatorWithFinanceDefault(String operatorUuid, String email,
                                                 String displayName, String financeDefaultAccountId) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_operators WHERE operator_id = ?",
                Integer.class, operatorUuid);
        if (existing == null || existing == 0) {
            jdbcTemplate.update("""
                    INSERT INTO admin_operators
                      (operator_id, tenant_id, email, password_hash, display_name, status,
                       finance_default_account_id, created_at, updated_at, version)
                    VALUES (?, 'fan-platform', ?, 'x', ?, 'ACTIVE', ?, NOW(6), NOW(6), 0)
                    """,
                    operatorUuid, email, displayName, financeDefaultAccountId);
        } else {
            jdbcTemplate.update(
                    "UPDATE admin_operators SET finance_default_account_id = ?, updated_at = NOW(6) WHERE operator_id = ?",
                    financeDefaultAccountId, operatorUuid);
        }
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
