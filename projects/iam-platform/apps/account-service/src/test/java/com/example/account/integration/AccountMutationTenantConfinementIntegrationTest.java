package com.example.account.integration;

import com.example.account.domain.account.Account;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.tenant.TenantId;
import com.example.account.infrastructure.outbox.AccountOutboxPublisher;
import com.example.testsupport.integration.AbstractIntegrationTest;
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
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-467 — admin account-mutation tenant confinement.
 *
 * <p>The admin mutation write-path stamps the actor's active tenant as
 * {@code X-Tenant-Id}; account-service resolves it via
 * {@link TenantId#fromHeaderOrDefault} and threads it into the tenant-scoped
 * {@code findById}. This IT is the AC-5 authority (the Docker-free {@code :test}
 * cannot exercise the header→repository wiring):
 *
 * <ul>
 *   <li><b>Same-tenant</b> ({@code X-Tenant-Id} = the account's tenant) → success.</li>
 *   <li><b>Cross-tenant</b> ({@code X-Tenant-Id} = a different tenant) →
 *       {@code 404 ACCOUNT_NOT_FOUND} (enumeration-safe; the account is NOT mutated).</li>
 *   <li><b>No tenant named</b> (no / {@code "*"} {@code X-Tenant-Id}) on {@code /lock} ·
 *       {@code /unlock} · {@code /delete} → the account's own tenant, from its row
 *       (TASK-MONO-735; until then this pinned to {@code fan-platform} and every account
 *       outside it was a 404). {@code fan-platform} accounts behave as before.</li>
 * </ul>
 */
@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Account mutation tenant confinement 통합 테스트 — TASK-BE-467")
class AccountMutationTenantConfinementIntegrationTest extends AbstractIntegrationTest {

    private static final String WMS_TENANT_ID = "wms";
    private static final String FAN_TENANT_ID = "fan-platform";
    private static final String ECOMMERCE_TENANT_ID = "ecommerce";

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AccountRepository accountRepository;

    @MockitoBean @SuppressWarnings("rawtypes") private KafkaTemplate kafkaTemplate;
    @MockitoBean private AccountOutboxPublisher accountOutboxPublisher;

    @BeforeEach
    void ensureWmsTenantExists() {
        jdbc.update("""
                INSERT IGNORE INTO tenants (tenant_id, display_name, tenant_type, status, created_at, updated_at)
                VALUES (?, 'Warehouse Management System', 'B2B_ENTERPRISE', 'ACTIVE', NOW(6), NOW(6))
                """, WMS_TENANT_ID);
        jdbc.update("""
                INSERT IGNORE INTO tenants (tenant_id, display_name, tenant_type, status, created_at, updated_at)
                VALUES (?, 'E-commerce', 'B2C_CONSUMER', 'ACTIVE', NOW(6), NOW(6))
                """, ECOMMERCE_TENANT_ID);
    }

    /** Seed an ACTIVE account directly under {@code tenantId} and return its id. */
    private String seedAccount(String tenantId) {
        Account account = Account.create(new TenantId(tenantId),
                "confine-" + UUID.randomUUID() + "@example.com");
        accountRepository.save(account);
        return account.getId();
    }

    private AccountStatus statusOf(String tenantId, String accountId) {
        return accountRepository.findById(new TenantId(tenantId), accountId).orElseThrow().getStatus();
    }

    private static final String LOCK_BODY = """
            {"reason":"ADMIN_LOCK","operatorId":"op-1"}""";

    /** The security-service auto-lock body (security-to-account.md). */
    private static final String AUTO_LOCK_BODY = """
            {"reason":"AUTO_DETECT","ruleCode":"TOKEN_REUSE","riskScore":90,"suspiciousEventId":"evt-1"}""";

    // ── LOCK ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("same-tenant lock (X-Tenant-Id=wms) → 200 + 계정 LOCKED")
    void lock_sameTenant_succeeds() throws Exception {
        String wmsAccountId = seedAccount(WMS_TENANT_ID);

        mockMvc.perform(post("/internal/accounts/{id}/lock", wmsAccountId)
                        .header("X-Tenant-Id", WMS_TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOCK_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.currentStatus").value("LOCKED"));

        assertThat(statusOf(WMS_TENANT_ID, wmsAccountId)).isEqualTo(AccountStatus.LOCKED);
    }

    @Test
    @DisplayName("cross-tenant lock (계정=wms, X-Tenant-Id=fan-platform) → 404 ACCOUNT_NOT_FOUND + 미변경")
    void lock_crossTenant_returns404_andDoesNotMutate() throws Exception {
        String wmsAccountId = seedAccount(WMS_TENANT_ID);

        mockMvc.perform(post("/internal/accounts/{id}/lock", wmsAccountId)
                        .header("X-Tenant-Id", FAN_TENANT_ID)  // wrong tenant
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOCK_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));

        // Enumeration-safe: the account still exists under wms and is untouched.
        assertThat(statusOf(WMS_TENANT_ID, wmsAccountId)).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("NET-ZERO: 헤더 없음 → fan-platform 기본값 → fan 계정 lock 200")
    void lock_noHeader_defaultsToFan_locksFanAccount() throws Exception {
        String fanAccountId = seedAccount(FAN_TENANT_ID);

        mockMvc.perform(post("/internal/accounts/{id}/lock", fanAccountId)
                        // no X-Tenant-Id → FAN_PLATFORM default (pre-BE-467 behavior)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOCK_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("LOCKED"));

        assertThat(statusOf(FAN_TENANT_ID, fanAccountId)).isEqualTo(AccountStatus.LOCKED);
    }

    // ── TASK-MONO-735: a caller that names no tenant gets the account's own tenant ──
    //
    // Replaces the BE-467 cell "헤더 없음 → fan 기본값이라 wms 계정은 404". That cell pinned
    // the defect: the header-less security-service auto-lock and the SUPER_ADMIN ('*') console
    // lock both returned 404 for an `ecommerce` account in the 2026-09-26 live window.

    @Test
    @DisplayName("TASK-MONO-735: 헤더 없음 → ecommerce 계정을 계정 행의 테넌트로 찾아 lock 200 + LOCKED")
    void lock_noHeader_resolvesAccountsOwnTenant_locksEcommerceAccount() throws Exception {
        String ecAccountId = seedAccount(ECOMMERCE_TENANT_ID);

        mockMvc.perform(post("/internal/accounts/{id}/lock", ecAccountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AUTO_LOCK_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.currentStatus").value("LOCKED"));

        assertThat(statusOf(ECOMMERCE_TENANT_ID, ecAccountId)).isEqualTo(AccountStatus.LOCKED);
    }

    @Test
    @DisplayName("TASK-MONO-735: X-Tenant-Id='*' (SUPER_ADMIN) → ecommerce 계정 lock 200 + LOCKED")
    void lock_wildcardHeader_resolvesAccountsOwnTenant_locksEcommerceAccount() throws Exception {
        String ecAccountId = seedAccount(ECOMMERCE_TENANT_ID);

        mockMvc.perform(post("/internal/accounts/{id}/lock", ecAccountId)
                        .header("X-Tenant-Id", "*")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOCK_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("LOCKED"));

        assertThat(statusOf(ECOMMERCE_TENANT_ID, ecAccountId)).isEqualTo(AccountStatus.LOCKED);
    }

    @Test
    @DisplayName("TASK-MONO-735 대조군: X-Tenant-Id=fan-platform + ecommerce 계정 → 404 + 미변경 (격리 유지)")
    void lock_concreteOtherTenantHeader_ecommerceAccount_returns404_andDoesNotMutate() throws Exception {
        String ecAccountId = seedAccount(ECOMMERCE_TENANT_ID);

        mockMvc.perform(post("/internal/accounts/{id}/lock", ecAccountId)
                        .header("X-Tenant-Id", FAN_TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AUTO_LOCK_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));

        assertThat(statusOf(ECOMMERCE_TENANT_ID, ecAccountId)).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("TASK-MONO-735: X-Tenant-Id=ecommerce (계정의 테넌트) → lock 200 — 테넌트를 아는 호출자의 명시 경로")
    void lock_concreteOwnTenantHeader_ecommerceAccount_locks() throws Exception {
        String ecAccountId = seedAccount(ECOMMERCE_TENANT_ID);

        mockMvc.perform(post("/internal/accounts/{id}/lock", ecAccountId)
                        .header("X-Tenant-Id", ECOMMERCE_TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AUTO_LOCK_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("LOCKED"));

        assertThat(statusOf(ECOMMERCE_TENANT_ID, ecAccountId)).isEqualTo(AccountStatus.LOCKED);
    }

    @Test
    @DisplayName("TASK-MONO-735: 헤더 없음 → 어느 테넌트에도 없는 id 는 여전히 404")
    void lock_noHeader_unknownId_returns404() throws Exception {
        mockMvc.perform(post("/internal/accounts/{id}/lock", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOCK_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    @DisplayName("TASK-MONO-735: 헤더 없음 → ecommerce 계정 unlock 200 · delete 202")
    void unlockAndDelete_noHeader_resolveAccountsOwnTenant() throws Exception {
        String ecAccountId = seedAccount(ECOMMERCE_TENANT_ID);
        mockMvc.perform(post("/internal/accounts/{id}/lock", ecAccountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOCK_BODY))
                .andExpect(status().isOk());

        mockMvc.perform(post("/internal/accounts/{id}/unlock", ecAccountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"ADMIN_UNLOCK","operatorId":"op-1"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("ACTIVE"));
        assertThat(statusOf(ECOMMERCE_TENANT_ID, ecAccountId)).isEqualTo(AccountStatus.ACTIVE);

        mockMvc.perform(post("/internal/accounts/{id}/delete", ecAccountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"ADMIN_DELETE","operatorId":"op-1"}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.currentStatus").value("DELETED"));
        assertThat(statusOf(ECOMMERCE_TENANT_ID, ecAccountId)).isEqualTo(AccountStatus.DELETED);
    }

    @Test
    @DisplayName("wildcard: X-Tenant-Id='*' → fan-platform 기본값(SUPER_ADMIN net-zero)")
    void lock_wildcardHeader_defaultsToFan() throws Exception {
        String fanAccountId = seedAccount(FAN_TENANT_ID);

        mockMvc.perform(post("/internal/accounts/{id}/lock", fanAccountId)
                        .header("X-Tenant-Id", "*")  // SUPER_ADMIN platform scope
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOCK_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("LOCKED"));

        assertThat(statusOf(FAN_TENANT_ID, fanAccountId)).isEqualTo(AccountStatus.LOCKED);
    }

    // ── GDPR DELETE ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("cross-tenant gdpr-delete → 404 + 미변경")
    void gdprDelete_crossTenant_returns404_andDoesNotMutate() throws Exception {
        String wmsAccountId = seedAccount(WMS_TENANT_ID);

        mockMvc.perform(post("/internal/accounts/{id}/gdpr-delete", wmsAccountId)
                        .header("X-Tenant-Id", FAN_TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"REGULATED_DELETION","operatorId":"op-1"}"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));

        assertThat(statusOf(WMS_TENANT_ID, wmsAccountId)).isEqualTo(AccountStatus.ACTIVE);
    }

    // ── EXPORT ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("same-tenant export → 200, cross-tenant export → 404")
    void export_confinedToTenant() throws Exception {
        String wmsAccountId = seedAccount(WMS_TENANT_ID);

        mockMvc.perform(get("/internal/accounts/{id}/export", wmsAccountId)
                        .header("X-Tenant-Id", WMS_TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(wmsAccountId));

        mockMvc.perform(get("/internal/accounts/{id}/export", wmsAccountId)
                        .header("X-Tenant-Id", FAN_TENANT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }
}
