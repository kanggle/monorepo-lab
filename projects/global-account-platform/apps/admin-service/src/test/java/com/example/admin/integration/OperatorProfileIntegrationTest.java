package com.example.admin.integration;

import com.example.admin.support.OperatorJwtTestFixture;
import com.example.testsupport.integration.AbstractIntegrationTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-306 — end-to-end integration test for
 * {@code PATCH /api/admin/operators/me/profile}.
 *
 * <p>Boots admin-service against real MySQL + Kafka (Testcontainers) + Redis,
 * with a WireMock stub replacing account-service's internal tenant endpoints
 * (used by the registry round-trip assertion). Verifies:
 * <ul>
 *   <li>IT-1: set → 204; subsequent registry GET reflects the new value on
 *       the finance product item with exactly one {@code operatorContext}
 *       substring (no cross-product leakage; same regression guard as
 *       TASK-BE-304 AC-3); audit row inserted with the canonical
 *       OPERATOR_PROFILE_UPDATE shape.</li>
 *   <li>IT-2: clear (null) after set → 204; subsequent registry GET no
 *       longer contains {@code operatorContext} anywhere.</li>
 *   <li>IT-3: no Authorization header → 401 TOKEN_INVALID.</li>
 *   <li>IT-4: whitespace-only value → 400 INVALID_REQUEST; column is
 *       unchanged (asserted by following GET shows the prior value).</li>
 * </ul>
 *
 * <p>Skipped automatically when Docker is unavailable
 * ({@code AbstractIntegrationTest} DockerAvailableCondition).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class OperatorProfileIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static WireMockServer wireMock;
    static OperatorJwtTestFixture jwt;
    static String signingKeyPem;

    // Each test uses a per-case unique operator UUID so we never need to
    // DELETE rows out of admin_actions (which is append-only — V0010 trigger
    // `trg_admin_actions_finalize_only` rejects DELETE/UPDATE by design;
    // attempting one yields `SIGNAL SQLSTATE '45000' MESSAGE_TEXT 'DELETE on
    // admin_actions is forbidden (append-only)'`). The per-case UUID gives
    // hermetic audit-row queries without any teardown step.
    private static final String SELF_UUID_IT1 = "00000000-0000-7000-8000-00000be06001";
    private static final String SELF_UUID_IT2 = "00000000-0000-7000-8000-00000be06002";
    private static final String SELF_UUID_IT3 = "00000000-0000-7000-8000-00000be06003";
    private static final String SELF_UUID_IT4 = "00000000-0000-7000-8000-00000be06004";

    @BeforeAll
    static void setupShared() throws IOException {
        jwt = new OperatorJwtTestFixture();

        java.security.PrivateKey pk = extractPrivateKey(jwt);
        signingKeyPem = "-----BEGIN PRIVATE KEY-----\n"
                + java.util.Base64.getMimeEncoder(64, "\n".getBytes())
                        .encodeToString(pk.getEncoded())
                + "\n-----END PRIVATE KEY-----\n";

        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());
    }

    @AfterAll
    static void tearDownShared() {
        if (wireMock != null) wireMock.stop();
    }

    private static java.security.PrivateKey extractPrivateKey(OperatorJwtTestFixture fixture) {
        try {
            var field = OperatorJwtTestFixture.class.getDeclaredField("keyPair");
            field.setAccessible(true);
            java.security.KeyPair kp = (java.security.KeyPair) field.get(fixture);
            return kp.getPrivate();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("admin.jwt.active-signing-kid", () -> "test-key-001");
        registry.add("admin.jwt.signing-keys.test-key-001", () -> signingKeyPem);
        registry.add("admin.jwt.issuer", () -> "admin-service");
        registry.add("admin.jwt.expected-token-type", () -> "admin");
        registry.add("admin.auth-service.base-url", wireMock::baseUrl);
        registry.add("admin.account-service.base-url", wireMock::baseUrl);
        registry.add("admin.security-service.base-url", wireMock::baseUrl);
        // TASK-BE-318b: GAP client_credentials token endpoint → same WireMock (stubbed in stubTenantList).
        registry.add("gap.internal-client.token-uri", () -> wireMock.baseUrl() + "/oauth2/token");
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    /** Single registered + ACTIVE finance tenant — enough for the registry round-trip. */
    private static final String ACTIVE_TENANTS_WITH_FINANCE = """
            {
              "items": [
                {"tenantId":"fan-platform","displayName":"Fan Platform","tenantType":"B2C_CONSUMER",
                 "status":"ACTIVE","createdAt":"2026-04-01T00:00:00Z","updatedAt":"2026-04-01T00:00:00Z"},
                {"tenantId":"finance","displayName":"Finance Platform","tenantType":"B2B_ENTERPRISE",
                 "status":"ACTIVE","createdAt":"2026-04-01T00:00:00Z","updatedAt":"2026-04-01T00:00:00Z"}
              ],
              "page":0,"size":100,"totalElements":2,"totalPages":1
            }
            """;

    @BeforeEach
    void stubTenantList() {
        wireMock.resetAll();
        // TASK-BE-318b: account client fetches a GAP client_credentials Bearer token first.
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/oauth2/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"test-jwt\",\"expires_in\":300,\"token_type\":\"Bearer\"}")));
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/tenants"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(ACTIVE_TENANTS_WITH_FINANCE)));
    }

    /**
     * Idempotent operator seed: INSERTs if absent, no-ops otherwise.
     * {@code admin_actions} is append-only (V0010 trigger
     * {@code trg_admin_actions_finalize_only} rejects DELETE) so we never try
     * to teardown audit rows — instead each test uses its own operator UUID
     * so audit-row queries are hermetic by construction.
     */
    private void seedOperator(String operatorUuid) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_operators WHERE operator_id = ?",
                Integer.class, operatorUuid);
        if (existing == null || existing == 0) {
            jdbcTemplate.update("""
                    INSERT INTO admin_operators
                      (operator_id, tenant_id, email, password_hash, display_name, status,
                       created_at, updated_at, version)
                    VALUES (?, '*', ?, 'x', ?, 'ACTIVE', NOW(6), NOW(6), 0)
                    """, operatorUuid, operatorUuid + "@example.com",
                    "Op " + operatorUuid.substring(operatorUuid.length() - 6));
        } else {
            // Reset the finance column to NULL so tests start from a known baseline.
            jdbcTemplate.update(
                    "UPDATE admin_operators SET finance_default_account_id = NULL WHERE operator_id = ?",
                    operatorUuid);
        }
    }

    private String token(String operatorUuid) {
        return "Bearer " + jwt.operatorToken(operatorUuid);
    }

    // -----------------------------------------------------------------------

    @Test
    @DisplayName("IT-1 set: PATCH /me/profile with a UUID → 204; subsequent registry GET surfaces operatorContext on finance item; audit row written")
    void it1_set_writesColumnAndAuditRow_andSurfacesOnRegistry() throws Exception {
        seedOperator(SELF_UUID_IT1);
        String newValue = "01928c4a-7e9f-7c00-9a40-d2b1f5e8a306";

        mockMvc.perform(patch("/api/admin/operators/me/profile")
                        .header("Authorization", token(SELF_UUID_IT1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorContext":{"defaultAccountId":"%s"}}
                                """.formatted(newValue)))
                .andExpect(status().isNoContent());

        // Column UPDATE landed
        String persisted = jdbcTemplate.queryForObject(
                "SELECT finance_default_account_id FROM admin_operators WHERE operator_id = ?",
                String.class, SELF_UUID_IT1);
        assertThat(persisted).isEqualTo(newValue);

        // Registry GET reflects the new value AND only one operatorContext substring
        String body = mockMvc.perform(get("/api/admin/console/registry")
                        .header("Authorization", token(SELF_UUID_IT1)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body)
                .as("registry envelope must carry the new operatorContext on the finance item")
                .contains("\"operatorContext\":{\"defaultAccountId\":\"" + newValue + "\"}");
        int occurrences = (body.length() - body.replace("operatorContext", "").length())
                / "operatorContext".length();
        assertThat(occurrences)
                .as("regression guard: substring 'operatorContext' must appear exactly once on finance only. body=%s", body)
                .isEqualTo(1);

        // Audit row written with the canonical shape. Hermetic by operator UUID
        // (each IT uses its own UUID, so the count is exact regardless of run
        // order or the append-only admin_actions table state from prior tests).
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT action_code, target_type, target_id, outcome, downstream_detail, reason " +
                "FROM admin_actions WHERE actor_id = ? AND action_code = 'OPERATOR_PROFILE_UPDATE'",
                SELF_UUID_IT1);
        assertThat(rows)
                .as("exactly one OPERATOR_PROFILE_UPDATE row must exist for the IT-1 operator")
                .hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("action_code")).isEqualTo("OPERATOR_PROFILE_UPDATE");
        assertThat(row.get("target_type")).isEqualTo("OPERATOR");
        assertThat(row.get("target_id")).isEqualTo(SELF_UUID_IT1);
        assertThat(row.get("outcome")).isEqualTo("SUCCESS");
        assertThat(row.get("downstream_detail"))
                .as("audit detail MUST be null — the new value is NOT logged into the audit detail column (R4/A3)")
                .isNull();
        assertThat(row.get("reason")).isEqualTo("<self_profile_update>");
    }

    @Test
    @DisplayName("IT-2 clear after set: PATCH /me/profile with null → 204; subsequent registry GET no longer contains operatorContext")
    void it2_clearAfterSet_omitsOperatorContextFromRegistry() throws Exception {
        seedOperator(SELF_UUID_IT2);

        // First set a value
        mockMvc.perform(patch("/api/admin/operators/me/profile")
                        .header("Authorization", token(SELF_UUID_IT2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorContext":{"defaultAccountId":"01928c4a-7e9f-7c00-9a40-d2b1f5e8a306"}}
                                """))
                .andExpect(status().isNoContent());

        // Then explicit clear
        mockMvc.perform(patch("/api/admin/operators/me/profile")
                        .header("Authorization", token(SELF_UUID_IT2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorContext":{"defaultAccountId":null}}
                                """))
                .andExpect(status().isNoContent());

        // Column should now be NULL
        String persisted = jdbcTemplate.queryForObject(
                "SELECT finance_default_account_id FROM admin_operators WHERE operator_id = ?",
                String.class, SELF_UUID_IT2);
        assertThat(persisted).isNull();

        // Registry envelope no longer carries operatorContext anywhere
        String body = mockMvc.perform(get("/api/admin/console/registry")
                        .header("Authorization", token(SELF_UUID_IT2)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body)
                .as("after clear, registry envelope must omit operatorContext. body=%s", body)
                .doesNotContain("operatorContext");
    }

    @Test
    @DisplayName("IT-3 no auth: PATCH /me/profile without Authorization → 401 TOKEN_INVALID")
    void it3_noAuth_returns401() throws Exception {
        // No operator seed needed — the Security filter rejects before any DB read.
        mockMvc.perform(patch("/api/admin/operators/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorContext":{"defaultAccountId":"01928c4a-7e9f-7c00-9a40-d2b1f5e8a306"}}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("IT-4 whitespace-only value → 400 INVALID_REQUEST; column unchanged")
    void it4_whitespaceOnly_returns400_columnUnchanged() throws Exception {
        seedOperator(SELF_UUID_IT4);
        // Seed a known prior value so the unchanged invariant is testable
        String prior = "01928c4a-7e9f-7c00-9a40-d2b1f5e8a000";
        jdbcTemplate.update(
                "UPDATE admin_operators SET finance_default_account_id = ? WHERE operator_id = ?",
                prior, SELF_UUID_IT4);

        mockMvc.perform(patch("/api/admin/operators/me/profile")
                        .header("Authorization", token(SELF_UUID_IT4))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorContext":{"defaultAccountId":"   "}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        // Column unchanged
        String persisted = jdbcTemplate.queryForObject(
                "SELECT finance_default_account_id FROM admin_operators WHERE operator_id = ?",
                String.class, SELF_UUID_IT4);
        assertThat(persisted)
                .as("column must remain at its prior value after a rejected request")
                .isEqualTo(prior);

        // No OPERATOR_PROFILE_UPDATE audit row should have been inserted for
        // this IT's operator UUID (hermetic by per-IT UUID).
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_actions WHERE actor_id = ? " +
                "AND action_code = 'OPERATOR_PROFILE_UPDATE'",
                Integer.class, SELF_UUID_IT4);
        assertThat(count).isZero();
    }
}
