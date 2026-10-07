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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    /**
     * TASK-MONO-770 — the state {@code VerifyEmailUseCase} leaves behind ({@code accounts.email_verified_at} set).
     * Written directly: this context has no Redis, so the token round-trip itself is covered by
     * {@code VerifyEmailUseCaseTest} / the IdP page slice; what this class proves is the grant's reading of the
     * column against a real MySQL row.
     */
    private void markEmailVerified(String accountId) {
        assertThat(jdbc.update("UPDATE accounts SET email_verified_at = CURRENT_TIMESTAMP(6) WHERE id = ?", accountId))
                .isEqualTo(1);
    }

    private int siteRoleRows(String accountId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM consumer_site_roles WHERE account_id = ?",
                Integer.class, accountId);
    }

    /**
     * TASK-MONO-770 AC-2 (ADR-MONO-080 § Verification) — the control group against a real MySQL. A pool account
     * whose email IS the invited address but is not verified: the grant is refused FIRST (asserted before
     * anything else), nothing is written; the SAME account, once verified, is granted in the SAME test.
     */
    @Test
    @DisplayName("🔴 AC-2 대조군: 초대 주소와 같은 미인증 풀 계정 → 403 EMAIL_NOT_VERIFIED · 행 없음 → 인증 뒤 같은 요청 → 200 SELLER")
    void unverifiedRefusedFirst_thenVerifiedGranted() throws Exception {
        String email = "770-unverified-" + UUID.randomUUID() + "@example.com";
        String accountId = storePoolSignup(email);

        mockMvc.perform(patch("/internal/tenants/ecommerce/accounts/{a}/site-roles:grant", accountId)
                        .contentType(MediaType.APPLICATION_JSON).content(grantBody(email)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        assertThat(siteRoleRows(accountId)).isZero();

        markEmailVerified(accountId);

        mockMvc.perform(patch("/internal/tenants/ecommerce/accounts/{a}/site-roles:grant", accountId)
                        .contentType(MediaType.APPLICATION_JSON).content(grantBody(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("SELLER"))
                .andExpect(jsonPath("$.changed").value(true));
        assertThat(siteRoleRows(accountId)).isEqualTo(1);
    }

    /**
     * TASK-MONO-770 AC-3 — consumer use does not depend on verification. An unverified pool account: the reads
     * the token issuer makes for the store and the fan site (membership + seed), the first-visit consent to the
     * fan site, and the account status read (what password login checks) are all exactly what they were. Only a company-role
     * write asks for verification.
     */
    @Test
    @DisplayName("AC-3 회귀: 미인증 풀 계정 — 스토어 멤버십 읽기 · 팬 동의 · 상태 · 프로필은 그대로 (게이트는 회사 권한 쓰기에만)")
    void unverifiedAccount_consumerUseUnchanged() throws Exception {
        String email = "770-consumer-" + UUID.randomUUID() + "@example.com";
        String accountId = storePoolSignup(email);
        assertThat(jdbc.queryForObject("SELECT email_verified_at FROM accounts WHERE id = ?", java.sql.Timestamp.class,
                accountId)).as("precondition: the account is unverified").isNull();

        mockMvc.perform(get("/internal/tenants/ecommerce/consumer-members/" + accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipStatus").value("ACTIVE"));
        mockMvc.perform(put("/internal/tenants/fan-platform/consumer-members/" + accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipStatus").value("ACTIVE"));
        mockMvc.perform(get("/internal/accounts/" + accountId + "/status").header("X-Tenant-Id", "consumer-pool"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("🔴 다른 이메일 → 403 · 행 없음 / 맞는 이메일 → 스토어 SELLER 만 · 팬 없음 / 회수 → 계정·멤버십 그대로")
    void grantThenRevoke_storeOnly_neverLocks() throws Exception {
        String email = "752-member-" + UUID.randomUUID() + "@example.com";
        String accountId = storePoolSignup(email);
        // TASK-MONO-770: this cell is about the email and revoke rules — the account has verified its address.
        markEmailVerified(accountId);

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
        markEmailVerified(accountId); // TASK-MONO-770: so the 409 below is the membership rule, not rule 4b

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
