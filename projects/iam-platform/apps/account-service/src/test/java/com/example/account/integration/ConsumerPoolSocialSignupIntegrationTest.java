package com.example.account.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-617 (ADR-MONO-078 D2 · D4; multi-tenancy.md § 소비자 계정 풀 § 2 · § 6;
 * auth-to-account-social.md) — {@code POST /internal/accounts/social-signup} on a real MySQL with
 * {@code iam.consumer-pool.enabled=true}. Shares {@link AbstractConsumerPoolIntegrationTest}'s context
 * (do not re-declare its properties/mocks here — that forks the context, see its javadoc).
 *
 * <p>🔴 The AC-2 control is {@link #passwordPoolAccountEmail_refused_notLinked}: the row-level proof that
 * a social signup whose email equals an existing PASSWORD pool account's never resolves to that account.
 */
@DisplayName("TASK-BE-617 — 소셜 가입은 풀로 · 이메일로 풀 계정에 붙지 않는다 (MySQL, 플래그 켜짐)")
class ConsumerPoolSocialSignupIntegrationTest extends AbstractConsumerPoolIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private ResultActions socialSignup(String siteTenant, String email) throws Exception {
        return mockMvc.perform(post("/internal/accounts/social-signup")
                .header("X-Tenant-Id", siteTenant)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "provider": "GOOGLE", "providerUserId": "google-%s", "displayName": "Fan"}
                        """.formatted(email, UUID.randomUUID())));
    }

    private String passwordPoolSignup(String siteTenant, String email) throws Exception {
        String body = mockMvc.perform(post("/api/accounts/signup")
                        .header("X-Tenant-Id", siteTenant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "Password1!"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accountId");
    }

    private int accountsWithEmail(String email) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM accounts WHERE email = ?", Integer.class, email);
    }

    @Test
    @DisplayName("AC-1: 팬 소셜 가입 → 201 · tenantId=consumer-pool · 계정은 풀 · 팬 멤버십 · account.created(fan-platform) 1회 · 사이트 행 없음")
    void fanSocialSignup_bornInPool() throws Exception {
        String email = uniqueEmail("social-fan");

        String body = socialSignup("fan-platform", email)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value("consumer-pool"))
                .andReturn().getResponse().getContentAsString();
        String accountId = JsonPath.read(body, "$.accountId");

        assertThat(jdbc.queryForObject("SELECT tenant_id FROM accounts WHERE id = ?", String.class, accountId))
                .isEqualTo("consumer-pool");
        assertThat(jdbc.queryForList(
                "SELECT site_tenant_id, status FROM consumer_site_memberships WHERE account_id = ?", accountId))
                .containsExactly(Map.of("site_tenant_id", "fan-platform", "status", "ACTIVE"));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE email = ? AND tenant_id = 'fan-platform'", Integer.class, email))
                .as("no per-site fan row beside the pool account").isZero();

        List<Map<String, Object>> events = jdbc.queryForList(
                "SELECT payload FROM account_outbox WHERE aggregate_id = ? AND event_type = 'account.created'",
                accountId);
        assertThat(events).hasSize(1);
        String tenantInEvent = JsonPath.read((String) events.get(0).get("payload"), "$.tenantId");
        assertThat(tenantInEvent).isEqualTo("fan-platform").isNotEqualTo("consumer-pool");
    }

    @Test
    @DisplayName("🔴 AC-2 대조군: 스토어 비밀번호 풀 계정과 같은 이메일로 팬 소셜 가입 → 409 · 그 풀 계정을 돌려주지 않는다 · 행·멤버십 무변경")
    void passwordPoolAccountEmail_refused_notLinked() throws Exception {
        String email = uniqueEmail("password-pool");
        String passwordAccountId = passwordPoolSignup("ecommerce", email);

        socialSignup("fan-platform", email)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.accountId").doesNotExist());

        assertThat(accountsWithEmail(email)).as("only the password pool account").isEqualTo(1);
        assertThat(jdbc.queryForList(
                "SELECT site_tenant_id FROM consumer_site_memberships WHERE account_id = ?", String.class,
                passwordAccountId))
                .as("the social attempt did not join the victim's pool account to the fan site")
                .containsExactly("ecommerce");
    }

    @Test
    @DisplayName("§ 2: 다른 소비자 사이트(스토어)에 같은 이메일의 사이트 계정 → 팬 소셜 풀 가입 409 (공존 금지)")
    void siteAccountOnAnotherSite_refused() throws Exception {
        String email = uniqueEmail("legacy-store");
        jdbc.update("INSERT INTO accounts (id, tenant_id, email, status, created_at, updated_at, version) "
                + "VALUES (?, 'ecommerce', ?, 'ACTIVE', NOW(6), NOW(6), 0)", UUID.randomUUID().toString(), email);

        socialSignup("fan-platform", email)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_ALREADY_EXISTS"));

        assertThat(accountsWithEmail(email)).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-3: 같은 사이트의 078 이전 사이트 계정 → 지금처럼 그 계정 (200 · tenantId=fan-platform · 멤버십 안 생김)")
    void sameSiteLegacyAccount_stillLinked() throws Exception {
        String email = uniqueEmail("legacy-fan");
        String siteAccountId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO accounts (id, tenant_id, email, status, created_at, updated_at, version) "
                + "VALUES (?, 'fan-platform', ?, 'ACTIVE', NOW(6), NOW(6), 0)", siteAccountId, email);

        socialSignup("fan-platform", email)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(siteAccountId))
                .andExpect(jsonPath("$.tenantId").value("fan-platform"));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM consumer_site_memberships WHERE account_id = ?", Integer.class, siteAccountId))
                .isZero();
        assertThat(accountsWithEmail(email)).isEqualTo(1);
    }
}
