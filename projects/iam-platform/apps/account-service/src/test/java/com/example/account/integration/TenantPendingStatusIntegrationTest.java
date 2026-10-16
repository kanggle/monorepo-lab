package com.example.account.integration;

import com.example.account.application.port.AuthServicePort;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.tenant.TenantId;
import com.example.account.infrastructure.outbox.AccountOutboxPublisher;
import com.example.testsupport.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
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
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-773 S2 — the tenant «관리자 대기» state {@code PENDING} in account-service.
 *
 * <p>Contract: {@code specs/contracts/http/internal/admin-to-account.md} § «Tenant lifecycle
 * (/internal/tenants) — 773 변경분» · {@code specs/features/multi-tenancy.md} (status enum row).
 *
 * <ul>
 *   <li>{@code POST /internal/tenants} takes an optional {@code initialStatus}: {@code ACTIVE}
 *       (default — byte-identical to today) | {@code PENDING}. Anything else, {@code SUSPENDED}
 *       included → {@code 400 VALIDATION_ERROR}.</li>
 *   <li>{@code PATCH}: {@code PENDING → ACTIVE} and {@code PENDING → SUSPENDED} are allowed; a
 *       requested {@code status = PENDING} is {@code 400 VALIDATION_ERROR} from every state.</li>
 *   <li>{@code GET /internal/tenants?status=PENDING} filters.</li>
 *   <li>🔴 Every existing «ACTIVE only» reader keeps refusing — {@code Tenant.isActive()} is not
 *       changed, so a PENDING tenant gets today's {@code TENANT_SUSPENDED} (no new code) on signup,
 *       single provisioning and bulk provisioning. The HTTP status asserted is the one the code
 *       and {@code account-internal-provisioning.md} / {@code account-api.md} /
 *       {@code auth-to-account-social.md} carry (409).</li>
 * </ul>
 *
 * <p>Rows are seeded with raw SQL (the column is {@code VARCHAR(10)}, no CHECK — V0009), so the
 * suite reaches the assertion even before {@code TenantStatus} has the value. Every tenant this
 * class creates is deleted after each test: the containers are JVM-shared
 * ({@link AbstractIntegrationTest}) and a PENDING row left behind must not reach another class.
 */
@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("TASK-MONO-773 S2 — 테넌트 PENDING(관리자 대기) 통합 테스트")
class TenantPendingStatusIntegrationTest extends AbstractIntegrationTest {

    private static final String PREFIX = "p773-";

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AccountRepository accountRepository;

    @MockitoBean private AuthServicePort authServicePort;
    @MockitoBean @SuppressWarnings("rawtypes") private KafkaTemplate kafkaTemplate;
    @MockitoBean private AccountOutboxPublisher accountOutboxPublisher;

    @AfterEach
    void deleteTenantsCreatedHere() {
        List<String> ids = jdbc.queryForList(
                "SELECT tenant_id FROM tenants WHERE tenant_id LIKE ?", String.class, PREFIX + "%");
        for (String id : ids) {
            // account_status_history is append-only (DB triggers, V0004) and the FK from accounts
            // cascades into it, so a tenant whose control path created an account (activate →
            // provision) cannot be deleted — the same rule R__05 and the sibling ITs follow
            // ("Deliberately NO DELETE"). Leave that tenant, but never as PENDING: the shared
            // container would otherwise carry a non-ACTIVE row into other classes.
            Integer history = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM account_status_history WHERE tenant_id = ?", Integer.class, id);
            if (history != null && history > 0) {
                jdbc.update("UPDATE tenants SET status = 'ACTIVE' WHERE tenant_id = ?", id);
                continue;
            }
            // TASK-BE-639 measurement seeds a subscription (FK → tenants)
            jdbc.update("DELETE FROM tenant_domain_subscription WHERE tenant_id = ?", id);
            jdbc.update("DELETE FROM account_roles WHERE tenant_id = ?", id);
            jdbc.update("DELETE FROM profiles WHERE account_id IN (SELECT id FROM accounts WHERE tenant_id = ?)", id);
            jdbc.update("DELETE FROM account_outbox WHERE aggregate_id IN (SELECT id FROM accounts WHERE tenant_id = ?)", id);
            jdbc.update("DELETE FROM accounts WHERE tenant_id = ?", id);
            // born-unified identity (V0023: identities.tenant_id → tenants, accounts.identity_id → identities)
            jdbc.update("DELETE FROM identities WHERE tenant_id = ?", id);
            jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", id);
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private static String newTenantId() {
        return PREFIX + UUID.randomUUID().toString().substring(0, 8);
    }

    private void seedTenant(String tenantId, String status) {
        jdbc.update("""
                INSERT INTO tenants (tenant_id, display_name, tenant_type, status, created_at, updated_at)
                VALUES (?, 'Pending Test Tenant', 'B2B_ENTERPRISE', ?, NOW(6), NOW(6))
                """, tenantId, status);
    }

    private String rowStatus(String tenantId) {
        return jdbc.queryForObject("SELECT status FROM tenants WHERE tenant_id = ?", String.class, tenantId);
    }

    private int rowCount(String tenantId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM tenants WHERE tenant_id = ?", Integer.class, tenantId);
        return n == null ? 0 : n;
    }

    // ── POST /internal/tenants — initialStatus ───────────────────────────────────

    @Test
    @DisplayName("POST initialStatus=PENDING → 201 · 응답 status=PENDING · 행 status=PENDING")
    void create_withInitialStatusPending_persistsPending() throws Exception {
        String id = newTenantId();

        mockMvc.perform(post("/internal/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId": "%s", "displayName": "Pending Co", "tenantType": "B2B_ENTERPRISE",
                                 "initialStatus": "PENDING"}
                                """.formatted(id)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(id))
                .andExpect(jsonPath("$.status").value("PENDING"));

        assertThat(rowStatus(id)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("대조군 — POST initialStatus 생략 → 201 ACTIVE (지금 동작 그대로)")
    void create_withoutInitialStatus_staysActive() throws Exception {
        String id = newTenantId();

        mockMvc.perform(post("/internal/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId": "%s", "displayName": "Default Co", "tenantType": "B2B_ENTERPRISE"}
                                """.formatted(id)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(rowStatus(id)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("대조군 — POST initialStatus=ACTIVE → 201 ACTIVE")
    void create_withInitialStatusActive_isActive() throws Exception {
        String id = newTenantId();

        mockMvc.perform(post("/internal/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId": "%s", "displayName": "Active Co", "tenantType": "B2C_CONSUMER",
                                 "initialStatus": "ACTIVE"}
                                """.formatted(id)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(rowStatus(id)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("POST initialStatus=SUSPENDED → 400 VALIDATION_ERROR · 행 0")
    void create_withInitialStatusSuspended_isRejected() throws Exception {
        String id = newTenantId();

        mockMvc.perform(post("/internal/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId": "%s", "displayName": "Suspended Co", "tenantType": "B2B_ENTERPRISE",
                                 "initialStatus": "SUSPENDED"}
                                """.formatted(id)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(rowCount(id)).isZero();
    }

    @Test
    @DisplayName("POST initialStatus=알 수 없는 값 → 400 VALIDATION_ERROR · 행 0")
    void create_withUnknownInitialStatus_isRejected() throws Exception {
        String id = newTenantId();

        mockMvc.perform(post("/internal/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId": "%s", "displayName": "Bogus Co", "tenantType": "B2B_ENTERPRISE",
                                 "initialStatus": "BOGUS"}
                                """.formatted(id)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(rowCount(id)).isZero();
    }

    // ── GET ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /internal/tenants/{id} — PENDING 행을 읽는다 → 200 status=PENDING")
    void get_pendingTenant_readsPending() throws Exception {
        String id = newTenantId();
        seedTenant(id, "PENDING");

        mockMvc.perform(get("/internal/tenants/{tenantId}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(id))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /internal/tenants?status=PENDING → 200 · 대기 테넌트만")
    void list_filteredByPending_returnsOnlyPending() throws Exception {
        String pending = newTenantId();
        String active = newTenantId();
        seedTenant(pending, "PENDING");
        seedTenant(active, "ACTIVE");

        String body = mockMvc.perform(get("/internal/tenants")
                        .param("status", "PENDING")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> ids = com.jayway.jsonpath.JsonPath.read(body, "$.items[*].tenantId");
        List<String> statuses = com.jayway.jsonpath.JsonPath.read(body, "$.items[*].status");
        assertThat(ids).contains(pending).doesNotContain(active);
        assertThat(statuses).containsOnly("PENDING");
    }

    // ── PATCH transitions ───────────────────────────────────────────────────────

    @Test
    @DisplayName("PATCH PENDING → ACTIVE → 200 · 행 ACTIVE")
    void patch_pendingToActive_isAllowed() throws Exception {
        String id = newTenantId();
        seedTenant(id, "PENDING");

        mockMvc.perform(patch("/internal/tenants/{tenantId}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(rowStatus(id)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("PATCH PENDING → SUSPENDED (정리 · 생성 보상) → 200 · 행 SUSPENDED")
    void patch_pendingToSuspended_isAllowed() throws Exception {
        String id = newTenantId();
        seedTenant(id, "PENDING");

        mockMvc.perform(patch("/internal/tenants/{tenantId}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"SUSPENDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        assertThat(rowStatus(id)).isEqualTo("SUSPENDED");
    }

    @Test
    @DisplayName("PATCH displayName 만 — PENDING 은 그대로 PENDING")
    void patch_displayNameOnly_keepsPending() throws Exception {
        String id = newTenantId();
        seedTenant(id, "PENDING");

        mockMvc.perform(patch("/internal/tenants/{tenantId}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\": \"Renamed Co\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Renamed Co"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        assertThat(rowStatus(id)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("PATCH status=PENDING 은 요청 값이 될 수 없다 — ACTIVE·SUSPENDED·PENDING 에서 모두 400 · 행 불변")
    void patch_toPending_isRejectedFromEveryState() throws Exception {
        for (String from : List.of("ACTIVE", "SUSPENDED", "PENDING")) {
            String id = newTenantId();
            seedTenant(id, from);

            mockMvc.perform(patch("/internal/tenants/{tenantId}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"PENDING\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

            assertThat(rowStatus(id)).as("row status after PATCH PENDING from %s", from).isEqualTo(from);
        }
    }

    // ── ACTIVE-only readers keep refusing PENDING ───────────────────────────────

    @Test
    @DisplayName("🔴 PENDING 테넌트로 가입 → 409 TENANT_SUSPENDED · 계정 0 · 자격 증명 호출 0")
    void signup_intoPendingTenant_isRefused() throws Exception {
        String id = newTenantId();
        seedTenant(id, "PENDING");
        String email = "pending-signup-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/accounts/signup")
                        .header("X-Tenant-Id", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "Password1!"}
                                """.formatted(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TENANT_SUSPENDED"));

        assertThat(accountRepository.findByEmail(new TenantId(id), email)).isEmpty();
        assertThat(accountRepository.findByEmail(TenantId.CONSUMER_POOL, email)).isEmpty();
        verifyNoInteractions(authServicePort);
    }

    @Test
    @DisplayName("🔴 PENDING 테넌트에 개별 프로비저닝 → 409 TENANT_SUSPENDED · 대조군: 활성화 뒤 같은 요청 201")
    void provision_intoPendingTenant_isRefused_untilActivated() throws Exception {
        String id = newTenantId();
        seedTenant(id, "PENDING");
        String email = "pending-prov-" + UUID.randomUUID() + "@example.com";
        String body = """
                {"email": "%s", "password": "Password1!"}
                """.formatted(email);

        mockMvc.perform(post("/internal/tenants/{tenantId}/accounts", id)
                        .header("X-Tenant-Id", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TENANT_SUSPENDED"));
        assertThat(accountRepository.findByEmail(new TenantId(id), email)).isEmpty();

        // Control: the refusal is the PENDING state, not the request — activate, then the same call lands.
        mockMvc.perform(patch("/internal/tenants/{tenantId}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"ACTIVE\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/internal/tenants/{tenantId}/accounts", id)
                        .header("X-Tenant-Id", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(id));
    }

    @Test
    @DisplayName("🔴 PENDING 테넌트에 일괄 프로비저닝 → 409 TENANT_SUSPENDED · 계정 0")
    void bulkProvision_intoPendingTenant_isRefused() throws Exception {
        String id = newTenantId();
        seedTenant(id, "PENDING");
        String email = "pending-bulk-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/internal/tenants/{tenantId}/accounts:bulk", id)
                        .header("X-Tenant-Id", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "items": [ { "externalId": "ext-1", "email": "%s", "displayName": "U1",
                                               "roles": ["WAREHOUSE_ADMIN"], "status": "ACTIVE" } ] }
                                """.formatted(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TENANT_SUSPENDED"));

        assertThat(accountRepository.findByEmail(new TenantId(id), email)).isEmpty();
    }

    // ── TASK-BE-639 AC-0 measurement — what an assumed token's entitlement reads ─────

    /**
     * 🔵 A MEASUREMENT pin (TASK-BE-639 AC-0), not a requirement. auth-service fills an operator assumed token's
     * {@code entitled_domains} (and through it the role ceiling) from this endpoint. It reads the tenant's ACTIVE
     * subscriptions and never the tenant's own status, so a non-ACTIVE tenant with a subscription still yields a
     * non-empty entitlement. Flips with the owner's BE-639 decision if that decision closes it here.
     */
    @org.junit.jupiter.params.ParameterizedTest(name = "tenant status {0} → entitled-domains still [wms]")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"PENDING", "SUSPENDED", "ACTIVE"})
    @DisplayName("🔵 측정(TASK-BE-639 AC-0): entitled-domains 는 테넌트 상태를 안 읽는다 — PENDING · SUSPENDED 도 구독 그대로")
    void measure_entitledDomains_ignoresTenantStatus(String tenantStatus) throws Exception {
        String id = newTenantId();
        seedTenant(id, tenantStatus);
        jdbc.update("""
                INSERT INTO tenant_domain_subscription (tenant_id, domain_key, status, created_at, updated_at)
                VALUES (?, 'wms', 'ACTIVE', NOW(6), NOW(6))""", id);

        mockMvc.perform(get("/internal/tenants/{id}/entitled-domains", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.domainKeys[0]").value("wms"));
    }
}
