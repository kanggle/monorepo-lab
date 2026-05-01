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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for TASK-BE-251 Phase 2c:
 * {@code POST /oauth2/revoke} (RFC 7009) and {@code POST /oauth2/introspect} (RFC 7662).
 *
 * <p>Test coverage:
 * <ol>
 *   <li>client_credentials flow → access token issued → introspect → active=true + tenant claims.</li>
 *   <li>introspect returns active=true for a valid access token with tenant_id + tenant_type claims.</li>
 *   <li>revoke access token → introspect → active=false (RFC 7009 + RFC 7662 combined E2E).</li>
 *   <li>authorization_code flow → refresh_token issued → revoke refresh → introspect → active=false.</li>
 *   <li>introspect with invalid/unknown token → active=false (not an error per RFC 7662 § 2.2).</li>
 *   <li>revoke with wrong client credentials → 401.</li>
 *   <li>regression: POST /api/auth/login still returns deprecation headers.</li>
 * </ol>
 *
 * <p>TASK-BE-251 Phase 2c.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OAuth2RevokeIntrospectIntegrationTest extends AbstractIntegrationTest {

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

    // Shared state across ordered tests
    private static String clientCredentialsAccessToken;
    private static String authCodeAccessToken;
    private static String authCodeRefreshToken;

    // --- Test client credentials ---
    private static final String CC_CLIENT_ID = "test-internal-client";
    private static final String CC_CLIENT_SECRET = "secret";
    private static final String CC_BASIC_AUTH =
            "Basic " + Base64.getEncoder()
                    .encodeToString((CC_CLIENT_ID + ":" + CC_CLIENT_SECRET).getBytes());

    // -----------------------------------------------------------------------
    // 1. Issue client_credentials access token
    // -----------------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("client_credentials: issue access token for introspect tests")
    void clientCredentials_issueAccessToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .header(HttpHeaders.AUTHORIZATION, CC_BASIC_AUTH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", "account.read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        clientCredentialsAccessToken = body.get("access_token").asText();
        assertThat(clientCredentialsAccessToken).isNotBlank();
    }

    // -----------------------------------------------------------------------
    // 2. Introspect valid access token → active=true + tenant claims
    // -----------------------------------------------------------------------

    @Test
    @Order(2)
    @DisplayName("introspect: valid access token → active=true + tenant_id + tenant_type")
    void introspect_validAccessToken_returnsActiveWithTenantClaims() throws Exception {
        assertThat(clientCredentialsAccessToken)
                .as("Requires Order=1 (client_credentials token)")
                .isNotBlank();

        MvcResult result = mockMvc.perform(post("/oauth2/introspect")
                        .header(HttpHeaders.AUTHORIZATION, CC_BASIC_AUTH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", clientCredentialsAccessToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());

        // Standard RFC 7662 fields
        assertThat(body.get("active").asBoolean()).isTrue();
        assertThat(body.has("client_id")).isTrue();
        assertThat(body.get("client_id").asText()).isEqualTo(CC_CLIENT_ID);
        assertThat(body.has("exp")).isTrue();
        assertThat(body.get("exp").asLong()).isGreaterThan(0L);
        assertThat(body.has("iat")).isTrue();
        assertThat(body.has("iss")).isTrue();
        assertThat(body.has("scope")).isTrue();

        // Phase 2c extension: tenant_id + tenant_type (from TenantIntrospectionCustomizer)
        assertThat(body.has("tenant_id"))
                .as("introspect response must contain tenant_id extension claim")
                .isTrue();
        assertThat(body.get("tenant_id").asText()).isEqualTo("fan-platform");

        assertThat(body.has("tenant_type"))
                .as("introspect response must contain tenant_type extension claim")
                .isTrue();
        assertThat(body.get("tenant_type").asText()).isEqualTo("B2C");
    }

    // -----------------------------------------------------------------------
    // 3. Revoke access token → introspect → active=false
    // -----------------------------------------------------------------------

    @Test
    @Order(3)
    @DisplayName("revoke access token → introspect → active=false (RFC 7009 + RFC 7662 E2E)")
    void revoke_thenIntrospect_returnsInactive() throws Exception {
        assertThat(clientCredentialsAccessToken)
                .as("Requires Order=1 (client_credentials token)")
                .isNotBlank();

        // Step 1: revoke the access token (RFC 7009)
        mockMvc.perform(post("/oauth2/revoke")
                        .header(HttpHeaders.AUTHORIZATION, CC_BASIC_AUTH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", clientCredentialsAccessToken))
                .andExpect(status().isOk());

        // Step 2: introspect the revoked token → must return active=false
        MvcResult result = mockMvc.perform(post("/oauth2/introspect")
                        .header(HttpHeaders.AUTHORIZATION, CC_BASIC_AUTH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", clientCredentialsAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("active").asBoolean())
                .as("revoked access token must be reported as inactive by introspect")
                .isFalse();
    }

    // -----------------------------------------------------------------------
    // 4. authorization_code flow → revoke refresh_token → introspect → inactive
    // -----------------------------------------------------------------------

    @Test
    @Order(4)
    @DisplayName("authCode flow: issue refresh_token → revoke → introspect → active=false")
    void authCode_revokeRefreshToken_introspectInactive() throws Exception {
        // PKCE setup
        String codeVerifier = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("revoke-test-verifier-" + UUID.randomUUID())
                        .getBytes(StandardCharsets.UTF_8));
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        String codeChallenge = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sha256.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII)));

        // Authorize
        MvcResult authorizeResult = mockMvc.perform(get("/oauth2/authorize")
                        .with(user("revoke-test-account")
                                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .param("response_type", "code")
                        .param("client_id", "demo-spa-client")
                        .param("redirect_uri", "http://localhost:3000/callback")
                        .param("scope", "openid profile email")
                        .param("code_challenge", codeChallenge)
                        .param("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String location = authorizeResult.getResponse().getHeader("Location");
        assertThat(location).isNotNull().contains("code=");
        String code = extractParam(location, "code");

        // Token exchange
        MvcResult tokenResult = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", "http://localhost:3000/callback")
                        .param("client_id", "demo-spa-client")
                        .param("code_verifier", codeVerifier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                .andReturn();

        JsonNode tokenResponse = objectMapper.readTree(tokenResult.getResponse().getContentAsString());
        authCodeAccessToken = tokenResponse.get("access_token").asText();
        authCodeRefreshToken = tokenResponse.get("refresh_token").asText();

        // Verify access token is initially active (using the internal client for introspect)
        mockMvc.perform(post("/oauth2/introspect")
                        .header(HttpHeaders.AUTHORIZATION, CC_BASIC_AUTH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", authCodeAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        // Revoke the refresh token (public client — no client secret)
        mockMvc.perform(post("/oauth2/revoke")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", authCodeRefreshToken)
                        .param("client_id", "demo-spa-client"))
                .andExpect(status().isOk());

        // Introspect the refresh token — must be inactive after revocation
        // Note: RFC 7662 allows either active=false or 200/active=false for refresh tokens.
        // We verify the refresh token is reported as inactive.
        MvcResult introspectResult = mockMvc.perform(post("/oauth2/introspect")
                        .header(HttpHeaders.AUTHORIZATION, CC_BASIC_AUTH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", authCodeRefreshToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode introspectBody = objectMapper.readTree(introspectResult.getResponse().getContentAsString());
        assertThat(introspectBody.get("active").asBoolean())
                .as("revoked refresh_token must be reported as inactive by introspect")
                .isFalse();
    }

    // -----------------------------------------------------------------------
    // 5. Introspect unknown/invalid token → active=false (not an error)
    // -----------------------------------------------------------------------

    @Test
    @Order(5)
    @DisplayName("introspect: unknown token → active=false (RFC 7662 § 2.2)")
    void introspect_unknownToken_returnsInactive() throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth2/introspect")
                        .header(HttpHeaders.AUTHORIZATION, CC_BASIC_AUTH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", "completely-invalid-token-" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("active").asBoolean()).isFalse();
    }

    // -----------------------------------------------------------------------
    // 6. Revoke with wrong client credentials → 401
    // -----------------------------------------------------------------------

    @Test
    @Order(6)
    @DisplayName("revoke: wrong client secret → 401")
    void revoke_wrongClientSecret_returns401() throws Exception {
        String badAuth = "Basic " + Base64.getEncoder()
                .encodeToString((CC_CLIENT_ID + ":wrongsecret").getBytes());

        // Need a fresh token for this test
        MvcResult tokenResult = mockMvc.perform(post("/oauth2/token")
                        .header(HttpHeaders.AUTHORIZATION, CC_BASIC_AUTH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", "account.read"))
                .andExpect(status().isOk())
                .andReturn();

        String freshToken = objectMapper.readTree(tokenResult.getResponse().getContentAsString())
                .get("access_token").asText();

        mockMvc.perform(post("/oauth2/revoke")
                        .header(HttpHeaders.AUTHORIZATION, badAuth)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", freshToken))
                .andExpect(status().isUnauthorized());
    }

    // -----------------------------------------------------------------------
    // 7. Regression: POST /api/auth/login still works + returns deprecation headers
    // -----------------------------------------------------------------------

    @Test
    @Order(7)
    @DisplayName("regression: POST /api/auth/login reachable + returns Deprecation + Sunset headers")
    void regression_legacyLoginReachableWithDeprecationHeaders() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"regression-phase2c@example.com","password":"anypassword"}
                                """))
                .andReturn();

        int status = result.getResponse().getStatus();
        assertThat(status)
                .as("Legacy /api/auth/login must return 4xx (not 404 or 5xx). Got " + status)
                .isBetween(400, 499);
        assertThat(status)
                .as("404 means route missing — SAS must not have captured /api/auth/login")
                .isNotEqualTo(404);

        // Phase 2c: verify deprecation response headers (RFC 8594 + RFC 9745)
        String deprecationHeader = result.getResponse().getHeader("Deprecation");
        String sunsetHeader = result.getResponse().getHeader("Sunset");

        assertThat(deprecationHeader)
                .as("LoginController must emit Deprecation header (RFC 8594)")
                .isEqualTo("true");
        assertThat(sunsetHeader)
                .as("LoginController must emit Sunset header (RFC 9745)")
                .isNotBlank();
        assertThat(sunsetHeader)
                .as("Sunset date must reference 2026-08-01 (ADR-001 D2-b)")
                .contains("2026");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String extractParam(String url, String paramName) {
        String query = url.contains("?") ? url.substring(url.indexOf("?") + 1) : url;
        for (String param : query.split("&")) {
            if (param.startsWith(paramName + "=")) {
                return param.substring(paramName.length() + 1);
            }
        }
        return null;
    }
}
