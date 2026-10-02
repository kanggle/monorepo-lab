package com.example.account.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-752 against a real MySQL — the IAM half of a seller member's lifecycle (consumer-site-roles.md):
 * a store pool signup (ACTIVE store membership) → {@code site-roles:grant} with the right email writes
 * {@code consumer_site_roles(account, ecommerce, SELLER)} (the native INSERT meets the membership FK), the
 * read the token issuer makes ({@code consumer-members}) now answers {@code siteRoles=[SELLER]} for the store
 * and nothing for the fan site, and {@code site-roles:revoke} removes it while the account stays ACTIVE and
 * the membership stays ACTIVE.
 *
 * <p>Shares {@link AbstractConsumerPoolIntegrationTest}'s context (TASK-BE-615 «Too many connections»).
 */
@DisplayName("TASK-MONO-752 — 사이트 역할 쓰기/회수 (MySQL, 풀 켜짐)")
class ConsumerSiteRoleWriteIntegrationTest extends AbstractConsumerPoolIntegrationTest {

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

    private static String grantBody(String email) {
        return "{\"roleName\":\"SELLER\",\"expectedEmail\":\"" + email + "\",\"operatorId\":\"product-service\"}";
    }

    @Test
    @DisplayName("🔴 다른 이메일 → 403 · 행 없음 / 맞는 이메일 → 스토어 SELLER 만 · 팬 없음 / 회수 → 계정·멤버십 그대로")
    void grantThenRevoke_storeOnly_neverLocks() throws Exception {
        String email = "752-member-" + UUID.randomUUID() + "@example.com";
        String accountId = storePoolSignup(email);

        mockMvc.perform(patch("/internal/tenants/ecommerce/accounts/{a}/site-roles:grant", accountId)
                        .contentType(MediaType.APPLICATION_JSON).content(grantBody("someone-else@example.com")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SITE_ROLE_EMAIL_MISMATCH"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consumer_site_roles WHERE account_id = ?",
                Integer.class, accountId)).isZero();

        mockMvc.perform(patch("/internal/tenants/ecommerce/accounts/{a}/site-roles:grant", accountId)
                        .contentType(MediaType.APPLICATION_JSON).content(grantBody(email.toUpperCase())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("SELLER"))
                .andExpect(jsonPath("$.changed").value(true));

        mockMvc.perform(get("/internal/tenants/ecommerce/consumer-members/" + accountId))
                .andExpect(jsonPath("$.siteRoles[0]").value("SELLER"));
        mockMvc.perform(get("/internal/tenants/fan-platform/consumer-members/" + accountId))
                .andExpect(jsonPath("$.siteRoles").isEmpty());

        // idempotent
        mockMvc.perform(patch("/internal/tenants/ecommerce/accounts/{a}/site-roles:grant", accountId)
                        .contentType(MediaType.APPLICATION_JSON).content(grantBody(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(false));

        mockMvc.perform(patch("/internal/tenants/ecommerce/accounts/{a}/site-roles:revoke", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleName\":\"SELLER\",\"operatorId\":\"product-service\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isEmpty())
                .andExpect(jsonPath("$.changed").value(true));

        assertThat(jdbc.queryForObject("SELECT status FROM accounts WHERE id = ?", String.class, accountId))
                .as("ADR-MONO-079 D5: revoking the seller role never locks the person")
                .isEqualTo("ACTIVE");
        mockMvc.perform(get("/internal/tenants/ecommerce/consumer-members/" + accountId))
                .andExpect(jsonPath("$.membershipStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.siteRoles").isEmpty());
    }

    @Test
    @DisplayName("팬 멤버십 없는 계정에 다른 사이트 역할은 닫힌 목록 밖 → 400 · 멤버십 없는 스토어 grant → 409")
    void closedList_andMembershipRequired() throws Exception {
        String email = "752-closed-" + UUID.randomUUID() + "@example.com";
        String accountId = storePoolSignup(email);

        mockMvc.perform(patch("/internal/tenants/ecommerce/accounts/{a}/site-roles:grant", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleName\":\"ECOMMERCE_OPERATOR\",\"expectedEmail\":\"" + email + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SITE_ROLE_NOT_GRANTABLE"));

        jdbc.update("UPDATE consumer_site_memberships SET status = 'LEFT' WHERE account_id = ? "
                + "AND site_tenant_id = 'ecommerce'", accountId);
        mockMvc.perform(patch("/internal/tenants/ecommerce/accounts/{a}/site-roles:grant", accountId)
                        .contentType(MediaType.APPLICATION_JSON).content(grantBody(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SITE_MEMBERSHIP_REQUIRED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consumer_site_roles WHERE account_id = ?",
                Integer.class, accountId)).isZero();
    }
}
