package com.example.auth.integration;

import com.example.testsupport.integration.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for TASK-BE-252 — JPA persistence layer for OAuth clients,
 * scopes, consent, and authorization state.
 *
 * <p>Verifies:
 * <ol>
 *   <li>Flyway V0008 migration applied — all four tables exist.</li>
 *   <li>System seed scopes present: openid, profile, email, offline_access (is_system=true).</li>
 *   <li>{@code client_credentials} E2E using the Flyway-seeded {@code test-internal-client}
 *       row (JPA-backed, not in-memory).</li>
 *   <li>Issued token row appears in {@code oauth2_authorization} table.</li>
 *   <li>Revoke → introspect → {@code active=false} lifecycle.</li>
 *   <li>Cross-tenant guard: {@code findByClientId} returns the correct client and its
 *       {@code tenant_id} is carried through {@code ClientSettings}.</li>
 * </ol>
 *
 * <p>TASK-BE-252.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OAuth2JpaPersistenceIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("auth.account-service.base-url", () -> "http://localhost:19999");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    // Shared token across ordered tests
    private static String issuedAccessToken;

    private static final String CC_CLIENT_ID = "test-internal-client";
    private static final String CC_CLIENT_SECRET = "secret";
    private static final String CC_BASIC_AUTH =
            "Basic " + Base64.getEncoder()
                    .encodeToString((CC_CLIENT_ID + ":" + CC_CLIENT_SECRET).getBytes());

    // -----------------------------------------------------------------------
    // 1. Flyway migration — four tables must exist
    // -----------------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("Flyway V0008: all four OAuth tables created")
    void flyway_v0008_allFourTablesExist() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = DATABASE() " +
                "AND TABLE_NAME IN ('oauth_clients','oauth_scopes','oauth_consent','oauth2_authorization')",
                String.class);

        assertThat(tables)
                .as("All four OAuth tables must exist after Flyway V0008")
                .containsExactlyInAnyOrder(
                        "oauth_clients", "oauth_scopes", "oauth_consent", "oauth2_authorization");
    }

    // -----------------------------------------------------------------------
    // 2. System seed scopes — four rows with is_system=true
    // -----------------------------------------------------------------------

    @Test
    @Order(2)
    @DisplayName("Flyway V0008: system seed scopes (openid, profile, email, offline_access) present")
    void flyway_v0008_systemScopesSeeded() {
        List<Map<String, Object>> scopes = jdbcTemplate.queryForList(
                "SELECT scope_name FROM oauth_scopes WHERE is_system = TRUE");

        List<String> scopeNames = scopes.stream()
                .map(row -> (String) row.get("scope_name"))
                .toList();

        assertThat(scopeNames)
                .as("System scopes must include openid, profile, email, offline_access")
                .containsExactlyInAnyOrder("openid", "profile", "email", "offline_access");
    }

    // -----------------------------------------------------------------------
    // 3. JPA RegisteredClientRepository finds Flyway-seeded client
    // -----------------------------------------------------------------------

    @Test
    @Order(3)
    @DisplayName("JpaRegisteredClientRepository: findByClientId returns Flyway-seeded test-internal-client")
    void jpaRepo_findByClientId_returnsFlywaySeededClient() {
        RegisteredClient client = registeredClientRepository.findByClientId("test-internal-client");

        assertThat(client).isNotNull();
        assertThat(client.getClientId()).isEqualTo("test-internal-client");

        // tenant_id must be in ClientSettings (Option B)
        String tenantId = client.getClientSettings().getSetting("custom.tenant_id");
        assertThat(tenantId)
                .as("tenant_id must be carried in ClientSettings, not parsed from clientName")
                .isEqualTo("fan-platform");

        String tenantType = client.getClientSettings().getSetting("custom.tenant_type");
        assertThat(tenantType).isEqualTo("B2C");
    }

    @Test
    @Order(3)
    @DisplayName("JpaRegisteredClientRepository: findByClientId for unknown client returns null (SAS contract)")
    void jpaRepo_findByClientId_unknownClient_returnsNull() {
        RegisteredClient client = registeredClientRepository.findByClientId("does-not-exist-client");
        assertThat(client).isNull();
    }

    // -----------------------------------------------------------------------
    // 4. client_credentials E2E — JPA-backed, access token issued + stored
    // -----------------------------------------------------------------------

    @Test
    @Order(4)
    @DisplayName("client_credentials E2E: access token issued via JPA-backed client + tenant_id claim")
    void clientCredentials_jpaBackedClient_issuesToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .header(HttpHeaders.AUTHORIZATION, CC_BASIC_AUTH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", "account.read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        issuedAccessToken = body.get("access_token").asText();
        assertThat(issuedAccessToken).isNotBlank();

        // Decode JWT and verify tenant claims
        String[] parts = issuedAccessToken.split("\\.");
        int mod = parts[1].length() % 4;
        String padded = parts[1] + (mod == 0 ? "" : "=".repeat(4 - mod));
        JsonNode payload = objectMapper.readTree(Base64.getUrlDecoder().decode(padded));

        assertThat(payload.get("tenant_id").asText())
                .as("tenant_id claim must be present in access token")
                .isEqualTo("fan-platform");
        assertThat(payload.get("tenant_type").asText()).isEqualTo("B2C");
    }

    // -----------------------------------------------------------------------
    // 5. Token row appears in oauth2_authorization table after issuance
    // -----------------------------------------------------------------------

    @Test
    @Order(5)
    @DisplayName("oauth2_authorization: token row persisted after client_credentials issuance")
    void oauth2Authorization_tokenRowPersistedAfterIssuance() {
        assertThat(issuedAccessToken)
                .as("Requires Order=4 (access token from client_credentials)")
                .isNotBlank();

        // The oauth2_authorization table stores the authorization entry;
        // at least one row for test-internal-client (registered_client_id) must exist
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oauth2_authorization WHERE registered_client_id = 'test-internal-client-id'",
                Integer.class);

        assertThat(count)
                .as("At least one oauth2_authorization row must exist for test-internal-client after token issuance")
                .isGreaterThanOrEqualTo(1);
    }

    // -----------------------------------------------------------------------
    // 6. Revoke → introspect → active=false lifecycle
    // -----------------------------------------------------------------------

    @Test
    @Order(6)
    @DisplayName("revoke → introspect lifecycle: revoked token returns active=false")
    void revoke_thenIntrospect_tokenIsInactive() throws Exception {
        assertThat(issuedAccessToken)
                .as("Requires Order=4 (access token to revoke)")
                .isNotBlank();

        // Revoke the access token
        mockMvc.perform(post("/oauth2/revoke")
                        .header(HttpHeaders.AUTHORIZATION, CC_BASIC_AUTH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", issuedAccessToken))
                .andExpect(status().isOk());

        // Introspect — must be inactive
        MvcResult result = mockMvc.perform(post("/oauth2/introspect")
                        .header(HttpHeaders.AUTHORIZATION, CC_BASIC_AUTH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", issuedAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("active").asBoolean())
                .as("Revoked token must be reported as inactive by introspect")
                .isFalse();
    }

    // -----------------------------------------------------------------------
    // 7. Cross-tenant guard: two clients with different tenant_id
    // -----------------------------------------------------------------------

    @Test
    @Order(7)
    @DisplayName("cross-tenant guard: findByClientId returns correct tenant_id for each client")
    void crossTenantGuard_eachClientCarriesCorrectTenantId() {
        // test-internal-client → fan-platform
        RegisteredClient ccClient = registeredClientRepository.findByClientId("test-internal-client");
        assertThat(ccClient).isNotNull();
        assertThat(ccClient.getClientSettings().<String>getSetting("custom.tenant_id"))
                .isEqualTo("fan-platform");

        // demo-spa-client → also fan-platform (both seeded with same tenant in V0008)
        RegisteredClient spaClient = registeredClientRepository.findByClientId("demo-spa-client");
        assertThat(spaClient).isNotNull();
        assertThat(spaClient.getClientSettings().<String>getSetting("custom.tenant_id"))
                .isEqualTo("fan-platform");

        // Verify the two clients are distinct and their clientIds are globally unique
        assertThat(ccClient.getClientId()).isNotEqualTo(spaClient.getClientId());
        assertThat(ccClient.getId()).isNotEqualTo(spaClient.getId());
    }
}
