package com.example.account.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-622 against a real MySQL (V0032 · V0033), pool on — the site backend's machine path
 * {@code PATCH /internal/tenants/{t}/accounts/{id}/status} changes only site {@code t}'s membership of a
 * consumer-POOL member (owner decision 2026-10-04, TASK-BE-621 § 소유자 결정 2 «별도 티켓으로 적용»).
 *
 * <ul>
 *   <li>AC-2: a pool member of store AND fan — store's LOCKED / ACTIVE / DELETED touch the store membership only;
 *       the fan membership and the account stay ACTIVE; no history row, no outbox event.</li>
 *   <li>AC-3 (control): the ecommerce seller-operator account (a site's own account — what product-service's
 *       seller SUSPEND / CLOSE targets) is LOCKED as an ACCOUNT, {@code scope = ACCOUNT}, {@code account.locked}.</li>
 *   <li>AC-4: a pool account that is not a member of the path site → 404, nothing changed anywhere.</li>
 * </ul>
 * A subclass of {@link AbstractConsumerPoolIntegrationTest} on purpose — one shared context.
 */
@DisplayName("TASK-BE-622 — 프로비저닝 상태 PATCH: 풀 멤버는 그 사이트 멤버십만 · 셀러 운영 계정은 계정 (MySQL, 풀 켜짐)")
class ProvisionStatusSiteScopeIntegrationTest extends AbstractConsumerPoolIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    private String poolSignupAtStore(String prefix) throws Exception {
        String body = mockMvc.perform(post("/api/accounts/signup")
                        .header("X-Tenant-Id", "ecommerce")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s-%s@example.com", "password": "Password1!"}
                                """.formatted(prefix, UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accountId");
    }

    /** Store pool signup, then fan consent — one pool account, ACTIVE on both sites. */
    private String poolMemberOfStoreAndFan(String prefix) throws Exception {
        String accountId = poolSignupAtStore(prefix);
        mockMvc.perform(put("/internal/tenants/fan-platform/consumer-members/" + accountId))
                .andExpect(jsonPath("$.membershipStatus").value("ACTIVE"));
        return accountId;
    }

    private ResultActions patchStatus(String site, String accountId, String status) throws Exception {
        return mockMvc.perform(patch("/internal/tenants/{t}/accounts/{id}/status", site, accountId)
                .header("X-Tenant-Id", site)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status": "%s", "operatorId": "sys-store-622"}
                        """.formatted(status)));
    }

    private Map<String, Object> membership(String accountId, String site) {
        return jdbc.queryForMap("SELECT status, locked_by_actor_id, locked_at, left_by, left_by_actor_id "
                + "FROM consumer_site_memberships WHERE account_id = ? AND site_tenant_id = ?", accountId, site);
    }

    private String accountStatus(String accountId) {
        return jdbc.queryForObject("SELECT status FROM accounts WHERE id = ?", String.class, accountId);
    }

    private int provisioningHistoryRows(String accountId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM account_status_history WHERE account_id = ? "
                + "AND reason_code = 'OPERATOR_PROVISIONING_STATUS_CHANGE'", Integer.class, accountId);
    }

    private int outboxCount(String accountId, String eventType) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM account_outbox WHERE aggregate_id = ? AND event_type = ?",
                Integer.class, accountId, eventType);
    }

    private void accountWideNothing(String accountId) {
        assertThat(accountStatus(accountId)).as("the pool account is every site's — untouched").isEqualTo("ACTIVE");
        assertThat(provisioningHistoryRows(accountId)).isZero();
        assertThat(outboxCount(accountId, "account.status.changed")).isZero();
        assertThat(outboxCount(accountId, "account.locked")).isZero();
        assertThat(outboxCount(accountId, "account.unlocked")).isZero();
        assertThat(outboxCount(accountId, "account.deleted")).isZero();
    }

    @Test
    @DisplayName("AC-2 스토어 백엔드 LOCKED → 스토어만 LOCKED · 팬 ACTIVE · ACTIVE → 해제 · DELETED → LEFT(OPERATOR) · 다시 DELETED 멱등 · 이후 LOCKED 404 · 계정·이력·이벤트 0")
    void storeBackend_poolMember_changesTheStoreMembershipOnly() throws Exception {
        String accountId = poolMemberOfStoreAndFan("622-pool");

        patchStatus("ecommerce", accountId, "LOCKED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("SITE_MEMBERSHIP"))
                .andExpect(jsonPath("$.tenantId").value("ecommerce"))
                .andExpect(jsonPath("$.previousStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.currentStatus").value("LOCKED"));
        assertThat(membership(accountId, "ecommerce")).containsEntry("status", "LOCKED")
                .containsEntry("locked_by_actor_id", "sys-store-622");
        assertThat(membership(accountId, "fan-platform")).as("control: the other site is untouched")
                .containsEntry("status", "ACTIVE").containsEntry("locked_at", null);
        accountWideNothing(accountId);
        mockMvc.perform(get("/internal/tenants/fan-platform/consumer-members/" + accountId))
                .andExpect(jsonPath("$.membershipStatus").value("ACTIVE"));

        patchStatus("ecommerce", accountId, "ACTIVE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("SITE_MEMBERSHIP"))
                .andExpect(jsonPath("$.previousStatus").value("LOCKED"))
                .andExpect(jsonPath("$.currentStatus").value("ACTIVE"));
        assertThat(membership(accountId, "ecommerce")).containsEntry("status", "ACTIVE")
                .containsEntry("locked_at", null).containsEntry("locked_by_actor_id", null);

        patchStatus("ecommerce", accountId, "DELETED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("SITE_MEMBERSHIP"))
                .andExpect(jsonPath("$.previousStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.currentStatus").value("LEFT"));
        assertThat(membership(accountId, "ecommerce")).containsEntry("status", "LEFT")
                .containsEntry("left_by", "OPERATOR").containsEntry("left_by_actor_id", "sys-store-622");
        assertThat(membership(accountId, "fan-platform")).containsEntry("status", "ACTIVE");

        // Edge 1: a fail-soft retry of DELETED is idempotent; LOCKED does not bring a LEFT member back.
        patchStatus("ecommerce", accountId, "DELETED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousStatus").value("LEFT"))
                .andExpect(jsonPath("$.currentStatus").value("LEFT"));
        patchStatus("ecommerce", accountId, "LOCKED")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
        assertThat(membership(accountId, "ecommerce")).containsEntry("status", "LEFT");

        accountWideNothing(accountId);
    }

    @Test
    @DisplayName("AC-3 대조군 — 셀러 운영 계정(사이트 자기 계정) LOCKED → 계정 LOCKED · scope=ACCOUNT · account.locked 1 · 이력 행 1")
    void storeBackend_sellerOperatorAccount_locksTheAccount() throws Exception {
        String email = "seller+ecommerce+" + UUID.randomUUID() + "@marketplace.local";
        String body = mockMvc.perform(post("/internal/tenants/{t}/accounts", "ecommerce")
                        .header("X-Tenant-Id", "ecommerce")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "Password1!", "displayName": "seller",
                                 "roles": ["SELLER"], "operatorId": "product-service"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value("ecommerce"))
                .andReturn().getResponse().getContentAsString();
        String sellerAccountId = JsonPath.read(body, "$.accountId");

        mockMvc.perform(patch("/internal/tenants/{t}/accounts/{id}/status", "ecommerce", sellerAccountId)
                        .header("X-Tenant-Id", "ecommerce")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "LOCKED", "operatorId": "product-service"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("ACCOUNT"))
                .andExpect(jsonPath("$.previousStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.currentStatus").value("LOCKED"));

        assertThat(accountStatus(sellerAccountId)).isEqualTo("LOCKED");
        assertThat(outboxCount(sellerAccountId, "account.locked")).isEqualTo(1);
        // Two OPERATOR_PROVISIONING_STATUS_CHANGE rows, both written on main before TASK-BE-622: the creation audit
        // (ProvisionAccountUseCase#writeProvisioningAudit — ACTIVE→ACTIVE, action OPERATOR_PROVISIONING_CREATE) and
        // the status change (ProvisionStatusChangeUseCase account branch — ACTIVE→LOCKED). Pin each one.
        assertThat(provisioningHistoryRows(sellerAccountId)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT CONCAT(from_status, '>', to_status) FROM account_status_history "
                        + "WHERE account_id = ? AND reason_code = 'OPERATOR_PROVISIONING_STATUS_CHANGE'",
                String.class, sellerAccountId))
                .containsExactlyInAnyOrder("ACTIVE>ACTIVE", "ACTIVE>LOCKED");
        assertThat(jdbc.queryForObject("SELECT tenant_id FROM account_status_history WHERE account_id = ? "
                + "AND to_status = 'LOCKED'", String.class, sellerAccountId)).isEqualTo("ecommerce");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consumer_site_memberships WHERE account_id = ?",
                Integer.class, sellerAccountId)).isZero();
    }

    @Test
    @DisplayName("AC-4 — 팬 멤버십이 없는 풀 계정에 팬 백엔드 LOCKED · DELETED → 404 · 스토어 멤버십·계정 그대로")
    void fanBackend_nonMemberPoolAccount_notFound() throws Exception {
        String accountId = poolSignupAtStore("622-nonmember");

        patchStatus("fan-platform", accountId, "LOCKED")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
        patchStatus("fan-platform", accountId, "DELETED")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));

        assertThat(membership(accountId, "ecommerce")).containsEntry("status", "ACTIVE")
                .containsEntry("locked_at", null).containsEntry("left_by", null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consumer_site_memberships "
                + "WHERE account_id = ? AND site_tenant_id = 'fan-platform'", Integer.class, accountId)).isZero();
        accountWideNothing(accountId);
    }
}
