package com.example.admin.integration;

import com.example.admin.support.OperatorJwtTestFixture;
import com.example.testsupport.integration.AbstractIntegrationTest;
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

import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-339 — integration coverage (Testcontainers MySQL, NO H2) for the
 * admin-facing org_scope management surface. Boots the full Spring context and
 * exercises PUT → GET round-trip against the real {@code org_scope} JSON column
 * (V0031): set persists, null clears (and GET response OMITS orgScope —
 * {@code @JsonInclude(NON_NULL)} / §14 / BE-338 lesson), {@code []} persists
 * (explicit zero-scope), active-tenant scoping, path↔header tenant-mismatch 403,
 * reason missing 400.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
class OperatorOrgScopeIntegrationTest extends AbstractIntegrationTest {

    // MySQL + Kafka inherited from AbstractIntegrationTest. Redis service-specific.
    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofMinutes(3));

    static OperatorJwtTestFixture jwt;
    static String signingKeyPem;

    static final String SUPER_ADMIN_UUID = "00000000-0000-7000-8000-0000000003a0";
    static final String TARGET_UUID = "00000000-0000-7000-8000-0000000003a1";
    static final String HOME_TENANT = "acme-corp";
    static final String ASSIGNED_TENANT = "globex";

    @org.junit.jupiter.api.BeforeAll
    static void setupShared() throws IOException {
        jwt = new OperatorJwtTestFixture();
        java.security.PrivateKey pk = extractPrivateKey(jwt);
        signingKeyPem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pk.getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
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
        registry.add("admin.auth-service.base-url", () -> "http://localhost:18087");
        registry.add("admin.account-service.base-url", () -> "http://localhost:18087");
        registry.add("admin.security-service.base-url", () -> "http://localhost:18087");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private String superAdminToken() {
        return "Bearer " + jwt.operatorToken(SUPER_ADMIN_UUID);
    }

    @BeforeEach
    void seed() {
        // Platform-scope SUPER_ADMIN (operator.manage) — the actor.
        seedOperator(SUPER_ADMIN_UUID, "*", "orgscope-super@example.com");
        jdbcTemplate.update("""
                INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, granted_at, granted_by, tenant_id)
                SELECT o.id, r.id, NOW(6), NULL, o.tenant_id
                  FROM admin_operators o CROSS JOIN admin_roles r
                 WHERE o.operator_id = ? AND r.name = 'SUPER_ADMIN'
                """, SUPER_ADMIN_UUID);

        // Target operator: home tenant acme-corp, plus an assignment row → globex
        // with org_scope initially NULL (net-zero).
        seedOperator(TARGET_UUID, HOME_TENANT, "orgscope-target@example.com");
        seedAssignment(TARGET_UUID, ASSIGNED_TENANT, null);
    }

    // ───────────────────────── AC-2 / AC-3: PUT → GET round-trip ─────────────────────────

    @Test
    @DisplayName("AC-2: PUT [ids] 영속 → GET 반영; JSON 배열 round-trip")
    void put_nonEmpty_persists_and_get_reflects() throws Exception {
        setOrgScope(TARGET_UUID, ASSIGNED_TENANT, ASSIGNED_TENANT, """
                {"orgScope": ["dept-sales", "dept-eng"]}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(ASSIGNED_TENANT))
                .andExpect(jsonPath("$.orgScope[0]").value("dept-sales"))
                .andExpect(jsonPath("$.orgScope[1]").value("dept-eng"));

        // GET reflects the persisted value (active tenant = globex).
        getAssignments(TARGET_UUID, ASSIGNED_TENANT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignments[0].tenantId").value(ASSIGNED_TENANT))
                .andExpect(jsonPath("$.assignments[0].orgScope[0]").value("dept-sales"))
                .andExpect(jsonPath("$.assignments[0].orgScope[1]").value("dept-eng"));

        // DB JSON column reflects the array.
        String stored = jdbcTemplate.queryForObject("""
                SELECT a.org_scope FROM operator_tenant_assignment a
                JOIN admin_operators o ON o.id = a.operator_id
                WHERE o.operator_id = ? AND a.tenant_id = ?
                """, String.class, TARGET_UUID, ASSIGNED_TENANT);
        assertThat(stored).contains("dept-sales").contains("dept-eng");
    }

    @Test
    @DisplayName("AC-3: PUT null → 컬럼 NULL clear; GET orgScope ABSENT (.doesNotExist())")
    void put_null_clears_and_get_omits_orgScope() throws Exception {
        // First set a value, then clear it.
        setOrgScope(TARGET_UUID, ASSIGNED_TENANT, ASSIGNED_TENANT,
                "{\"orgScope\": [\"dept-x\"]}").andExpect(status().isOk());

        setOrgScope(TARGET_UUID, ASSIGNED_TENANT, ASSIGNED_TENANT, "{\"orgScope\": null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(ASSIGNED_TENANT))
                .andExpect(jsonPath("$.orgScope").doesNotExist());

        // GET: orgScope OMITTED (NON_NULL) — assert doesNotExist (NOT nullValue()).
        getAssignments(TARGET_UUID, ASSIGNED_TENANT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignments[0].tenantId").value(ASSIGNED_TENANT))
                .andExpect(jsonPath("$.assignments[0].orgScope").doesNotExist());

        // DB column is NULL.
        String stored = jdbcTemplate.queryForObject("""
                SELECT a.org_scope FROM operator_tenant_assignment a
                JOIN admin_operators o ON o.id = a.operator_id
                WHERE o.operator_id = ? AND a.tenant_id = ?
                """, String.class, TARGET_UUID, ASSIGNED_TENANT);
        assertThat(stored).isNull();
    }

    @Test
    @DisplayName("AC-3: PUT [] → 빈 배열 영속 (zero-scope, NULL 과 구분); GET 빈 배열 반영")
    void put_emptyList_persists_zeroScope() throws Exception {
        setOrgScope(TARGET_UUID, ASSIGNED_TENANT, ASSIGNED_TENANT, "{\"orgScope\": []}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orgScope").isArray())
                .andExpect(jsonPath("$.orgScope").isEmpty());

        getAssignments(TARGET_UUID, ASSIGNED_TENANT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignments[0].orgScope").isArray())
                .andExpect(jsonPath("$.assignments[0].orgScope").isEmpty());

        // DB column is an empty JSON array (NOT NULL).
        String stored = jdbcTemplate.queryForObject("""
                SELECT a.org_scope FROM operator_tenant_assignment a
                JOIN admin_operators o ON o.id = a.operator_id
                WHERE o.operator_id = ? AND a.tenant_id = ?
                """, String.class, TARGET_UUID, ASSIGNED_TENANT);
        assertThat(stored).isEqualTo("[]");
    }

    // ───────────────────────── AC-1: active-tenant scoping ─────────────────────────

    @Test
    @DisplayName("AC-1: GET 활성 테넌트에 미배정 → 빈 배열 (타 테넌트 누설 없음)")
    void get_unassigned_active_tenant_returns_empty() throws Exception {
        // Target has an assignment to globex but NOT to acme-corp's "initech".
        getAssignments(TARGET_UUID, "initech")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignments").isEmpty());
    }

    // ───────────────────────── AC-4: error codes ─────────────────────────

    @Test
    @DisplayName("AC-4: path tenantId != X-Tenant-Id → 403 TENANT_SCOPE_MISMATCH")
    void put_tenant_mismatch_returns_403() throws Exception {
        setOrgScope(TARGET_UUID, ASSIGNED_TENANT, HOME_TENANT, "{\"orgScope\": [\"dept-x\"]}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_MISMATCH"));
    }

    @Test
    @DisplayName("AC-4: 활성 테넌트에 assignment 행 부재 → 404 ASSIGNMENT_NOT_FOUND")
    void put_no_assignment_returns_404() throws Exception {
        // acme-corp is the home tenant but has NO explicit assignment row.
        setOrgScope(TARGET_UUID, HOME_TENANT, HOME_TENANT, "{\"orgScope\": [\"dept-x\"]}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ASSIGNMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("AC-4: reason 누락 → 400 REASON_REQUIRED")
    void put_missing_reason_returns_400() throws Exception {
        mockMvc.perform(put("/api/admin/operators/" + TARGET_UUID
                        + "/assignments/" + ASSIGNED_TENANT + "/org-scope")
                        .header("Authorization", superAdminToken())
                        .header("X-Tenant-Id", ASSIGNED_TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orgScope\": [\"dept-x\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
    }

    @Test
    @DisplayName("정규화: 중복/공백 입력 → dedupe+trim 후 영속")
    void put_normalizes_dedupe_and_trim() throws Exception {
        setOrgScope(TARGET_UUID, ASSIGNED_TENANT, ASSIGNED_TENANT,
                "{\"orgScope\": [\" dept-a \", \"dept-b\", \"dept-a\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orgScope.length()").value(2))
                .andExpect(jsonPath("$.orgScope[0]").value("dept-a"))
                .andExpect(jsonPath("$.orgScope[1]").value("dept-b"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private org.springframework.test.web.servlet.ResultActions getAssignments(
            String operatorUuid, String activeTenant) throws Exception {
        return mockMvc.perform(get("/api/admin/operators/" + operatorUuid + "/assignments")
                .header("Authorization", superAdminToken())
                .header("X-Tenant-Id", activeTenant));
    }

    private org.springframework.test.web.servlet.ResultActions setOrgScope(
            String operatorUuid, String pathTenant, String activeTenant, String jsonBody)
            throws Exception {
        return mockMvc.perform(put("/api/admin/operators/" + operatorUuid
                        + "/assignments/" + pathTenant + "/org-scope")
                .header("Authorization", superAdminToken())
                .header("X-Tenant-Id", activeTenant)
                .header("X-Operator-Reason", "reorg")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody));
    }

    private void seedOperator(String uuid, String tenantId, String email) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_operators WHERE operator_id = ?", Integer.class, uuid);
        if (existing == null || existing == 0) {
            jdbcTemplate.update("""
                    INSERT INTO admin_operators
                      (operator_id, tenant_id, email, password_hash, display_name, status,
                       created_at, updated_at, version)
                    VALUES (?, ?, ?, 'x', 'Org Scope Op', 'ACTIVE', NOW(6), NOW(6), 0)
                    """, uuid, tenantId, email);
        }
    }

    private void seedAssignment(String operatorUuid, String assignedTenant, String orgScopeJson) {
        jdbcTemplate.update("""
                INSERT IGNORE INTO operator_tenant_assignment
                  (operator_id, tenant_id, granted_at, granted_by, permission_set_id, org_scope)
                SELECT o.id, ?, NOW(6), NULL, NULL, CAST(? AS JSON)
                  FROM admin_operators o WHERE o.operator_id = ?
                """, assignedTenant, orgScopeJson, operatorUuid);
    }
}
