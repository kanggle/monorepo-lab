package com.example.account.integration;

import com.example.account.application.port.AuthServicePort;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.tenant.TenantId;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-614 (ADR-MONO-078 A) — the consumer-account pool against a real MySQL with
 * {@code iam.consumer-pool.enabled=true}: signup into the pool (AC-3), the {@code account.created}
 * row (AC-10), the operator-faceted refusal (AC-6), the site-scoped lookups (AC-9) and what tenant
 * isolation now guarantees for pool accounts (AC-5).
 *
 * <p>auth-service is mocked at the port (as in {@code AccountSignupIntegrationTest}); the
 * credential's tenant is read off the mock.
 */
@DisplayName("TASK-BE-614 — 소비자 계정 풀 가입 · 조회 · 이벤트 (MySQL, 플래그 켜짐)")
class ConsumerPoolSignupIntegrationTest extends AbstractConsumerPoolIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AccountRepository accountRepository;

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private MvcResult signup(String siteTenant, String email, int expectedStatus) throws Exception {
        return mockMvc.perform(post("/api/accounts/signup")
                        .header("X-Tenant-Id", siteTenant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "Password1!"}
                                """.formatted(email)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    private String signupAccountId(String siteTenant, String email) throws Exception {
        return JsonPath.read(signup(siteTenant, email, 201).getResponse().getContentAsString(), "$.accountId");
    }

    /** Every account id the site list returns, across all pages (the shared DB holds other tests' rows). */
    private List<String> siteListIds(String siteTenant) throws Exception {
        List<String> ids = new ArrayList<>();
        int page = 0;
        int totalPages;
        do {
            String body = mockMvc.perform(get("/internal/tenants/" + siteTenant + "/accounts")
                            .param("page", String.valueOf(page)).param("size", "100"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            ids.addAll(JsonPath.read(body, "$.content[*].accountId"));
            totalPages = JsonPath.read(body, "$.totalPages");
            page++;
        } while (page < totalPages);
        return ids;
    }

    private List<Map<String, Object>> createdEventRows(String accountId) {
        return jdbc.queryForList(
                "SELECT payload FROM account_outbox WHERE aggregate_id = ? AND event_type = 'account.created'",
                accountId);
    }

    // ── AC-3 · AC-10 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-3/AC-10: 스토어 가입 → consumer-pool 계정 + ecommerce 멤버십 + 자격은 풀 + account.created(ecommerce) 1회")
    void storeSignup_bornInPool() throws Exception {
        String email = uniqueEmail("pool-shopper");

        String accountId = signupAccountId("ecommerce", email);

        assertThat(jdbc.queryForObject("SELECT tenant_id FROM accounts WHERE id = ?", String.class, accountId))
                .isEqualTo("consumer-pool");
        assertThat(jdbc.queryForList(
                "SELECT site_tenant_id, status FROM consumer_site_memberships WHERE account_id = ?", accountId))
                .containsExactly(Map.of("site_tenant_id", "ecommerce", "status", "ACTIVE"));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE email = ? AND tenant_id = 'ecommerce'", Integer.class, email))
                .as("no per-site ecommerce row is created alongside the pool account")
                .isZero();
        verify(authServicePort).createCredential(eq(accountId), eq(email), eq("Password1!"),
                eq("consumer-pool"), any());

        List<Map<String, Object>> events = createdEventRows(accountId);
        assertThat(events).as("account.created once, for the signup site").hasSize(1);
        String tenantInEvent = JsonPath.read((String) events.get(0).get("payload"), "$.tenantId");
        assertThat(tenantInEvent).isEqualTo("ecommerce").isNotEqualTo("consumer-pool");
    }

    @Test
    @DisplayName("AC-3 / § 2: 같은 이메일의 팬 사이트 계정이 있으면 스토어 풀 가입은 409 — 묶이지도, 풀 계정이 생기지도 않는다")
    void emailWithSiteAccount_isRefused_notPaired() throws Exception {
        String email = uniqueEmail("legacy-fan");
        String fanAccountId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO accounts (id, tenant_id, email, status, created_at, updated_at, version) "
                + "VALUES (?, 'fan-platform', ?, 'ACTIVE', NOW(6), NOW(6), 0)", fanAccountId, email);

        mockMvc.perform(post("/api/accounts/signup")
                        .header("X-Tenant-Id", "ecommerce")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "Password1!"}
                                """.formatted(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_ALREADY_EXISTS"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM accounts WHERE email = ?", Integer.class, email))
                .as("only the original fan account — no pool account, nothing merged into it")
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM consumer_site_memberships WHERE account_id = ?", Integer.class, fanAccountId))
                .as("the existing site account is not silently paired with the pool (ADR-MONO-078 D2)")
                .isZero();
        verify(authServicePort, never()).createCredential(any(), eq(email), any(), any(), any());
    }

    // ── AC-6 ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-6: 셀러 계정(ecommerce · SELLER)이 있는 이메일로 팬 풀 가입 → 거절, 셀러 계정은 그대로")
    void sellerEmail_fanPoolSignup_refused_sellerUntouched() throws Exception {
        String email = uniqueEmail("seller");
        String sellerId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO accounts (id, tenant_id, email, status, created_at, updated_at, version) "
                + "VALUES (?, 'ecommerce', ?, 'ACTIVE', NOW(6), NOW(6), 0)", sellerId, email);
        jdbc.update("INSERT INTO account_roles (tenant_id, account_id, role_name, granted_by, granted_at) "
                + "VALUES ('ecommerce', ?, 'SELLER', NULL, NOW(6))", sellerId);

        mockMvc.perform(post("/api/accounts/signup")
                        .header("X-Tenant-Id", "fan-platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "Password1!"}
                                """.formatted(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_ALREADY_EXISTS"));

        assertThat(jdbc.queryForMap("SELECT tenant_id, status FROM accounts WHERE id = ?", sellerId))
                .containsEntry("tenant_id", "ecommerce").containsEntry("status", "ACTIVE");
        assertThat(jdbc.queryForList("SELECT role_name FROM account_roles WHERE account_id = ?", String.class, sellerId))
                .containsExactly("SELLER");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE email = ? AND tenant_id = 'consumer-pool'", Integer.class, email))
                .isZero();
        // the seller's credential is never touched — no credential write happens for this email
        verify(authServicePort, never()).createCredential(any(), eq(email), any(), any(), any());
    }

    // ── AC-9 · AC-5 ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-9: ecommerce 목록·이메일 검색·단건에 풀 가입 쇼핑객이 나오고, 풀 가입 팬은 나오지 않는다(대조군)")
    void siteLookups_includeMembers_excludeNonMembers() throws Exception {
        String shopperEmail = uniqueEmail("ac9-shopper");
        String fanEmail = uniqueEmail("ac9-fan");
        String shopperId = signupAccountId("ecommerce", shopperEmail);
        String fanId = signupAccountId("fan-platform", fanEmail);

        List<String> ecommerceList = siteListIds("ecommerce");
        assertThat(ecommerceList).contains(shopperId).doesNotContain(fanId);
        List<String> fanList = siteListIds("fan-platform");
        assertThat(fanList).contains(fanId).doesNotContain(shopperId);

        // the search admin-service uses for the console AND for CreateOperatorUseCase
        mockMvc.perform(get("/internal/accounts").param("tenantId", "ecommerce").param("email", shopperEmail))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(shopperId));
        mockMvc.perform(get("/internal/accounts").param("tenantId", "ecommerce").param("email", fanEmail))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/internal/tenants/ecommerce/accounts/" + shopperId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/internal/tenants/ecommerce/accounts/" + fanId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AC-5: 풀 계정은 멤버가 아닌 사이트 · B2B 테넌트 · 탈퇴(LEFT)한 사이트에서 보이지 않는다")
    void poolAccount_doesNotLeak() throws Exception {
        String email = uniqueEmail("ac5-pool");
        String poolId = signupAccountId("fan-platform", email);

        // tenant-scoped repository reads stay strict: a pool account is not a fan-platform row
        assertThat(accountRepository.findById(TenantId.FAN_PLATFORM, poolId)).isEmpty();
        assertThat(accountRepository.findById(new TenantId("ecommerce"), poolId)).isEmpty();
        assertThat(accountRepository.findByIdInSiteIncludingPoolMembers(new TenantId("ecommerce"), poolId))
                .as("member of fan-platform only").isEmpty();
        assertThat(accountRepository.findByIdInSiteIncludingPoolMembers(TenantId.FAN_PLATFORM, poolId))
                .isPresent();

        // a non-consumer tenant never sees it (erp: B2B, seeded by V0018; wms is avoided because
        // the JPA slice tests delete and re-create that row in the shared container)
        assertThat(siteListIds("erp")).doesNotContain(poolId);
        mockMvc.perform(get("/internal/accounts").param("tenantId", "erp").param("email", email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // leaving the site removes it from that site's surfaces
        jdbc.update("UPDATE consumer_site_memberships SET status = 'LEFT' "
                + "WHERE account_id = ? AND site_tenant_id = 'fan-platform'", poolId);
        assertThat(siteListIds("fan-platform")).doesNotContain(poolId);
        mockMvc.perform(get("/internal/accounts").param("tenantId", "fan-platform").param("email", email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("consumer-pool 을 X-Tenant-Id 로 지명한 가입은 행이 생기기 전과 같은 응답(TenantNotFound) — 풀 계정은 만들어지지 않는다")
    void namingThePoolTenant_isRefusedAsBefore() throws Exception {
        String email = uniqueEmail("named-pool");

        MvcResult result = signup("consumer-pool", email, 404);

        assertThat(result.getResponse().getContentAsString()).contains("TENANT_NOT_FOUND");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM accounts WHERE email = ?", Integer.class, email))
                .isZero();
    }
}
