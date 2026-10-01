package com.example.account.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-616 against a real MySQL — the account-service half of «store pool signup → fan first visit
 * → consent» (AC-6; the auth-service half, through the browser to the fan token, is
 * {@code ConsumerPoolSsoIntegrationTest#storeSignup_fanFirstVisit_consent_fanToken} with this service
 * as WireMock):
 * <ul>
 *   <li>{@code PUT /internal/tenants/fan-platform/consumer-members/{id}} turns «no membership» into an
 *       ACTIVE fan membership with {@code consented_at}, and publishes {@code account.created} with
 *       {@code tenantId = fan-platform} — the second created event of that one account (store at signup,
 *       fan at consent), never {@code consumer-pool} (multi-tenancy.md § 소비자 계정 풀 § 6).</li>
 *   <li>Idempotent: a second consent writes nothing and publishes nothing.</li>
 *   <li>No role row is written by consent (the FAN seed is computed at issuance, never stored).</li>
 *   <li>Nothing for a B2B tenant.</li>
 * </ul>
 * A subclass of {@link AbstractConsumerPoolIntegrationTest} on purpose — it shares that one context
 * (TASK-BE-615 «Too many connections» lesson).
 */
@DisplayName("TASK-BE-616 — 첫 방문 동의 쓰기 (MySQL, 풀 켜짐)")
class ConsumerSiteConsentIntegrationTest extends AbstractConsumerPoolIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    private String storePoolSignup(String email) throws Exception {
        String body = mockMvc.perform(post("/api/accounts/signup")
                        .header("X-Tenant-Id", "ecommerce")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "Password1!"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accountId");
    }

    private List<String> createdEventTenants(String accountId) {
        return jdbc.queryForList(
                        "SELECT payload FROM account_outbox WHERE aggregate_id = ? AND event_type = 'account.created' "
                                + "ORDER BY created_at", String.class, accountId)
                .stream().map(p -> (String) JsonPath.read(p, "$.tenantId")).toList();
    }

    @Test
    @DisplayName("AC-6 계정 쪽: 스토어 풀 가입 → 팬 멤버십 없음 → 동의(PUT) → 팬 ACTIVE · account.created(fan-platform) · 재동의는 무변화")
    void storeSignup_thenFanConsent_membershipAndEventOnce() throws Exception {
        String accountId = storePoolSignup("616-store-then-fan-" + UUID.randomUUID() + "@example.com");

        // Before consent: the fan site sees no membership (the token endpoint would refuse).
        mockMvc.perform(get("/internal/tenants/fan-platform/consumer-members/" + accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipStatus").doesNotExist());
        assertThat(createdEventTenants(accountId)).containsExactly("ecommerce");

        mockMvc.perform(put("/internal/tenants/fan-platform/consumer-members/" + accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consumerSite").value(true))
                .andExpect(jsonPath("$.membershipStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.siteRoles").isEmpty());

        List<Map<String, Object>> fanRow = jdbc.queryForList(
                "SELECT status, consented_at FROM consumer_site_memberships "
                        + "WHERE account_id = ? AND site_tenant_id = 'fan-platform'", accountId);
        assertThat(fanRow).hasSize(1);
        assertThat(fanRow.get(0).get("status")).isEqualTo("ACTIVE");
        assertThat(fanRow.get(0).get("consented_at")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT tenant_id FROM accounts WHERE id = ?", String.class, accountId))
                .as("still one pool account — consent creates no per-site account")
                .isEqualTo("consumer-pool");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM consumer_site_roles WHERE account_id = ?", Integer.class, accountId))
                .as("Failure Scenario 1: no role row is seeded by consent — FAN is computed at issuance")
                .isZero();
        assertThat(createdEventTenants(accountId))
                .as("account.created once per site: store at signup, fan at consent — never consumer-pool")
                .containsExactly("ecommerce", "fan-platform");

        // Idempotent: a double submit / back-button replay writes and publishes nothing.
        mockMvc.perform(put("/internal/tenants/fan-platform/consumer-members/" + accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipStatus").value("ACTIVE"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consumer_site_memberships WHERE account_id = ?",
                Integer.class, accountId)).isEqualTo(2);
        assertThat(createdEventTenants(accountId)).containsExactly("ecommerce", "fan-platform");

        // The read the token issuer makes now says ACTIVE on BOTH sites.
        mockMvc.perform(get("/internal/tenants/fan-platform/consumer-members/" + accountId))
                .andExpect(jsonPath("$.membershipStatus").value("ACTIVE"));
        mockMvc.perform(get("/internal/tenants/ecommerce/consumer-members/" + accountId))
                .andExpect(jsonPath("$.membershipStatus").value("ACTIVE"));
    }

    @Test
    @DisplayName("동의를 B2B 테넌트(erp)에 → 200 · consumerSite=false · 멤버십·이벤트 없음")
    void consentOnB2bTenant_writesNothing() throws Exception {
        String accountId = storePoolSignup("616-erp-" + UUID.randomUUID() + "@example.com");

        mockMvc.perform(put("/internal/tenants/erp/consumer-members/" + accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consumerSite").value(false))
                .andExpect(jsonPath("$.membershipStatus").doesNotExist());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consumer_site_memberships WHERE account_id = ?",
                Integer.class, accountId)).isEqualTo(1);
        assertThat(createdEventTenants(accountId)).containsExactly("ecommerce");
    }
}
