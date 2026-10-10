package com.example.account.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-772 S3 against a real MySQL (pool flag on) — {@code POST /internal/consumer-pool/signups}
 * (auth-to-account.md), the account-service half of AC-6's invitee path:
 *
 * <ul>
 *   <li>🔴 the site-less signup creates a {@code consumer-pool} account and 🔴 NO {@code consumer_site_memberships}
 *       row and NO {@code account.created} outbox row (772 AC-0 F8);</li>
 *   <li>that account then answers the acceptance's {@code verified-email:match}: 403 {@code EMAIL_NOT_VERIFIED}
 *       until it is verified, 200 after — the same account, the same address (the admin-service half is
 *       {@code OperatorInvitationAcceptanceIntegrationTest});</li>
 *   <li>a pool duplicate and a consumer SITE account with the email → 409 {@code ACCOUNT_ALREADY_EXISTS}, nothing
 *       created.</li>
 * </ul>
 *
 * <p>Shares {@link AbstractConsumerPoolIntegrationTest}'s context (TASK-BE-615 «Too many connections»).
 */
@DisplayName("TASK-MONO-772 S3 — 사이트 없는 풀 가입 (MySQL, 풀 켜짐)")
class ConsumerPoolSiteLessSignupIntegrationTest extends AbstractConsumerPoolIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    private ResultActions signup(String email) throws Exception {
        return mockMvc.perform(post("/internal/consumer-pool/signups")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "Password1!", "displayName": "S3 피초대자"}
                        """.formatted(email)));
    }

    private ResultActions match(String accountId, String expectedEmail) throws Exception {
        return mockMvc.perform(post("/internal/accounts/{a}/verified-email:match", accountId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedEmail\":\"" + expectedEmail + "\"}"));
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    @Test
    @DisplayName("🔴 풀 계정만: consumer-pool 계정 · 자격은 풀 · 사이트 멤버십 0 · account.created 0 → 미인증 403 → 인증 뒤 200 (AC-6 계정 쪽)")
    void siteLessSignup_poolAccountOnly_thenVerifiedMatch() throws Exception {
        String email = "772-s3-" + UUID.randomUUID() + "@example.com";

        String body = signup(email)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        String accountId = JsonPath.read(body, "$.accountId");

        assertThat(jdbc.queryForObject("SELECT tenant_id FROM accounts WHERE id = ?", String.class, accountId))
                .isEqualTo("consumer-pool");
        assertThat(count("SELECT COUNT(*) FROM consumer_site_memberships WHERE account_id = ?", accountId))
                .as("no site membership — the invitee did not join a store or fan site").isZero();
        assertThat(count("SELECT COUNT(*) FROM account_outbox WHERE aggregate_id = ? AND event_type = 'account.created'",
                accountId)).as("no account.created — no site to announce it to").isZero();
        verify(authServicePort).createCredential(eq(accountId), eq(email), eq("Password1!"), eq("consumer-pool"), any());

        // The acceptance's verdict on this very account: not verified yet → 403; verified → 200.
        match(accountId, email)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        jdbc.update("UPDATE accounts SET email_verified_at = CURRENT_TIMESTAMP(6) WHERE id = ?", accountId);
        match(accountId, email)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId));
    }

    @Test
    @DisplayName("같은 이메일의 풀 계정 · 소비자 사이트 계정 → 409 ACCOUNT_ALREADY_EXISTS · 새 계정 없음")
    void duplicates_refused() throws Exception {
        String pooled = "772-s3-dup-" + UUID.randomUUID() + "@example.com";
        signup(pooled).andExpect(status().isCreated());
        signup(pooled).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_ALREADY_EXISTS"));
        assertThat(count("SELECT COUNT(*) FROM accounts WHERE email = ?", pooled)).isEqualTo(1);

        String siteEmail = "772-s3-site-" + UUID.randomUUID() + "@example.com";
        jdbc.update("INSERT INTO accounts (id, tenant_id, email, status, created_at, updated_at, version) "
                + "VALUES (?, 'fan-platform', ?, 'ACTIVE', NOW(6), NOW(6), 0)", UUID.randomUUID().toString(), siteEmail);
        signup(siteEmail).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_ALREADY_EXISTS"));
        assertThat(count("SELECT COUNT(*) FROM accounts WHERE email = ? AND tenant_id = 'consumer-pool'", siteEmail))
                .as("§ 2 — no pool account next to the site account").isZero();
    }
}
