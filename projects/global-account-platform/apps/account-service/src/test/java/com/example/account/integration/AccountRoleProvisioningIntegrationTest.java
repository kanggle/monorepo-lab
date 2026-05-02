package com.example.account.integration;

import com.example.account.application.port.AuthServicePort;
import com.example.account.domain.account.AccountRole;
import com.example.account.domain.repository.AccountRoleRepository;
import com.example.account.domain.tenant.TenantId;
import com.example.messaging.outbox.OutboxPollingScheduler;
import com.example.testsupport.integration.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-255 integration coverage for the {@code account_roles} schema and the
 * provisioning role endpoints (replace-all, single-add, single-remove).
 *
 * <p>Validates:
 * <ul>
 *   <li>cross-tenant 404 regression — providing a foreign accountId surfaces as
 *       {@code ACCOUNT_NOT_FOUND}, not {@code TENANT_SCOPE_DENIED}.</li>
 *   <li>{@code account_roles} ON DELETE CASCADE — deleting an account row
 *       removes its role rows automatically.</li>
 *   <li>outbox payload of {@code account.roles.changed} carries the new
 *       {@code beforeRoles}, {@code afterRoles}, {@code changedBy} fields.</li>
 *   <li>idempotent add/remove behavior — repeating the operation does not
 *       generate additional outbox events.</li>
 * </ul>
 */
@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("AccountRoleProvisioning integration — TASK-BE-255")
class AccountRoleProvisioningIntegrationTest extends AbstractIntegrationTest {

    private static final String INTERNAL_TOKEN = "test-internal-token";
    private static final String WMS_TENANT_ID = "wms";
    private static final String FAN_TENANT_ID = "fan-platform";

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("internal.api.token", () -> INTERNAL_TOKEN);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AccountRoleRepository accountRoleRepository;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private AuthServicePort authServicePort;
    @MockitoBean @SuppressWarnings("rawtypes") private KafkaTemplate kafkaTemplate;
    @MockitoBean private OutboxPollingScheduler outboxPollingScheduler;

    @BeforeEach
    void ensureWmsTenantExists() {
        jdbc.update("""
                INSERT IGNORE INTO tenants (tenant_id, display_name, tenant_type, status, created_at, updated_at)
                VALUES (?, 'Warehouse Management System', 'B2B_ENTERPRISE', 'ACTIVE', NOW(6), NOW(6))
                """, WMS_TENANT_ID);
    }

    private String createAccount(String tenantId, String email, List<String> roles) throws Exception {
        StringBuilder rolesJson = new StringBuilder("[");
        for (int i = 0; i < roles.size(); i++) {
            if (i > 0) rolesJson.append(',');
            rolesJson.append('"').append(roles.get(i)).append('"');
        }
        rolesJson.append(']');

        MvcResult result = mockMvc.perform(post("/internal/tenants/{tenantId}/accounts", tenantId)
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Password1!",
                                  "roles": %s,
                                  "operatorId": "sys-test"
                                }
                                """.formatted(email, rolesJson.toString())))
                .andExpect(status().isCreated())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.accountId");
    }

    // ── Cross-tenant 404 regression ─────────────────────────────────────────────

    @Test
    @DisplayName("cross-tenant: tenantA 의 accountId 를 tenantB path 로 PATCH /roles → 404 ACCOUNT_NOT_FOUND")
    void replaceAll_crossTenantAccountId_returns404() throws Exception {
        // Create account in fan-platform via signup path (default tenant for self-signup).
        String email = "cross-" + UUID.randomUUID() + "@example.com";
        MvcResult signupResult = mockMvc.perform(post("/api/accounts/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Password1!"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        String fanAccountId = com.jayway.jsonpath.JsonPath.read(
                signupResult.getResponse().getContentAsString(), "$.accountId");

        // Try to assign roles to that account via WMS path — same tenant scope as caller.
        mockMvc.perform(patch("/internal/tenants/{tenantId}/accounts/{accountId}/roles",
                        WMS_TENANT_ID, fanAccountId)
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .header("X-Tenant-Id", WMS_TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roles": ["WAREHOUSE_ADMIN"]
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    // ── ON DELETE CASCADE ──────────────────────────────────────────────────────

    @Test
    @DisplayName("account 행 삭제 시 account_roles 가 ON DELETE CASCADE 로 함께 사라진다")
    void deleteAccount_cascadesRoles() throws Exception {
        String email = "cascade-" + UUID.randomUUID() + "@example.com";
        String accountId = createAccount(WMS_TENANT_ID, email,
                List.of("WAREHOUSE_ADMIN", "INBOUND_OPERATOR"));

        // Pre-condition: 2 role rows exist.
        Long preCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM account_roles WHERE tenant_id = ? AND account_id = ?",
                Long.class, WMS_TENANT_ID, accountId);
        assertThat(preCount).isEqualTo(2L);

        // Hard-delete the account row directly (bypasses status machine — ok for CASCADE assertion).
        jdbc.update("DELETE FROM accounts WHERE tenant_id = ? AND id = ?",
                WMS_TENANT_ID, accountId);

        // Post-condition: role rows are gone via cascade.
        Long postCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM account_roles WHERE tenant_id = ? AND account_id = ?",
                Long.class, WMS_TENANT_ID, accountId);
        assertThat(postCount).isZero();
    }

    // ── Outbox payload (replace-all) ────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /roles → outbox 페이로드에 beforeRoles / afterRoles / changedBy 포함")
    void replaceAll_outboxPayloadCarriesNewFields() throws Exception {
        String email = "outbox-replace-" + UUID.randomUUID() + "@example.com";
        String accountId = createAccount(WMS_TENANT_ID, email, List.of("WAREHOUSE_ADMIN"));

        // Replace
        mockMvc.perform(patch("/internal/tenants/{tenantId}/accounts/{accountId}/roles",
                        WMS_TENANT_ID, accountId)
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .header("X-Tenant-Id", WMS_TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roles": ["INBOUND_OPERATOR", "INVENTORY_VIEWER"],
                                  "operatorId": "sys-wms"
                                }
                                """))
                .andExpect(status().isOk());

        // Find the latest account.roles.changed outbox row for this account.
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT payload
                  FROM outbox
                 WHERE aggregate_id = ? AND event_type = 'account.roles.changed'
                 ORDER BY id DESC LIMIT 1
                """, accountId);
        JsonNode payload = objectMapper.readTree((String) row.get("payload"));

        assertThat(payload.get("tenantId").asText()).isEqualTo(WMS_TENANT_ID);
        // legacy alias
        assertThat(payload.get("roles").isArray()).isTrue();
        // TASK-BE-255 new fields
        List<String> beforeList = objectMapper.convertValue(payload.get("beforeRoles"), List.class);
        List<String> afterList = objectMapper.convertValue(payload.get("afterRoles"), List.class);
        assertThat(beforeList).containsExactly("WAREHOUSE_ADMIN");
        assertThat(afterList).containsExactlyInAnyOrder("INBOUND_OPERATOR", "INVENTORY_VIEWER");
        assertThat(payload.get("changedBy").asText()).isEqualTo("sys-wms");
    }

    // ── Single-role add ────────────────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /roles:add — 신규 추가 + outbox 발행")
    void addRole_newRole_emitsOutbox() throws Exception {
        String email = "add-" + UUID.randomUUID() + "@example.com";
        String accountId = createAccount(WMS_TENANT_ID, email, List.of("WAREHOUSE_ADMIN"));

        long outboxBefore = jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox
                 WHERE aggregate_id = ? AND event_type = 'account.roles.changed'
                """, Long.class, accountId);

        mockMvc.perform(patch("/internal/tenants/{tenantId}/accounts/{accountId}/roles:add",
                        WMS_TENANT_ID, accountId)
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .header("X-Tenant-Id", WMS_TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleName": "INBOUND_OPERATOR",
                                  "operatorId": "sys-wms"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.containsInAnyOrder(
                        "WAREHOUSE_ADMIN", "INBOUND_OPERATOR")));

        // DB state
        List<AccountRole> persisted = accountRoleRepository.findByTenantIdAndAccountId(
                new TenantId(WMS_TENANT_ID), accountId);
        assertThat(persisted).extracting(AccountRole::getRoleName)
                .containsExactlyInAnyOrder("WAREHOUSE_ADMIN", "INBOUND_OPERATOR");

        long outboxAfter = jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox
                 WHERE aggregate_id = ? AND event_type = 'account.roles.changed'
                """, Long.class, accountId);
        assertThat(outboxAfter).isEqualTo(outboxBefore + 1);
    }

    @Test
    @DisplayName("PATCH /roles:add — 멱등 호출 (이미 존재) → outbox 미발행")
    void addRole_alreadyAssigned_noOutbox() throws Exception {
        String email = "addidem-" + UUID.randomUUID() + "@example.com";
        String accountId = createAccount(WMS_TENANT_ID, email, List.of("WAREHOUSE_ADMIN"));

        long outboxBefore = jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox
                 WHERE aggregate_id = ? AND event_type = 'account.roles.changed'
                """, Long.class, accountId);

        mockMvc.perform(patch("/internal/tenants/{tenantId}/accounts/{accountId}/roles:add",
                        WMS_TENANT_ID, accountId)
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .header("X-Tenant-Id", WMS_TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleName": "WAREHOUSE_ADMIN"
                                }
                                """))
                .andExpect(status().isOk());

        long outboxAfter = jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox
                 WHERE aggregate_id = ? AND event_type = 'account.roles.changed'
                """, Long.class, accountId);
        assertThat(outboxAfter).isEqualTo(outboxBefore);  // no new event
    }

    // ── Single-role remove ─────────────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /roles:remove — 기존 role 제거 + outbox 발행")
    void removeRole_existingRole_emitsOutbox() throws Exception {
        String email = "rm-" + UUID.randomUUID() + "@example.com";
        String accountId = createAccount(WMS_TENANT_ID, email,
                List.of("WAREHOUSE_ADMIN", "INBOUND_OPERATOR"));

        mockMvc.perform(patch("/internal/tenants/{tenantId}/accounts/{accountId}/roles:remove",
                        WMS_TENANT_ID, accountId)
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .header("X-Tenant-Id", WMS_TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleName": "INBOUND_OPERATOR",
                                  "operatorId": "sys-wms"
                                }
                                """))
                .andExpect(status().isOk());

        List<AccountRole> persisted = accountRoleRepository.findByTenantIdAndAccountId(
                new TenantId(WMS_TENANT_ID), accountId);
        assertThat(persisted).extracting(AccountRole::getRoleName)
                .containsExactly("WAREHOUSE_ADMIN");

        // Outbox payload check — afterRoles must NOT contain the removed role.
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT payload FROM outbox
                 WHERE aggregate_id = ? AND event_type = 'account.roles.changed'
                 ORDER BY id DESC LIMIT 1
                """, accountId);
        JsonNode payload = objectMapper.readTree((String) row.get("payload"));
        List<String> afterList = objectMapper.convertValue(payload.get("afterRoles"), List.class);
        List<String> beforeList = objectMapper.convertValue(payload.get("beforeRoles"), List.class);
        assertThat(afterList).doesNotContain("INBOUND_OPERATOR");
        assertThat(beforeList).contains("INBOUND_OPERATOR");
        assertThat(payload.get("changedBy").asText()).isEqualTo("sys-wms");
    }

    @Test
    @DisplayName("PATCH /roles:remove — 미할당 role 제거 시도 → outbox 미발행 (멱등)")
    void removeRole_notAssigned_noOutbox() throws Exception {
        String email = "rmidem-" + UUID.randomUUID() + "@example.com";
        String accountId = createAccount(WMS_TENANT_ID, email, List.of("WAREHOUSE_ADMIN"));

        long outboxBefore = jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox
                 WHERE aggregate_id = ? AND event_type = 'account.roles.changed'
                """, Long.class, accountId);

        mockMvc.perform(patch("/internal/tenants/{tenantId}/accounts/{accountId}/roles:remove",
                        WMS_TENANT_ID, accountId)
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .header("X-Tenant-Id", WMS_TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleName": "INVENTORY_VIEWER"
                                }
                                """))
                .andExpect(status().isOk());

        long outboxAfter = jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox
                 WHERE aggregate_id = ? AND event_type = 'account.roles.changed'
                """, Long.class, accountId);
        assertThat(outboxAfter).isEqualTo(outboxBefore);
    }

    // ── Validation ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /roles:add — 규격 위반 role 이름 → 400 VALIDATION_ERROR")
    void addRole_invalidPattern_returns400() throws Exception {
        String email = "valid-" + UUID.randomUUID() + "@example.com";
        String accountId = createAccount(WMS_TENANT_ID, email, List.of());

        mockMvc.perform(patch("/internal/tenants/{tenantId}/accounts/{accountId}/roles:add",
                        WMS_TENANT_ID, accountId)
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .header("X-Tenant-Id", WMS_TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleName": "lowercase-role"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
