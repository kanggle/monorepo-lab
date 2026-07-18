package com.example.admin.integration;

import com.example.admin.support.OperatorJwtTestFixture;
import com.example.testsupport.integration.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.io.IOException;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-520 / ADR-MONO-046 — the operator-group plane end-to-end against a real MySQL
 * (Flyway V0043 tables + V0044 group_origin marker + V0045 group.manage seed), the real RBAC
 * aspect, the real {@code TenantScopeGuard}/{@code RoleGrantGuard}, and the real fan-out
 * engine. Mirrors {@code OrgNodeAdminIntegrationTest}.
 *
 * <p>Covers AC-2 (full CRUD + members + grants against the contract), AC-3 (fan-out visible in
 * the substrate; idempotent skip on an equal direct grant; removal spares direct grants), and
 * the deny-default security slice (non-{@code group.manage} → 403). CI Linux is authoritative;
 * a local Windows run may be FLAKY/SKIPPED.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
class GroupAdminIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofMinutes(3));

    static OperatorJwtTestFixture jwt;
    static String signingKeyPem;
    static final ObjectMapper MAPPER = new ObjectMapper();

    // Task-scoped operator UUIDs — must not collide (case-insensitively) with any other IT.
    static final String SUPER_UUID = "00000000-0000-7000-8000-00000be05201";
    static final String TENANT_ADMIN_UUID = "00000000-0000-7000-8000-00000be05202";
    static final String PLAIN_UUID = "00000000-0000-7000-8000-00000be05203";
    static final String MEMBER1_UUID = "00000000-0000-7000-8000-00000be05204";
    static final String MEMBER2_UUID = "00000000-0000-7000-8000-00000be05205";

    @BeforeAll
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
        registry.add("admin.auth-service.base-url", () -> "http://localhost:18086");
        registry.add("admin.account-service.base-url", () -> "http://localhost:18086");
        registry.add("admin.security-service.base-url", () -> "http://localhost:18086");
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        GroupItSeeds.seedOperator(jdbcTemplate, SUPER_UUID, "*", "grp-super@example.com", "SUPER_ADMIN");
        GroupItSeeds.seedOperator(jdbcTemplate, TENANT_ADMIN_UUID, "acme", "grp-ta@example.com", "TENANT_ADMIN");
        GroupItSeeds.seedOperator(jdbcTemplate, PLAIN_UUID, "acme", "grp-plain@example.com", "SUPPORT_READONLY");
        GroupItSeeds.seedOperator(jdbcTemplate, MEMBER1_UUID, "acme", "grp-m1@example.com", null);
        GroupItSeeds.seedOperator(jdbcTemplate, MEMBER2_UUID, "acme", "grp-m2@example.com", null);
    }

    // ── deny-default (security slice, AC-7) ──────────────────────────────────────

    @Test
    @DisplayName("an operator without group.manage → 403 PERMISSION_DENIED on the group surface")
    void withoutGroupManage_denied() throws Exception {
        mockMvc.perform(get("/api/admin/groups").header("Authorization", bearer(PLAIN_UUID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    // ── full CRUD + members + grants + fan-out (AC-2, AC-3) ─────────────────────

    @Test
    @DisplayName("SUPER_ADMIN: create → grant role → add member fans out a group_origin row → delete cascade-revokes it")
    void fullLifecycle_fanOutVisibleInSubstrate() throws Exception {
        String groupId = createGroup(SUPER_UUID, "acme", "물류 지원팀", "squad");

        // rename
        mockMvc.perform(patch("/api/admin/groups/" + groupId)
                        .header("Authorization", bearer(SUPER_UUID))
                        .header("X-Operator-Reason", "rename")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"물류 지원팀 (개편)\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("물류 지원팀 (개편)"));

        // grant SUPPORT_LOCK to the group (no members yet → 0 fan-out rows)
        mockMvc.perform(post("/api/admin/groups/" + groupId + "/grants")
                        .header("Authorization", bearer(SUPER_UUID))
                        .header("X-Operator-Reason", "grant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"SUPPORT_LOCK\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.fannedOutRows").value(0));

        // add MEMBER1 → the group's current grant fans out onto it (1 row)
        mockMvc.perform(post("/api/admin/groups/" + groupId + "/members")
                        .header("Authorization", bearer(SUPER_UUID))
                        .header("X-Operator-Reason", "onboard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operatorId\":\"" + MEMBER1_UUID + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fannedOutGrants").value(1));

        // the fan-out row exists, tagged with group_origin (NOT a direct grant)
        assertThat(groupOriginRoleRows(MEMBER1_UUID, "SUPPORT_LOCK")).isEqualTo(1);

        mockMvc.perform(get("/api/admin/groups/" + groupId + "/members").header("Authorization", bearer(SUPER_UUID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].operatorId").value(MEMBER1_UUID));

        mockMvc.perform(get("/api/admin/groups/" + groupId + "/grants").header("Authorization", bearer(SUPER_UUID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].roleName").value("SUPPORT_LOCK"));

        // remove member → its group_origin row is revoked
        mockMvc.perform(delete("/api/admin/groups/" + groupId + "/members/" + MEMBER1_UUID)
                        .header("Authorization", bearer(SUPER_UUID))
                        .header("X-Operator-Reason", "offboard"))
                .andExpect(status().isNoContent());
        assertThat(groupOriginRoleRows(MEMBER1_UUID, "SUPPORT_LOCK")).isZero();

        // re-add then delete the whole group → cascade-revoke every group_origin row
        mockMvc.perform(post("/api/admin/groups/" + groupId + "/members")
                        .header("Authorization", bearer(SUPER_UUID))
                        .header("X-Operator-Reason", "re-onboard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operatorId\":\"" + MEMBER1_UUID + "\"}"))
                .andExpect(status().isCreated());
        assertThat(groupOriginRoleRows(MEMBER1_UUID, "SUPPORT_LOCK")).isEqualTo(1);

        mockMvc.perform(delete("/api/admin/groups/" + groupId)
                        .header("Authorization", bearer(SUPER_UUID))
                        .header("X-Operator-Reason", "disband"))
                .andExpect(status().isNoContent());
        assertThat(groupOriginRoleRows(MEMBER1_UUID, "SUPPORT_LOCK")).isZero();

        // group is gone
        mockMvc.perform(get("/api/admin/groups/" + groupId).header("Authorization", bearer(SUPER_UUID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));

        Integer audited = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_actions WHERE action_code = 'GROUP_DELETE' AND target_type = 'GROUP'",
                Integer.class);
        assertThat(audited).isPositive();
    }

    @Test
    @DisplayName("AC-3 idempotence: a member holding an equal DIRECT grant is skipped, and the direct grant survives removal")
    void idempotentSkip_directGrantSurvives() throws Exception {
        // MEMBER2 already holds a DIRECT SUPPORT_LOCK grant (group_origin IS NULL).
        jdbcTemplate.update("""
                INSERT INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
                SELECT o.id, r.id, o.tenant_id, NOW(6), NULL
                  FROM admin_operators o CROSS JOIN admin_roles r
                 WHERE o.operator_id = ? AND r.name = 'SUPPORT_LOCK'
                """, MEMBER2_UUID);

        String groupId = createGroup(SUPER_UUID, "acme", "지원팀 B", null);
        mockMvc.perform(post("/api/admin/groups/" + groupId + "/grants")
                        .header("Authorization", bearer(SUPER_UUID))
                        .header("X-Operator-Reason", "grant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"SUPPORT_LOCK\"]}"))
                .andExpect(status().isCreated());

        // add MEMBER2 → the equal direct grant means the fan-out is a no-op skip.
        mockMvc.perform(post("/api/admin/groups/" + groupId + "/members")
                        .header("Authorization", bearer(SUPER_UUID))
                        .header("X-Operator-Reason", "onboard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operatorId\":\"" + MEMBER2_UUID + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fannedOutGrants").value(0));

        // exactly one row, and it is the DIRECT grant (group_origin IS NULL) — not overwritten.
        assertThat(totalRoleRows(MEMBER2_UUID, "SUPPORT_LOCK")).isEqualTo(1);
        assertThat(groupOriginRoleRows(MEMBER2_UUID, "SUPPORT_LOCK")).isZero();

        // deleting the group must NOT destroy the member's direct grant.
        mockMvc.perform(delete("/api/admin/groups/" + groupId)
                        .header("Authorization", bearer(SUPER_UUID))
                        .header("X-Operator-Reason", "disband"))
                .andExpect(status().isNoContent());
        assertThat(totalRoleRows(MEMBER2_UUID, "SUPPORT_LOCK")).isEqualTo(1);
    }

    @Test
    @DisplayName("create: duplicate (tenant, name) → 409 GROUP_NAME_CONFLICT")
    void duplicateName_conflict() throws Exception {
        createGroup(SUPER_UUID, "acme", "중복팀", null);
        mockMvc.perform(post("/api/admin/groups")
                        .header("Authorization", bearer(SUPER_UUID))
                        .header("X-Operator-Reason", "dup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"acme\",\"name\":\"중복팀\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_NAME_CONFLICT"));
    }

    @Test
    @DisplayName("add member of a different tenant → 422 GROUP_MEMBER_TENANT_MISMATCH")
    void crossTenantMember_unprocessable() throws Exception {
        String groupId = createGroup(SUPER_UUID, "acme", "테넌트체크팀", null);
        // SUPER_UUID is a platform operator (tenant '*'); its home tenant differs from 'acme'.
        mockMvc.perform(post("/api/admin/groups/" + groupId + "/members")
                        .header("Authorization", bearer(SUPER_UUID))
                        .header("X-Operator-Reason", "mismatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operatorId\":\"" + SUPER_UUID + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("GROUP_MEMBER_TENANT_MISMATCH"));
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private String createGroup(String actorUuid, String tenantId, String name, String description) throws Exception {
        String body = description == null
                ? "{\"tenantId\":\"" + tenantId + "\",\"name\":\"" + name + "\"}"
                : "{\"tenantId\":\"" + tenantId + "\",\"name\":\"" + name + "\",\"description\":\"" + description + "\"}";
        MvcResult result = mockMvc.perform(post("/api/admin/groups")
                        .header("Authorization", bearer(actorUuid))
                        .header("X-Operator-Reason", "create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = MAPPER.readTree(result.getResponse().getContentAsString());
        return node.get("groupId").asText();
    }

    private int groupOriginRoleRows(String operatorUuid, String roleName) {
        Integer n = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM admin_operator_roles aor
                JOIN admin_operators o ON o.id = aor.operator_id
                JOIN admin_roles r ON r.id = aor.role_id
                WHERE o.operator_id = ? AND r.name = ? AND aor.group_origin IS NOT NULL
                """, Integer.class, operatorUuid, roleName);
        return n == null ? 0 : n;
    }

    private int totalRoleRows(String operatorUuid, String roleName) {
        Integer n = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM admin_operator_roles aor
                JOIN admin_operators o ON o.id = aor.operator_id
                JOIN admin_roles r ON r.id = aor.role_id
                WHERE o.operator_id = ? AND r.name = ?
                """, Integer.class, operatorUuid, roleName);
        return n == null ? 0 : n;
    }

    private String bearer(String operatorUuid) {
        return "Bearer " + jwt.operatorToken(operatorUuid);
    }
}
