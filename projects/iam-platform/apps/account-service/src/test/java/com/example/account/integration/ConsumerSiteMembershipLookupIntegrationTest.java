package com.example.account.integration;

import com.example.account.application.port.AuthServicePort;
import com.example.account.infrastructure.outbox.AccountOutboxPublisher;
import com.example.testsupport.integration.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
 *   <li>AC-8 — {@code GET /internal/accounts?excludePoolMembers=true} (admin-service operator
 *       creation) does NOT find a pool shopper in {@code ecommerce}, while the default search (console
 *       account operations, § 5) still does.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("TASK-BE-615 — 사이트 멤버십 조회 · 운영자 생성 확인의 옛 범위 (MySQL, 풀 켜짐)")
class ConsumerSiteMembershipLookupIntegrationTest extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("iam.consumer-pool.enabled", () -> "true");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    @MockitoBean private AuthServicePort authServicePort;
    @MockitoBean @SuppressWarnings("rawtypes") private KafkaTemplate kafkaTemplate;
    @MockitoBean private AccountOutboxPublisher accountOutboxPublisher;

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
    @DisplayName("AC-8: excludePoolMembers=true → 풀 가입 쇼핑객은 ecommerce 에 «없음»; 기본 검색(콘솔 계정 운영)은 여전히 찾는다 — 대조군")
    void operatorCreationSearch_excludesPoolMembers_consoleSearchKeepsThem() throws Exception {
        String email = uniqueEmail("615-ac8-shopper");
        String accountId = poolSignup("ecommerce", email);

        mockMvc.perform(get("/internal/accounts").param("tenantId", "ecommerce").param("email", email)
                        .param("excludePoolMembers", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/internal/accounts").param("tenantId", "ecommerce").param("email", email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(accountId));
    }
}
