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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-772 S2 against a real MySQL — {@code verified-email:match} reads the real {@code accounts} row of a
 * real pool signup (admin-to-account.md):
 *
 * <ul>
 *   <li>🔴 control group in ONE test: the same pool account and the same expected address — unverified → 403
 *       {@code EMAIL_NOT_VERIFIED} first, then (column set) → 200 with the verification time;</li>
 *   <li>a different address → 403 {@code ACCOUNT_EMAIL_MISMATCH}; no such id · a LOCKED pool account → one 404;</li>
 *   <li>the read writes nothing ({@code email_verified_at} and {@code status} unchanged);</li>
 *   <li>{@code notifications/operator-invitation} with mail off (the logging stub) → 204.</li>
 * </ul>
 *
 * <p>Shares {@link AbstractConsumerPoolIntegrationTest}'s context (TASK-BE-615 «Too many connections»).
 */
@DisplayName("TASK-MONO-772 S2 — 인증된 이메일 일치 판정 · 초대 메일 (MySQL, 풀 켜짐)")
class OperatorInvitationSupportIntegrationTest extends AbstractConsumerPoolIntegrationTest {

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
        String accountId = JsonPath.read(body, "$.accountId");
        assertThat(jdbc.queryForObject("SELECT tenant_id FROM accounts WHERE id = ?", String.class, accountId))
                .as("precondition: the store signup lands in the consumer pool").isEqualTo("consumer-pool");
        return accountId;
    }

    private ResultActions match(String accountId, String expectedEmail) throws Exception {
        return mockMvc.perform(post("/internal/accounts/{a}/verified-email:match", accountId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedEmail\":\"" + expectedEmail + "\"}"));
    }

    @Test
    @DisplayName("🔴 대조군: 같은 풀 계정 · 같은 주소 — 미인증 403 EMAIL_NOT_VERIFIED 먼저 → 인증 뒤 200 (읽기는 아무것도 쓰지 않는다)")
    void unverifiedFirst_thenVerified() throws Exception {
        String email = "772-match-" + UUID.randomUUID() + "@example.com";
        String accountId = storePoolSignup(email);

        match(accountId, email)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        assertThat(jdbc.queryForObject("SELECT email_verified_at FROM accounts WHERE id = ?",
                java.sql.Timestamp.class, accountId)).as("the refusal wrote nothing").isNull();

        assertThat(jdbc.update("UPDATE accounts SET email_verified_at = CURRENT_TIMESTAMP(6) WHERE id = ?", accountId))
                .isEqualTo(1);

        match(accountId, email.toUpperCase())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.emailVerifiedAt").isNotEmpty());
        assertThat(jdbc.queryForObject("SELECT status FROM accounts WHERE id = ?", String.class, accountId))
                .isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("다른 주소 → 403 ACCOUNT_EMAIL_MISMATCH (인증돼 있어도) · 없는 id · LOCKED 풀 계정 → 404 ACCOUNT_NOT_FOUND")
    void mismatch_andNotEligible() throws Exception {
        String email = "772-other-" + UUID.randomUUID() + "@example.com";
        String accountId = storePoolSignup(email);
        jdbc.update("UPDATE accounts SET email_verified_at = CURRENT_TIMESTAMP(6) WHERE id = ?", accountId);

        match(accountId, "someone-else@example.com")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_EMAIL_MISMATCH"));

        match(UUID.randomUUID().toString(), email)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));

        jdbc.update("UPDATE accounts SET status = 'LOCKED' WHERE id = ?", accountId);
        match(accountId, email)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    @DisplayName("초대 메일: 메일 꺼짐(로깅 스텁) → 204 · 필드 누락 → 400")
    void invitationMail() throws Exception {
        mockMvc.perform(post("/internal/notifications/operator-invitation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"to":"772-mail@example.com","token":"%s","tenantId":"ecommerce",
                                 "inviterDisplayName":"김관리","expiresAt":"2026-10-17T10:00:00Z"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/internal/notifications/operator-invitation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"772-mail@example.com\",\"tenantId\":\"ecommerce\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
