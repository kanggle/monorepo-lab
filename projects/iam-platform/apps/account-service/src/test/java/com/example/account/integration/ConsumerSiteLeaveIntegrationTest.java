package com.example.account.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-619 against a real MySQL (V0032) — «사이트 탈퇴» vs «계정 삭제» (owner decisions 2026-10-03), with the
 * control the AC asks for: leaving ONE site never touches the other site or the account.
 *
 * <ul>
 *   <li>A store operator's GDPR erasure of a pool member that also uses the fan site → only the store
 *       membership is LEFT ({@code left_by = OPERATOR}); the fan membership stays ACTIVE, the account stays
 *       ACTIVE with its e-mail, no {@code account.deleted}. Consent cannot reopen it.</li>
 *   <li>The person leaving the fan site themself → LEFT ({@code SELF}); consenting again reopens it, and no
 *       second {@code account.created(fan-platform)} is published.</li>
 *   <li>The platform admin (no tenant named — {@code "*"}) erases the pool account itself.</li>
 * </ul>
 * «LEFT → no token for that site» is the auth-service side (TASK-BE-615 issuance reads
 * {@code membershipStatus}); here the read it makes is asserted per site.
 * A subclass of {@link AbstractConsumerPoolIntegrationTest} on purpose — one shared context.
 */
@DisplayName("TASK-BE-619 — 사이트 탈퇴(멤버십 LEFT) vs 계정 삭제 (MySQL, 풀 켜짐)")
class ConsumerSiteLeaveIntegrationTest extends AbstractConsumerPoolIntegrationTest {

    private static final String GDPR_BODY = """
            {"reason": "REGULATED_DELETION", "operatorId": "op-619"}
            """;

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    /** Store pool signup, then fan consent — one pool account, ACTIVE on both sites. */
    private String poolMemberOfStoreAndFan(String prefix) throws Exception {
        String body = mockMvc.perform(post("/api/accounts/signup")
                        .header("X-Tenant-Id", "ecommerce")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s-%s@example.com", "password": "Password1!"}
                                """.formatted(prefix, UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String accountId = JsonPath.read(body, "$.accountId");
        mockMvc.perform(put("/internal/tenants/fan-platform/consumer-members/" + accountId))
                .andExpect(jsonPath("$.membershipStatus").value("ACTIVE"));
        return accountId;
    }

    private Map<String, Object> membership(String accountId, String site) {
        return jdbc.queryForMap("SELECT status, left_by, left_by_actor_id, left_at FROM consumer_site_memberships "
                + "WHERE account_id = ? AND site_tenant_id = ?", accountId, site);
    }

    private int outboxCount(String accountId, String eventType) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM account_outbox WHERE aggregate_id = ? AND event_type = ?",
                Integer.class, accountId, eventType);
    }

    @Test
    @DisplayName("스토어 운영자 GDPR 삭제 → 스토어 멤버십만 LEFT(OPERATOR) · 팬 ACTIVE · 계정 ACTIVE·이메일 그대로 · 재동의로 안 열림")
    void storeOperatorErasure_leavesTheStoreOnly() throws Exception {
        String accountId = poolMemberOfStoreAndFan("619-op");
        String emailBefore = jdbc.queryForObject("SELECT email FROM accounts WHERE id = ?", String.class, accountId);

        mockMvc.perform(post("/internal/accounts/{id}/gdpr-delete", accountId)
                        .header("X-Tenant-Id", "ecommerce")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GDPR_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("SITE_MEMBERSHIP"))
                .andExpect(jsonPath("$.siteTenantId").value("ecommerce"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(membership(accountId, "ecommerce")).containsEntry("status", "LEFT")
                .containsEntry("left_by", "OPERATOR").containsEntry("left_by_actor_id", "op-619");
        assertThat(membership(accountId, "fan-platform")).as("control: the other site is untouched")
                .containsEntry("status", "ACTIVE").containsEntry("left_by", null);
        assertThat(jdbc.queryForObject("SELECT status FROM accounts WHERE id = ?", String.class, accountId))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT email FROM accounts WHERE id = ?", String.class, accountId))
                .as("nothing erased — the pool account is every site's").isEqualTo(emailBefore);
        assertThat(outboxCount(accountId, "account.deleted")).isZero();

        // The reads the token issuer makes: store LEFT (no token), fan ACTIVE (token as before).
        mockMvc.perform(get("/internal/tenants/ecommerce/consumer-members/" + accountId))
                .andExpect(jsonPath("$.membershipStatus").value("LEFT"))
                .andExpect(jsonPath("$.leftBy").value("OPERATOR"));
        mockMvc.perform(get("/internal/tenants/fan-platform/consumer-members/" + accountId))
                .andExpect(jsonPath("$.membershipStatus").value("ACTIVE"));

        // Owner decision: an operator removal is NOT reopened by consent.
        mockMvc.perform(put("/internal/tenants/ecommerce/consumer-members/" + accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipStatus").value("LEFT"));

        // The store operator cannot reach it again (no ACTIVE store membership → 404, § 5 non-member control).
        mockMvc.perform(post("/internal/accounts/{id}/gdpr-delete", accountId)
                        .header("X-Tenant-Id", "ecommerce")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GDPR_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("본인 팬 탈퇴 → 팬만 LEFT(SELF) · 스토어 ACTIVE · 다시 동의하면 팬 ACTIVE · account.created(fan) 는 1회 그대로")
    void selfLeave_thenConsentAgain_reopens() throws Exception {
        String accountId = poolMemberOfStoreAndFan("619-self");
        assertThat(outboxCount(accountId, "account.created")).isEqualTo(2);

        mockMvc.perform(delete("/api/accounts/me/site-membership")
                        .header("X-Account-Id", accountId)
                        .header("X-Tenant-Id", "fan-platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipStatus").value("LEFT"))
                .andExpect(jsonPath("$.leftBy").value("SELF"));

        assertThat(membership(accountId, "fan-platform")).containsEntry("status", "LEFT")
                .containsEntry("left_by", "SELF");
        assertThat(membership(accountId, "ecommerce")).as("control: the other site is untouched")
                .containsEntry("status", "ACTIVE");
        assertThat(jdbc.queryForObject("SELECT status FROM accounts WHERE id = ?", String.class, accountId))
                .isEqualTo("ACTIVE");
        mockMvc.perform(get("/internal/tenants/fan-platform/consumer-members/" + accountId))
                .andExpect(jsonPath("$.membershipStatus").value("LEFT"))
                .andExpect(jsonPath("$.leftBy").value("SELF"));

        mockMvc.perform(put("/internal/tenants/fan-platform/consumer-members/" + accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipStatus").value("ACTIVE"));
        assertThat(membership(accountId, "fan-platform")).containsEntry("status", "ACTIVE")
                .containsEntry("left_by", null).containsEntry("left_at", null);
        assertThat(outboxCount(accountId, "account.created"))
                .as("§ 6 — account.created once per (account, site); coming back is not a first visit")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("플랫폼 관리자('*') GDPR 삭제 → 풀 계정 자체를 지운다 (DELETED · 마스킹 · scope=ACCOUNT)")
    void platformAdminErasure_deletesThePoolAccount() throws Exception {
        String accountId = poolMemberOfStoreAndFan("619-platform");

        mockMvc.perform(post("/internal/accounts/{id}/gdpr-delete", accountId)
                        .header("X-Tenant-Id", "*")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GDPR_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("ACCOUNT"))
                .andExpect(jsonPath("$.status").value("DELETED"));

        assertThat(jdbc.queryForObject("SELECT status FROM accounts WHERE id = ?", String.class, accountId))
                .isEqualTo("DELETED");
        assertThat(jdbc.queryForObject("SELECT email FROM accounts WHERE id = ?", String.class, accountId))
                .startsWith("gdpr_");
        assertThat(outboxCount(accountId, "account.deleted")).isEqualTo(1);
    }
}
