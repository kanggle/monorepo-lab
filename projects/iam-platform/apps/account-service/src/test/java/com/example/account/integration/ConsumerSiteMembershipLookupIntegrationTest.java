package com.example.account.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-615 against a real MySQL, pool on:
 * <ul>
 *   <li>{@code GET /internal/tenants/{site}/consumer-members/{accountId}} — the read auth-service signs
 *       a pool principal into a site with: ACTIVE on the signup site, no membership elsewhere, and
 *       the site roles of THAT site only (no flattening across sites — contract § 4 role rule).</li>
 *   <li>AC-8 — retired by TASK-MONO-772 S7: {@code excludePoolMembers} had one caller (admin-service's
 *       TASK-MONO-334 operator-create check), which is gone. A stale {@code excludePoolMembers=true} is now
 *       ignored — the search answers by § 5 (pool members included) exactly like the default.</li>
 * </ul>
 */
@DisplayName("TASK-BE-615 — 사이트 멤버십 조회 · 퇴역한 운영자 생성 확인 범위 (MySQL, 풀 켜짐)")
class ConsumerSiteMembershipLookupIntegrationTest extends AbstractConsumerPoolIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private String poolSignup(String site, String email) throws Exception {
        String body = mockMvc.perform(post("/api/accounts/signup")
                        .header("X-Tenant-Id", site)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "Password1!"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accountId");
    }

    @Test
    @DisplayName("멤버십 조회: 가입 사이트 ACTIVE · 다른 사이트 null · 사이트 역할은 그 사이트 것만")
    void membershipRead_perSite_noFlattening() throws Exception {
        String accountId = poolSignup("ecommerce", uniqueEmail("615-shopper"));
        // The fan site too (what TASK-BE-616's consent will write), with a fan-only role, and a store role.
        jdbc.update("INSERT INTO consumer_site_memberships (account_id, site_tenant_id, status, consented_at) "
                + "VALUES (?, 'fan-platform', 'ACTIVE', NOW(6))", accountId);
        jdbc.update("INSERT INTO consumer_site_roles (account_id, site_tenant_id, role_name, granted_at) "
                + "VALUES (?, 'fan-platform', 'ARTIST', NOW(6))", accountId);
        jdbc.update("INSERT INTO consumer_site_roles (account_id, site_tenant_id, role_name, granted_at) "
                + "VALUES (?, 'ecommerce', 'SELLER', NOW(6))", accountId);

        mockMvc.perform(get("/internal/tenants/ecommerce/consumer-members/" + accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consumerSite").value(true))
                .andExpect(jsonPath("$.siteTenantType").value("B2C_CONSUMER"))
                .andExpect(jsonPath("$.membershipStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.siteRoles.length()").value(1))
                .andExpect(jsonPath("$.siteRoles[0]").value("SELLER"));
        mockMvc.perform(get("/internal/tenants/fan-platform/consumer-members/" + accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.siteRoles.length()").value(1))
                .andExpect(jsonPath("$.siteRoles[0]").value("ARTIST"));
    }

    @Test
    @DisplayName("멤버십 조회: 멤버가 아닌 사이트 → 200 · membershipStatus null · 역할 없음 · B2B 테넌트는 consumerSite=false")
    void membershipRead_nonMember_and_nonConsumerSite() throws Exception {
        String accountId = poolSignup("ecommerce", uniqueEmail("615-store-only"));

        mockMvc.perform(get("/internal/tenants/fan-platform/consumer-members/" + accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consumerSite").value(true))
                .andExpect(jsonPath("$.membershipStatus").doesNotExist())
                .andExpect(jsonPath("$.siteRoles").isEmpty());
        mockMvc.perform(get("/internal/tenants/erp/consumer-members/" + accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consumerSite").value(false))
                .andExpect(jsonPath("$.membershipStatus").doesNotExist());
    }

    @Test
    @DisplayName("772 S7: 퇴역한 excludePoolMembers=true 는 무시 — 풀 가입 쇼핑객을 기본 검색과 똑같이 찾는다")
    void retiredExcludePoolMembersFlag_isIgnored() throws Exception {
        String email = uniqueEmail("772-s7-shopper");
        String accountId = poolSignup("ecommerce", email);

        mockMvc.perform(get("/internal/accounts").param("tenantId", "ecommerce").param("email", email)
                        .param("excludePoolMembers", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(accountId));
        mockMvc.perform(get("/internal/accounts").param("tenantId", "ecommerce").param("email", email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(accountId));
    }
}
