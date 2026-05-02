package com.example.account.integration;

import com.example.account.application.port.AuthServicePort;
import com.example.account.domain.repository.AccountRepository;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for POST /internal/tenants/{tenantId}/accounts:bulk — TASK-BE-257.
 *
 * <p>Uses the shared {@link AbstractIntegrationTest} Testcontainers base (MySQL + Kafka).
 * AuthServicePort is mocked so credential creation is skipped at the integration boundary.
 */
@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("BulkProvisioning 통합 테스트 — TASK-BE-257")
class BulkProvisioningIntegrationTest extends AbstractIntegrationTest {

    private static final String INTERNAL_TOKEN = "test-internal-token";
    private static final String WMS_TENANT_ID = "wms-bulk-test";
    private static final String OTHER_TENANT_ID = "other-bulk-test";

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("internal.api.token", () -> INTERNAL_TOKEN);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AccountRoleRepository accountRoleRepository;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private AuthServicePort authServicePort;
    @MockitoBean @SuppressWarnings("rawtypes") private KafkaTemplate kafkaTemplate;
    @MockitoBean private OutboxPollingScheduler outboxPollingScheduler;

    @BeforeEach
    void ensureTenantsExist() {
        jdbc.update("""
                INSERT IGNORE INTO tenants (tenant_id, display_name, tenant_type, status, created_at, updated_at)
                VALUES (?, 'WMS Bulk Test', 'B2B_ENTERPRISE', 'ACTIVE', NOW(6), NOW(6))
                """, WMS_TENANT_ID);
        jdbc.update("""
                INSERT IGNORE INTO tenants (tenant_id, display_name, tenant_type, status, created_at, updated_at)
                VALUES (?, 'Other Bulk Test', 'B2B_ENTERPRISE', 'ACTIVE', NOW(6), NOW(6))
                """, OTHER_TENANT_ID);
    }

    // ── 정상 케이스 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("5건 정상 생성 → 200 + created=5 + outbox 5건 + audit 1건")
    void bulkCreate_5Items_allSucceed() throws Exception {
        List<String> emails = new ArrayList<>();
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            String email = "bulk-ok-" + UUID.randomUUID() + "@example.com";
            emails.add(email);
            if (i > 0) items.append(",");
            items.append("""
                    { "externalId": "ext-%d", "email": "%s", "displayName": "User %d",
                      "roles": ["WAREHOUSE_ADMIN"], "status": "ACTIVE" }
                    """.formatted(i, email, i));
        }

        MvcResult result = mockMvc.perform(post("/internal/tenants/{tenantId}/accounts:bulk", WMS_TENANT_ID)
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .header("X-Tenant-Id", WMS_TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"items\": [" + items + "] }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.requested").value(5))
                .andExpect(jsonPath("$.summary.created").value(5))
                .andExpect(jsonPath("$.summary.failed").value(0))
                .andExpect(jsonPath("$.created").isArray())
                .andExpect(jsonPath("$.failed").isArray())
                .andReturn();

        // Verify accounts persisted with correct tenant_id
        JsonNode responseJson = objectMapper.readTree(result.getResponse().getContentAsString());
        for (JsonNode createdEntry : responseJson.get("created")) {
            String accountId = createdEntry.get("accountId").asText();
            assertThat(accountRepository.findById(new TenantId(WMS_TENANT_ID), accountId)).isPresent();
        }

        // Verify outbox events: 5 account.created events
        for (String email : emails) {
            String accountId = jdbc.queryForObject(
                    "SELECT id FROM accounts WHERE email = ? AND tenant_id = ?",
                    String.class, email, WMS_TENANT_ID);
            List<String> outboxPayloads = jdbc.queryForList(
                    "SELECT payload FROM outbox WHERE aggregate_id = ? AND event_type = 'account.created'",
                    String.class, accountId);
            assertThat(outboxPayloads).hasSize(1);
            JsonNode payload = objectMapper.readTree(outboxPayloads.get(0));
            assertThat(payload.get("tenantId").asText()).isEqualTo(WMS_TENANT_ID);
        }

        // Verify one audit row for the entire bulk call
        int auditRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM account_status_history WHERE tenant_id = ? AND details LIKE '%ACCOUNT_BULK_CREATE%'",
                Integer.class, WMS_TENANT_ID);
        assertThat(auditRows).isEqualTo(1);
    }

    // ── 부분 실패 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("5건 중 2건 email 중복 → created=3, failed=2, EMAIL_DUPLICATE")
    void bulkCreate_partialFailure_emailDuplicate() throws Exception {
        // Pre-insert 2 accounts to simulate email duplicates
        String dupEmail1 = "bulk-dup1-" + UUID.randomUUID() + "@example.com";
        String dupEmail2 = "bulk-dup2-" + UUID.randomUUID() + "@example.com";

        // Create duplicates via single provisioning endpoint first
        provisionSingle(WMS_TENANT_ID, dupEmail1);
        provisionSingle(WMS_TENANT_ID, dupEmail2);

        String newEmail1 = "bulk-new1-" + UUID.randomUUID() + "@example.com";
        String newEmail2 = "bulk-new2-" + UUID.randomUUID() + "@example.com";
        String newEmail3 = "bulk-new3-" + UUID.randomUUID() + "@example.com";

        String requestBody = """
                {
                  "items": [
                    { "externalId": "dup-1", "email": "%s" },
                    { "externalId": "new-1", "email": "%s" },
                    { "externalId": "dup-2", "email": "%s" },
                    { "externalId": "new-2", "email": "%s" },
                    { "externalId": "new-3", "email": "%s" }
                  ]
                }
                """.formatted(dupEmail1, newEmail1, dupEmail2, newEmail2, newEmail3);

        mockMvc.perform(post("/internal/tenants/{tenantId}/accounts:bulk", WMS_TENANT_ID)
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .header("X-Tenant-Id", WMS_TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.requested").value(5))
                .andExpect(jsonPath("$.summary.created").value(3))
                .andExpect(jsonPath("$.summary.failed").value(2))
                .andExpect(jsonPath("$.failed[0].errorCode").value("EMAIL_DUPLICATE"))
                .andExpect(jsonPath("$.failed[1].errorCode").value("EMAIL_DUPLICATE"));
    }

    // ── tenant scope violation ────────────────────────────────────────────────

    @Test
    @DisplayName("X-Tenant-Id != path tenantId → 403 TENANT_SCOPE_DENIED")
    void bulkCreate_crossTenant_returns403() throws Exception {
        mockMvc.perform(post("/internal/tenants/{tenantId}/accounts:bulk", WMS_TENANT_ID)
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .header("X-Tenant-Id", OTHER_TENANT_ID)    // different from path
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "items": [{ "email": "user@example.com" }] }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));
    }

    // ── 1001건 → 400 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("1001건 요청 → 400 BULK_LIMIT_EXCEEDED — TASK-BE-271 (contract 정합)")
    void bulkCreate_1001Items_returns400() throws Exception {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < 1001; i++) {
            if (i > 0) items.append(",");
            items.append("{\"email\":\"user").append(i).append("@example.com\"}");
        }

        mockMvc.perform(post("/internal/tenants/{tenantId}/accounts:bulk", WMS_TENANT_ID)
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .header("X-Tenant-Id", WMS_TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"items\": [" + items + "] }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BULK_LIMIT_EXCEEDED"));
    }

    // ── 빈 배열 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("빈 items 배열 → 200 + 빈 결과")
    void bulkCreate_emptyItems_returns200EmptyResult() throws Exception {
        mockMvc.perform(post("/internal/tenants/{tenantId}/accounts:bulk", WMS_TENANT_ID)
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .header("X-Tenant-Id", WMS_TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"items\": [] }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.requested").value(0))
                .andExpect(jsonPath("$.summary.created").value(0))
                .andExpect(jsonPath("$.summary.failed").value(0));
    }

    // ── roles 생성 검증 ───────────────────────────────────────────────────────

    @Test
    @DisplayName("roles 포함 생성 → account_roles 테이블에 저장됨")
    void bulkCreate_withRoles_rolesPersistedInDb() throws Exception {
        String email = "bulk-role-" + UUID.randomUUID() + "@example.com";

        MvcResult result = mockMvc.perform(post("/internal/tenants/{tenantId}/accounts:bulk", WMS_TENANT_ID)
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .header("X-Tenant-Id", WMS_TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {
                                      "externalId": "role-test",
                                      "email": "%s",
                                      "roles": ["WAREHOUSE_ADMIN", "INBOUND_OPERATOR"]
                                    }
                                  ]
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.created").value(1))
                .andReturn();

        JsonNode responseJson = objectMapper.readTree(result.getResponse().getContentAsString());
        String accountId = responseJson.get("created").get(0).get("accountId").asText();

        var roles = accountRoleRepository.findByTenantIdAndAccountId(new TenantId(WMS_TENANT_ID), accountId);
        assertThat(roles).extracting(r -> r.getRoleName())
                .containsExactlyInAnyOrder("WAREHOUSE_ADMIN", "INBOUND_OPERATOR");
    }

    // ── 미등록 테넌트 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("미등록 테넌트 → 404 TENANT_NOT_FOUND")
    void bulkCreate_unregisteredTenant_returns404() throws Exception {
        mockMvc.perform(post("/internal/tenants/{tenantId}/accounts:bulk", "nonexistent-bulk")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .header("X-Tenant-Id", "nonexistent-bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "items": [{ "email": "user@example.com" }] }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private void provisionSingle(String tenantId, String email) throws Exception {
        mockMvc.perform(post("/internal/tenants/{tenantId}/accounts", tenantId)
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Password1!"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated());
    }
}
