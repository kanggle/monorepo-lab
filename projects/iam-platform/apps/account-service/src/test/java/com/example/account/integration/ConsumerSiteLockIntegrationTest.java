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
 * TASK-BE-621 against a real MySQL (V0033) — «사이트 잠금» vs «계정 잠금» (owner decision 2026-10-04 «사이트
 * 운영자가 회원을 잠글 때, 그 잠금은 자기 사이트에만 걸린다. 계정 전체 잠금은 플랫폼 관리자만.»), with the
 * control the AC asks for: a store operator's lock never touches the fan site or the account.
 *
 * <ul>
 *   <li>Store operator lock ({@code X-Tenant-Id: ecommerce}) of a pool member that also uses the fan site → only
 *       the store membership is LOCKED; the fan membership and the account stay ACTIVE; no {@code account.locked}.
 *       The store operator still finds the member (list / unlock), consent and the person's own «leave» do not
 *       reopen it, and the store operator's unlock makes it ACTIVE again.</li>
 *   <li>The platform admin ({@code "*"}) locks the pool account itself — every site.</li>
 * </ul>
 * «LOCKED → no token for that site» is the auth-service side (TASK-BE-615 issuance reads
 * {@code membershipStatus}); here the read it makes is asserted per site.
 * A subclass of {@link AbstractConsumerPoolIntegrationTest} on purpose — one shared context.
 */
@DisplayName("TASK-BE-621 — 사이트 잠금(멤버십 LOCKED) vs 계정 잠금 (MySQL, 풀 켜짐)")
class ConsumerSiteLockIntegrationTest extends AbstractConsumerPoolIntegrationTest {

    private static final String LOCK_BODY = """
            {"reason": "ADMIN_LOCK", "operatorId": "op-621"}
            """;
    private static final String UNLOCK_BODY = """
            {"reason": "ADMIN_UNLOCK", "operatorId": "op-621"}
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
        return jdbc.queryForMap("SELECT status, locked_by_actor_id, locked_at, left_by FROM consumer_site_memberships "
                + "WHERE account_id = ? AND site_tenant_id = ?", accountId, site);
    }

    private String accountStatus(String accountId) {
        return jdbc.queryForObject("SELECT status FROM accounts WHERE id = ?", String.class, accountId);
    }

    private int outboxCount(String accountId, String eventType) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM account_outbox WHERE aggregate_id = ? AND event_type = ?",
                Integer.class, accountId, eventType);
    }

    @Test
    @DisplayName("스토어 운영자 잠금 → 스토어 멤버십만 LOCKED · 팬 ACTIVE · 계정 ACTIVE · account.locked 0 · 재동의·본인 탈퇴로 안 열림 · 해제 → ACTIVE")
    void storeOperatorLock_locksTheStoreOnly() throws Exception {
        String accountId = poolMemberOfStoreAndFan("621-op");

        mockMvc.perform(post("/internal/accounts/{id}/lock", accountId)
                        .header("X-Tenant-Id", "ecommerce")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOCK_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("SITE_MEMBERSHIP"))
                .andExpect(jsonPath("$.siteTenantId").value("ecommerce"))
                .andExpect(jsonPath("$.previousStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.currentStatus").value("LOCKED"));

        assertThat(membership(accountId, "ecommerce")).containsEntry("status", "LOCKED")
                .containsEntry("locked_by_actor_id", "op-621");
        assertThat(membership(accountId, "ecommerce").get("locked_at")).isNotNull();
        assertThat(membership(accountId, "fan-platform")).as("control: the other site is untouched")
                .containsEntry("status", "ACTIVE").containsEntry("locked_at", null);
        assertThat(accountStatus(accountId)).as("the pool account is every site's — not locked").isEqualTo("ACTIVE");
        assertThat(outboxCount(accountId, "account.locked")).isZero();
        assertThat(outboxCount(accountId, "account.status.changed")).isZero();

        // The reads the token issuer makes: store LOCKED (no token), fan ACTIVE (token as before).
        mockMvc.perform(get("/internal/tenants/ecommerce/consumer-members/" + accountId))
                .andExpect(jsonPath("$.membershipStatus").value("LOCKED"));
        mockMvc.perform(get("/internal/tenants/fan-platform/consumer-members/" + accountId))
                .andExpect(jsonPath("$.membershipStatus").value("ACTIVE"));

        // Still a member of the store for the store operator (§ 5 — ACTIVE or LOCKED): the list shows it.
        mockMvc.perform(get("/internal/tenants/ecommerce/accounts/" + accountId))
                .andExpect(status().isOk());

        // Consent does not reopen it; the person's own «leave» is not a way out either.
        mockMvc.perform(put("/internal/tenants/ecommerce/consumer-members/" + accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipStatus").value("LOCKED"));
        mockMvc.perform(delete("/api/accounts/me/site-membership")
                        .header("X-Account-Id", accountId)
                        .header("X-Tenant-Id", "ecommerce"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipStatus").value("LOCKED"));
        assertThat(membership(accountId, "ecommerce")).containsEntry("status", "LOCKED");

        // The store operator unlocks → ACTIVE again, lock record cleared.
        mockMvc.perform(post("/internal/accounts/{id}/unlock", accountId)
                        .header("X-Tenant-Id", "ecommerce")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UNLOCK_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("SITE_MEMBERSHIP"))
                .andExpect(jsonPath("$.previousStatus").value("LOCKED"))
                .andExpect(jsonPath("$.currentStatus").value("ACTIVE"));
        assertThat(membership(accountId, "ecommerce")).containsEntry("status", "ACTIVE")
                .containsEntry("locked_at", null).containsEntry("locked_by_actor_id", null);
        assertThat(outboxCount(accountId, "account.unlocked")).isZero();
    }

    @Test
    @DisplayName("잠긴 스토어 회원을 스토어 운영자가 GDPR 삭제 → LEFT(OPERATOR) · 잠금 기록 지움 · 팬·계정 그대로")
    void storeOperatorErasure_ofLockedMember_leavesTheStore() throws Exception {
        String accountId = poolMemberOfStoreAndFan("621-gdpr");
        mockMvc.perform(post("/internal/accounts/{id}/lock", accountId)
                        .header("X-Tenant-Id", "ecommerce")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOCK_BODY))
                .andExpect(status().isOk());

        mockMvc.perform(post("/internal/accounts/{id}/gdpr-delete", accountId)
                        .header("X-Tenant-Id", "ecommerce")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "REGULATED_DELETION", "operatorId": "op-621"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("SITE_MEMBERSHIP"));

        assertThat(membership(accountId, "ecommerce")).containsEntry("status", "LEFT")
                .containsEntry("left_by", "OPERATOR").containsEntry("locked_at", null);
        assertThat(membership(accountId, "fan-platform")).containsEntry("status", "ACTIVE");
        assertThat(accountStatus(accountId)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("플랫폼 관리자('*') 잠금 → 풀 계정 자체를 잠근다 (계정 LOCKED · scope=ACCOUNT · account.locked 1 · 멤버십 그대로)")
    void platformAdminLock_locksThePoolAccount() throws Exception {
        String accountId = poolMemberOfStoreAndFan("621-platform");

        mockMvc.perform(post("/internal/accounts/{id}/lock", accountId)
                        .header("X-Tenant-Id", "*")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOCK_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("ACCOUNT"))
                .andExpect(jsonPath("$.currentStatus").value("LOCKED"));

        assertThat(accountStatus(accountId)).isEqualTo("LOCKED");
        assertThat(outboxCount(accountId, "account.locked")).isEqualTo(1);
        assertThat(membership(accountId, "ecommerce")).containsEntry("status", "ACTIVE");
        assertThat(membership(accountId, "fan-platform")).containsEntry("status", "ACTIVE");

        // D-3: the store operator cannot lift the whole-account lock — only the store membership is looked at.
        mockMvc.perform(post("/internal/accounts/{id}/unlock", accountId)
                        .header("X-Tenant-Id", "ecommerce")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UNLOCK_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("SITE_MEMBERSHIP"));
        assertThat(accountStatus(accountId)).isEqualTo("LOCKED");
    }
}
