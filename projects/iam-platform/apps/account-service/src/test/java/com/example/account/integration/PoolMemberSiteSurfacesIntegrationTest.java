package com.example.account.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-616 against a real MySQL, pool on — the surfaces that find ONE account by a request site tenant
 * (multi-tenancy.md § 소비자 계정 풀 § 5) and the reverse-direction coexistence refusal (§ 2).
 *
 * <p>Each cell signs up a pool shopper at the STORE only, then asks through {@code ecommerce} (member →
 * found) and through {@code fan-platform} (not a member → 404, nothing changed) — the non-member control.
 * A subclass of {@link AbstractConsumerPoolIntegrationTest}: the shared context (TASK-BE-615 lesson).
 */
@DisplayName("TASK-BE-616 — 사이트 테넌트 단건 표면의 풀 멤버 포함 · 프로비저닝 공존 거절 (MySQL, 풀 켜짐)")
class PoolMemberSiteSurfacesIntegrationTest extends AbstractConsumerPoolIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private String storePoolShopper(String email) throws Exception {
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

    private String accountStatus(String accountId) {
        return jdbc.queryForObject("SELECT status FROM accounts WHERE id = ?", String.class, accountId);
    }

    @Test
    @DisplayName("/api/accounts/me · /me/status · export: ecommerce(멤버) 200 · fan-platform(비멤버) 404")
    void readSurfaces_member200_nonMember404() throws Exception {
        String id = storePoolShopper(uniqueEmail("616-me"));

        mockMvc.perform(get("/api/accounts/me").header("X-Account-Id", id).header("X-Tenant-Id", "ecommerce"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(id));
        mockMvc.perform(get("/api/accounts/me").header("X-Account-Id", id).header("X-Tenant-Id", "fan-platform"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/accounts/me/status").header("X-Account-Id", id).header("X-Tenant-Id", "ecommerce"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(get("/api/accounts/me/status").header("X-Account-Id", id).header("X-Tenant-Id", "fan-platform"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/internal/accounts/{id}/export", id).header("X-Tenant-Id", "ecommerce"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(id));
        mockMvc.perform(get("/internal/accounts/{id}/export", id).header("X-Tenant-Id", "fan-platform"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    @DisplayName("PATCH /internal/tenants/{t}/accounts/{id}/status: fan-platform(비멤버) 404·무변경 → ecommerce 200 LOCKED (계정 전체) · 이력 행 테넌트 consumer-pool")
    void statusChange_nonMemberRefused_memberLocksTheOneAccount() throws Exception {
        String id = storePoolShopper(uniqueEmail("616-status"));
        String body = """
                {"status":"LOCKED","operatorId":"op-616"}""";

        mockMvc.perform(patch("/internal/tenants/fan-platform/accounts/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
        assertThat(accountStatus(id)).isEqualTo("ACTIVE");

        mockMvc.perform(patch("/internal/tenants/ecommerce/accounts/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("LOCKED"));
        assertThat(accountStatus(id)).isEqualTo("LOCKED");
        assertThat(jdbc.queryForList("SELECT tenant_id FROM account_status_history WHERE account_id = ? "
                + "AND reason_code = 'OPERATOR_PROVISIONING_STATUS_CHANGE'", String.class, id))
                .containsExactly("consumer-pool");
    }

    @Test
    @DisplayName("GDPR 삭제: fan-platform(비멤버) 404·무변경 → ecommerce(멤버) 200 — TASK-BE-619: 스토어 멤버십만 LEFT, 계정은 그대로 → 플랫폼('*')만 풀 계정을 DELETED·마스킹 · 이벤트 consumer-pool")
    void gdprDelete_nonMemberRefused_siteOperatorLeavesSite_platformErasesThePoolAccount() throws Exception {
        String email = uniqueEmail("616-gdpr");
        String id = storePoolShopper(email);
        String body = """
                {"reason":"REGULATED_DELETION","operatorId":"op-616"}""";

        mockMvc.perform(post("/internal/accounts/{id}/gdpr-delete", id).header("X-Tenant-Id", "fan-platform")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
        assertThat(accountStatus(id)).isEqualTo("ACTIVE");

        // TASK-BE-619 (owner decision 2026-10-03): the store operator ends only the store membership.
        mockMvc.perform(post("/internal/accounts/{id}/gdpr-delete", id).header("X-Tenant-Id", "ecommerce")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("SITE_MEMBERSHIP"));
        assertThat(accountStatus(id)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT email FROM accounts WHERE id = ?", String.class, id)).isEqualTo(email);

        // Erasing the pool account is the platform admin's (no tenant named).
        mockMvc.perform(post("/internal/accounts/{id}/gdpr-delete", id).header("X-Tenant-Id", "*")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("ACCOUNT"));
        assertThat(accountStatus(id)).isEqualTo("DELETED");
        assertThat(jdbc.queryForObject("SELECT tenant_id FROM accounts WHERE id = ?", String.class, id))
                .isEqualTo("consumer-pool");
        assertThat(jdbc.queryForObject("SELECT email FROM accounts WHERE id = ?", String.class, id))
                .isNotEqualTo(email).startsWith("gdpr_");
        List<String> eventTenants = jdbc.queryForList(
                        "SELECT payload FROM account_outbox WHERE aggregate_id = ? AND event_type = 'account.deleted'",
                        String.class, id)
                .stream().map(p -> (String) JsonPath.read(p, "$.tenantId")).toList();
        assertThat(eventTenants).containsExactly("consumer-pool");
    }

    @Test
    @DisplayName("§ 2 역방향: 풀 쇼퍼 이메일로 ecommerce 프로비저닝 → 409 ACCOUNT_ALREADY_EXISTS · 사이트 행 0 / 대조군: 같은 이메일 wms 프로비저닝 → 201")
    void provisioning_poolEmail_refusedOnConsumerSite_allowedOnB2b() throws Exception {
        String email = uniqueEmail("616-provision");
        storePoolShopper(email);
        String body = """
                {"email": "%s", "password": "Password1!", "displayName": "seller", "roles": ["SELLER"]}
                """.formatted(email);

        mockMvc.perform(post("/internal/tenants/{t}/accounts", "ecommerce").header("X-Tenant-Id", "ecommerce")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_ALREADY_EXISTS"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM accounts WHERE email = ? AND tenant_id = 'ecommerce'",
                Integer.class, email)).isZero();

        mockMvc.perform(post("/internal/tenants/{t}/accounts", "wms").header("X-Tenant-Id", "wms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "Password1!", "displayName": "worker", "roles": ["WAREHOUSE_ADMIN"]}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value("wms"));
    }
}
