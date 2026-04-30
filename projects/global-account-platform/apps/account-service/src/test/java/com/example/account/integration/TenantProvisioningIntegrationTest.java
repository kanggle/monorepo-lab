package com.example.account.integration;

import com.example.account.application.port.AuthServicePort;
import com.example.account.domain.account.Account;
import com.example.account.domain.history.AccountStatusHistoryEntry;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.AccountRoleRepository;
import com.example.account.domain.repository.AccountStatusHistoryRepository;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.status.StatusChangeReason;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("TenantProvisioning 통합 테스트 — TASK-BE-231")
class TenantProvisioningIntegrationTest extends AbstractIntegrationTest {

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
    @Autowired private AccountRepository accountRepository;
    @Autowired private AccountRoleRepository accountRoleRepository;
    @Autowired private AccountStatusHistoryRepository historyRepository;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private AuthServicePort authServicePort;
    @MockitoBean @SuppressWarnings("rawtypes") private KafkaTemplate kafkaTemplate;
    @MockitoBean private OutboxPollingScheduler outboxPollingScheduler;

    @BeforeEach
    void ensureWmsTenantExists() {
        // Insert wms tenant if not present (idempotent)
        jdbc.update("""
                INSERT IGNORE INTO tenants (tenant_id, display_name, tenant_type, status, created_at, updated_at)
                VALUES (?, 'Warehouse Management System', 'B2B_ENTERPRISE', 'ACTIVE', NOW(6), NOW(6))
                """, WMS_TENANT_ID);
    }

    // ── Create Account ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("WMS 토큰으로 자기 테넌트 사용자 생성 → 201 + DB에 저장 + outbox에 tenant_id 포함")
    void createAccount_wmsCallerOwnTenant_returns201() throws Exception {
        String email = "wms-user-" + UUID.randomUUID() + "@example.com";

        MvcResult result = mockMvc.perform(post("/internal/tenants/{tenantId}/accounts", WMS_TENANT_ID)
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .header("X-Tenant-Id", WMS_TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Password1!",
                                  "displayName": "홍길동",
                                  "roles": ["WAREHOUSE_ADMIN", "INBOUND_OPERATOR"]
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(WMS_TENANT_ID))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.roles").isArray())
                // Sensitive fields must not be present
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.deletedAt").doesNotExist())
                .andExpect(jsonPath("$.emailHash").doesNotExist())
                .andReturn();

        String accountId = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.accountId");

        // Verify account persisted with correct tenant_id
        Account saved = accountRepository.findById(new TenantId(WMS_TENANT_ID), accountId).orElseThrow();
        assertThat(saved.getTenantId().value()).isEqualTo(WMS_TENANT_ID);
        assertThat(saved.getEmail()).isEqualTo(email);
        assertThat(saved.getStatus()).isEqualTo(AccountStatus.ACTIVE);

        // Verify roles persisted
        var roles = accountRoleRepository.findByTenantIdAndAccountId(new TenantId(WMS_TENANT_ID), accountId);
        assertThat(roles).extracting(r -> r.getRoleName())
                .containsExactlyInAnyOrder("WAREHOUSE_ADMIN", "INBOUND_OPERATOR");

        // Verify outbox event contains tenant_id = wms
        List<String> outboxPayloads = jdbc.queryForList(
                "SELECT payload FROM outbox WHERE aggregate_id = ? AND event_type = 'account.created'",
                String.class, accountId);
        assertThat(outboxPayloads).hasSize(1);
        JsonNode payload = objectMapper.readTree(outboxPayloads.get(0));
        assertThat(payload.get("tenantId").asText()).isEqualTo(WMS_TENANT_ID);
    }

    @Test
    @DisplayName("WMS 토큰으로 다른 테넌트(fan-platform) path 호출 → 403 TENANT_SCOPE_DENIED")
    void createAccount_wmsCallerDifferentTenant_returns403() throws Exception {
        mockMvc.perform(post("/internal/tenants/{tenantId}/accounts", FAN_TENANT_ID)
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .header("X-Tenant-Id", WMS_TENANT_ID)  // WMS caller, fan-platform path
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "Password1!"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));
    }

    @Test
    @DisplayName("미등록 tenantId → 404 TENANT_NOT_FOUND")
    void createAccount_unregisteredTenant_returns404() throws Exception {
        mockMvc.perform(post("/internal/tenants/{tenantId}/accounts", "nonexistent-t")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .header("X-Tenant-Id", "nonexistent-t")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "Password1!"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"));
    }

    @Test
    @DisplayName("SUSPENDED tenantId → 409 TENANT_SUSPENDED")
    void createAccount_suspendedTenant_returns409() throws Exception {
        String suspendedTenantId = "suspended-t";
        jdbc.update("""
                INSERT IGNORE INTO tenants (tenant_id, display_name, tenant_type, status, created_at, updated_at)
                VALUES (?, 'Suspended Tenant', 'B2B_ENTERPRISE', 'SUSPENDED', NOW(6), NOW(6))
                """, suspendedTenantId);

        mockMvc.perform(post("/internal/tenants/{tenantId}/accounts", suspendedTenantId)
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .header("X-Tenant-Id", suspendedTenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "Password1!"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TENANT_SUSPENDED"));
    }

    @Test
    @DisplayName("cross-tenant unique: 동일 이메일을 fan-platform + wms 양쪽에 생성 가능")
    void crossTenantUnique_sameEmailTwoDifferentTenants_bothSucceed() throws Exception {
        String sharedEmail = "cross-tenant-" + UUID.randomUUID() + "@example.com";

        // Create in fan-platform via normal signup
        mockMvc.perform(post("/api/accounts/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Password1!"
                                }
                                """.formatted(sharedEmail)))
                .andExpect(status().isCreated());

        // Create in wms via provisioning — should succeed despite same email
        MvcResult wmsResult = mockMvc.perform(
                        post("/internal/tenants/{tenantId}/accounts", WMS_TENANT_ID)
                                .header("X-Internal-Token", INTERNAL_TOKEN)
                                .header("X-Tenant-Id", WMS_TENANT_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "email": "%s",
                                          "password": "Password1!"
                                        }
                                        """.formatted(sharedEmail)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(WMS_TENANT_ID))
                .andReturn();

        String wmsAccountId = com.jayway.jsonpath.JsonPath.read(
                wmsResult.getResponse().getContentAsString(), "$.accountId");

        // Verify both accounts exist in their respective tenants
        Account wmsAccount = accountRepository.findById(new TenantId(WMS_TENANT_ID), wmsAccountId).orElseThrow();
        assertThat(wmsAccount.getTenantId().value()).isEqualTo(WMS_TENANT_ID);

        // fan-platform account should also exist with same email but different tenant
        assertThat(accountRepository.findByEmail(new TenantId(FAN_TENANT_ID), sharedEmail)).isPresent();
        assertThat(accountRepository.findByEmail(new TenantId(WMS_TENANT_ID), sharedEmail)).isPresent();

        // Cross-tenant isolation: wms account not visible under fan-platform
        assertThat(accountRepository.findById(new TenantId(FAN_TENANT_ID), wmsAccountId)).isEmpty();
    }

    // ── Assign Roles ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("역할 교체 후 audit row에 OPERATOR_PROVISIONING_ROLES_REPLACE 코드가 기록된다")
    void assignRoles_success_auditRowRecorded() throws Exception {
        String email = "roles-test-" + UUID.randomUUID() + "@example.com";

        // Create account first
        MvcResult createResult = mockMvc.perform(
                        post("/internal/tenants/{tenantId}/accounts", WMS_TENANT_ID)
                                .header("X-Internal-Token", INTERNAL_TOKEN)
                                .header("X-Tenant-Id", WMS_TENANT_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "email": "%s",
                                          "password": "Password1!",
                                          "roles": ["WAREHOUSE_ADMIN"]
                                        }
                                        """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();

        String accountId = com.jayway.jsonpath.JsonPath.read(
                createResult.getResponse().getContentAsString(), "$.accountId");

        // Replace roles
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.tenantId").value(WMS_TENANT_ID));

        // Verify roles updated in DB
        var roles = accountRoleRepository.findByTenantIdAndAccountId(new TenantId(WMS_TENANT_ID), accountId);
        assertThat(roles).extracting(r -> r.getRoleName())
                .containsExactlyInAnyOrder("INBOUND_OPERATOR", "INVENTORY_VIEWER");

        // Verify audit row with OPERATOR_PROVISIONING_ROLES_REPLACE
        List<AccountStatusHistoryEntry> history = historyRepository.findByAccountIdOrderByOccurredAtDesc(accountId);
        assertThat(history).isNotEmpty();
        boolean hasRolesReplaceAudit = history.stream()
                .anyMatch(h -> h.getReasonCode() == StatusChangeReason.OPERATOR_PROVISIONING_ROLES_REPLACE);
        assertThat(hasRolesReplaceAudit).isTrue();
    }

    // ── Get account ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("단일 계정 조회 시 민감 필드 없음")
    void getAccount_noSensitiveFields() throws Exception {
        String email = "get-test-" + UUID.randomUUID() + "@example.com";

        MvcResult createResult = mockMvc.perform(
                        post("/internal/tenants/{tenantId}/accounts", WMS_TENANT_ID)
                                .header("X-Internal-Token", INTERNAL_TOKEN)
                                .header("X-Tenant-Id", WMS_TENANT_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "email": "%s",
                                          "password": "Password1!"
                                        }
                                        """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();

        String accountId = com.jayway.jsonpath.JsonPath.read(
                createResult.getResponse().getContentAsString(), "$.accountId");

        mockMvc.perform(get("/internal/tenants/{tenantId}/accounts/{accountId}",
                        WMS_TENANT_ID, accountId)
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .header("X-Tenant-Id", WMS_TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.tenantId").value(WMS_TENANT_ID))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.deletedAt").doesNotExist())
                .andExpect(jsonPath("$.emailHash").doesNotExist());
    }

    // ── Status change ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("상태 변경 ACTIVE→LOCKED 후 audit 로그에 OPERATOR_PROVISIONING_STATUS_CHANGE 기록")
    void changeStatus_activeToLocked_auditRecorded() throws Exception {
        String email = "status-test-" + UUID.randomUUID() + "@example.com";

        MvcResult createResult = mockMvc.perform(
                        post("/internal/tenants/{tenantId}/accounts", WMS_TENANT_ID)
                                .header("X-Internal-Token", INTERNAL_TOKEN)
                                .header("X-Tenant-Id", WMS_TENANT_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "email": "%s",
                                          "password": "Password1!"
                                        }
                                        """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();

        String accountId = com.jayway.jsonpath.JsonPath.read(
                createResult.getResponse().getContentAsString(), "$.accountId");

        mockMvc.perform(patch("/internal/tenants/{tenantId}/accounts/{accountId}/status",
                        WMS_TENANT_ID, accountId)
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .header("X-Tenant-Id", WMS_TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "LOCKED",
                                  "operatorId": "sys-wms"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.currentStatus").value("LOCKED"));

        // Verify account actually locked
        Account account = accountRepository.findById(new TenantId(WMS_TENANT_ID), accountId).orElseThrow();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.LOCKED);

        // Audit log must contain OPERATOR_PROVISIONING_STATUS_CHANGE
        List<AccountStatusHistoryEntry> history = historyRepository.findByAccountIdOrderByOccurredAtDesc(accountId);
        boolean hasStatusChangeAudit = history.stream()
                .anyMatch(h -> h.getReasonCode() == StatusChangeReason.OPERATOR_PROVISIONING_STATUS_CHANGE);
        assertThat(hasStatusChangeAudit).isTrue();
    }
}
