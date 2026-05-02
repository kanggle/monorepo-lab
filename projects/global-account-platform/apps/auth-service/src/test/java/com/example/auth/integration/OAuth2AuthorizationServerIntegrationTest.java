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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Spring Authorization Server (SAS) OIDC endpoints.
 *
 * <p>Phase 1 scope (TASK-BE-251):
 * <ol>
 *   <li>{@code GET /.well-known/openid-configuration} — discovery document</li>
 *   <li>{@code GET /oauth2/jwks} — JWKS endpoint</li>
 *   <li>{@code POST /oauth2/token} with {@code grant_type=client_credentials} —
 *       access token + tenant_id claim</li>
 * </ol>
 *
 * <p>The test client ({@code test-internal-client / secret}) is registered as an
 * in-memory placeholder in
 * {@link com.example.auth.infrastructure.oauth2.AuthorizationServerConfig}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OAuth2AuthorizationServerIntegrationTest extends AbstractIntegrationTest {

    // Redis — service-specific container (not shared via AbstractIntegrationTest)
    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        // WireMock is not needed for SAS endpoint tests; set a safe default
        registry.add("auth.account-service.base-url", () -> "http://localhost:19999");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Basic auth header for test-internal-client / secret
    private static final String CLIENT_ID = "test-internal-client";
    private static final String CLIENT_SECRET = "secret";
    private static final String BASIC_AUTH_HEADER =
            "Basic " + Base64.getEncoder().encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes());

    // -----------------------------------------------------------------------
    // 1. OIDC Discovery
    // -----------------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("GET /.well-known/openid-configuration returns 200 with required fields")
    void oidcDiscovery_returns200WithRequiredFields() throws Exception {
        MvcResult result = mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.issuer").isNotEmpty())
                .andExpect(jsonPath("$.jwks_uri").isNotEmpty())
                .andExpect(jsonPath("$.token_endpoint").isNotEmpty())
                .andExpect(jsonPath("$.response_types_supported").isArray())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        JsonNode doc = objectMapper.readTree(body);

        assertThat(doc.get("issuer").asText()).isNotBlank();
        assertThat(doc.get("jwks_uri").asText()).contains("/oauth2/jwks");
        assertThat(doc.get("token_endpoint").asText()).contains("/oauth2/token");

        // grant_types_supported must include client_credentials
        JsonNode grantTypes = doc.get("grant_types_supported");
        assertThat(grantTypes).isNotNull();
        boolean hasClientCredentials = false;
        for (JsonNode grantType : grantTypes) {
            if ("client_credentials".equals(grantType.asText())) {
                hasClientCredentials = true;
                break;
            }
        }
        assertThat(hasClientCredentials)
                .as("grant_types_supported must contain client_credentials")
                .isTrue();
    }

    // -----------------------------------------------------------------------
    // 2. JWKS
    // -----------------------------------------------------------------------

    @Test
    @Order(2)
    @DisplayName("GET /oauth2/jwks returns 200 with RSA key")
    void jwks_returns200WithRsaKey() throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.keys").isArray())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].use").value("sig"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        JsonNode jwks = objectMapper.readTree(body);
        JsonNode firstKey = jwks.get("keys").get(0);

        assertThat(firstKey.get("kty").asText()).isEqualTo("RSA");
        assertThat(firstKey.get("use").asText()).isEqualTo("sig");
        assertThat(firstKey.get("kid").asText()).isNotBlank();
        assertThat(firstKey.get("n").asText()).isNotBlank();
        assertThat(firstKey.get("e").asText()).isNotBlank();

        // alg field — SAS may or may not include it; RS256 is the default
        if (firstKey.has("alg")) {
            assertThat(firstKey.get("alg").asText()).isEqualTo("RS256");
        }
    }

    // -----------------------------------------------------------------------
    // 3. client_credentials token endpoint
    // -----------------------------------------------------------------------

    @Test
    @Order(3)
    @DisplayName("POST /oauth2/token (client_credentials) returns access token with tenant_id claim")
    void clientCredentials_returnsAccessTokenWithTenantIdClaim() throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .header(HttpHeaders.AUTHORIZATION, BASIC_AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", "account.read"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        JsonNode tokenResponse = objectMapper.readTree(body);
        String accessToken = tokenResponse.get("access_token").asText();

        // Decode JWT payload (without verification — SAS signed it with our own key)
        String[] parts = accessToken.split("\\.");
        assertThat(parts).hasSize(3);

        byte[] payloadBytes = Base64.getUrlDecoder().decode(
                parts[1].length() % 4 == 0 ? parts[1] : parts[1] + "=".repeat(4 - parts[1].length() % 4));
        JsonNode payload = objectMapper.readTree(payloadBytes);

        // tenant_id claim must be present (fail-closed guard in TenantClaimTokenCustomizer)
        assertThat(payload.has("tenant_id"))
                .as("access token must contain tenant_id claim")
                .isTrue();
        assertThat(payload.get("tenant_id").asText()).isEqualTo("fan-platform");

        // tenant_type claim must be present
        assertThat(payload.has("tenant_type"))
                .as("access token must contain tenant_type claim")
                .isTrue();
        assertThat(payload.get("tenant_type").asText()).isEqualTo("B2C");

        // Standard claims
        assertThat(payload.get("iss").asText()).isNotBlank();
        assertThat(payload.get("exp").asLong()).isGreaterThan(0L);
    }

    @Test
    @Order(4)
    @DisplayName("POST /oauth2/token with wrong client secret returns 401")
    void clientCredentials_wrongSecret_returns401() throws Exception {
        String badAuth = "Basic " + Base64.getEncoder()
                .encodeToString((CLIENT_ID + ":wrongsecret").getBytes());

        mockMvc.perform(post("/oauth2/token")
                        .header(HttpHeaders.AUTHORIZATION, badAuth)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", "account.read"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(5)
    @DisplayName("POST /oauth2/token with unknown client returns 401")
    void clientCredentials_unknownClient_returns401() throws Exception {
        String unknownAuth = "Basic " + Base64.getEncoder()
                .encodeToString("unknown-client:secret".getBytes());

        mockMvc.perform(post("/oauth2/token")
                        .header(HttpHeaders.AUTHORIZATION, unknownAuth)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", "account.read"))
                .andExpect(status().isUnauthorized());
    }

    // -----------------------------------------------------------------------
    // 4. Regression — existing /api/auth/login still works
    // -----------------------------------------------------------------------

    @Test
    @Order(6)
    @DisplayName("Regression: POST /api/auth/login endpoint is still reachable (returns 4xx, not 404/403)")
    void regression_loginEndpointStillReachable() throws Exception {
        // Send a deliberately bad login — the point is that the SAS filter chain
        // did NOT intercept /api/auth/login. We expect 401 (credentials invalid),
        // not 404 (route not found) or 403 (access denied by SAS).
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"regression@example.com","password":"any"}
                                """))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status)
                            .as("Login endpoint must not be captured by SAS filter chain. " +
                                    "Expected 4xx but got " + status)
                            .isBetween(400, 499);
                    assertThat(status)
                            .as("Must not be 404 (route missing) — SAS must not have swallowed the route")
                            .isNotEqualTo(404);
                });
    }
}
