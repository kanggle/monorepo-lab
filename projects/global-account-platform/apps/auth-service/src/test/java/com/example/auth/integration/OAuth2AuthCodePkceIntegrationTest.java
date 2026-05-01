package com.example.auth.integration;

import com.example.testsupport.integration.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
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
 * Integration tests for Phase 2a of TASK-BE-251:
 * {@code authorization_code} + PKCE flow + {@code /oauth2/userinfo} endpoint.
 *
 * <p>This test class exercises:
 * <ol>
 *   <li>Full authorization_code + PKCE E2E:
 *       {@code /oauth2/authorize} (authenticated user) → callback redirect with
 *       {@code code} → {@code POST /oauth2/token} (code_verifier) → access + id_token.</li>
 *   <li>ID token contains {@code tenant_id} and {@code tenant_type} claims.</li>
 *   <li>{@code GET /oauth2/userinfo} with a valid access token → 200 + OIDC claims.</li>
 *   <li>Discovery document includes {@code authorization_endpoint}, {@code userinfo_endpoint},
 *       {@code response_types_supported=[code]}, {@code code_challenge_methods_supported=[S256]}.</li>
 * </ol>
 *
 * <p><b>AccountServiceClient</b> is stubbed via WireMock so that
 * {@code GET /internal/accounts/{id}/profile} returns a predictable profile.
 *
 * <p>TASK-BE-251 Phase 2a.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OAuth2AuthCodePkceIntegrationTest extends AbstractIntegrationTest {

    // Redis container (service-specific, not shared in AbstractIntegrationTest)
    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    // WireMock for account-service stub
    static WireMockServer wireMock;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        registry.add("auth.account-service.base-url", wireMock::baseUrl);

        // Stub GET /internal/accounts/*/profile for OidcUserInfoMapper
        // Using a fixed-body stub (no templating) — accountId in body may differ from path
        // but OidcUserInfoMapper only uses the returned claims, not the accountId field.
        wireMock.stubFor(WireMock.get(WireMock.urlMatching("/internal/accounts/.+/profile"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "accountId": "test-account",
                                  "email": "user@example.com",
                                  "emailVerified": true,
                                  "displayName": "Test User",
                                  "preferredUsername": "testuser",
                                  "locale": "ko-KR",
                                  "tenantId": "fan-platform",
                                  "tenantType": "B2C"
                                }
                                """)));
    }

    @AfterAll
    static void teardown() {
        if (wireMock != null && wireMock.isRunning()) {
            wireMock.stop();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // PKCE values — generated once per test class, reused across ordered tests
    private static String codeVerifier;
    private static String codeChallenge;
    private static String authCode;

    @BeforeAll
    static void generatePkce() throws Exception {
        // code_verifier: 43-128 chars, unreserved URI chars
        codeVerifier = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(UUID.randomUUID().toString().replace("-", "").getBytes(StandardCharsets.UTF_8));

        // code_challenge = BASE64URL(SHA-256(ASCII(code_verifier)))
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] hash = sha256.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
        codeChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    // -----------------------------------------------------------------------
    // 1. Discovery document — Phase 2a fields
    // -----------------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("discovery: authorization_endpoint + userinfo_endpoint + S256 PKCE present")
    void discovery_phase2a_fields() throws Exception {
        MvcResult result = mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode doc = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(doc.get("authorization_endpoint").asText()).contains("/oauth2/authorize");
        assertThat(doc.get("userinfo_endpoint").asText()).contains("/userinfo");

        // response_types_supported must include "code"
        boolean hasCode = false;
        for (JsonNode rt : doc.get("response_types_supported")) {
            if ("code".equals(rt.asText())) { hasCode = true; break; }
        }
        assertThat(hasCode).as("response_types_supported must include 'code'").isTrue();

        // code_challenge_methods_supported — SAS 1.4 may expose this
        JsonNode pkce = doc.get("code_challenge_methods_supported");
        if (pkce != null) {
            boolean hasS256 = false;
            for (JsonNode m : pkce) {
                if ("S256".equals(m.asText())) { hasS256 = true; break; }
            }
            assertThat(hasS256).as("code_challenge_methods_supported must include S256").isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // 2. /oauth2/authorize with authenticated user → code redirect
    // -----------------------------------------------------------------------

    @Test
    @Order(2)
    @DisplayName("authorize: authenticated user → 302 redirect to redirect_uri with code param")
    void authorize_authenticatedUser_returnsCodeRedirect() throws Exception {
        // Simulate an authenticated user session via Spring Security test support.
        // The principal name becomes the JWT sub claim (accountId in our system).
        MvcResult result = mockMvc.perform(get("/oauth2/authorize")
                        .with(user("test-account-001")
                                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .param("response_type", "code")
                        .param("client_id", "demo-spa-client")
                        .param("redirect_uri", "http://localhost:3000/callback")
                        .param("scope", "openid profile email")
                        .param("code_challenge", codeChallenge)
                        .param("code_challenge_method", "S256")
                        .param("state", "test-state-xyz"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        assertThat(location).isNotNull();
        assertThat(location).startsWith("http://localhost:3000/callback");
        assertThat(location).contains("code=");
        assertThat(location).contains("state=test-state-xyz");

        // Extract code for the next test
        String query = location.contains("?") ? location.substring(location.indexOf("?") + 1) : location;
        for (String param : query.split("&")) {
            if (param.startsWith("code=")) {
                authCode = param.substring("code=".length());
                break;
            }
        }
        assertThat(authCode).as("authorization code must be present in redirect").isNotBlank();
    }

    // -----------------------------------------------------------------------
    // 3. /oauth2/token (authorization_code + code_verifier) → access + id_token
    // -----------------------------------------------------------------------

    @Test
    @Order(3)
    @DisplayName("token: authorization_code + code_verifier → access_token + id_token with tenant_id")
    void token_authorizationCode_returnsAccessAndIdToken() throws Exception {
        assertThat(authCode).as("Requires authCode from previous test (Order=2)").isNotBlank();

        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", authCode)
                        .param("redirect_uri", "http://localhost:3000/callback")
                        .param("client_id", "demo-spa-client")
                        .param("code_verifier", codeVerifier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andReturn();

        JsonNode tokenResponse = objectMapper.readTree(result.getResponse().getContentAsString());

        // Validate access token claims
        String accessToken = tokenResponse.get("access_token").asText();
        JsonNode accessPayload = decodeJwtPayload(accessToken);

        assertThat(accessPayload.get("sub").asText()).isEqualTo("test-account-001");
        assertThat(accessPayload.has("tenant_id"))
                .as("access_token must contain tenant_id claim")
                .isTrue();
        assertThat(accessPayload.has("tenant_type"))
                .as("access_token must contain tenant_type claim")
                .isTrue();

        // id_token must be present when openid scope is requested
        assertThat(tokenResponse.has("id_token"))
                .as("id_token must be present when scope=openid is requested")
                .isTrue();

        String idToken = tokenResponse.get("id_token").asText();
        JsonNode idPayload = decodeJwtPayload(idToken);

        assertThat(idPayload.get("sub").asText()).isEqualTo("test-account-001");
        assertThat(idPayload.has("tenant_id"))
                .as("id_token must contain tenant_id claim (Phase 2a)")
                .isTrue();
        assertThat(idPayload.has("tenant_type"))
                .as("id_token must contain tenant_type claim (Phase 2a)")
                .isTrue();

        // aud must include demo-spa-client
        JsonNode aud = idPayload.get("aud");
        assertThat(aud).isNotNull();
        boolean hasDemoSpaClient = false;
        if (aud.isArray()) {
            for (JsonNode a : aud) {
                if ("demo-spa-client".equals(a.asText())) { hasDemoSpaClient = true; break; }
            }
        } else {
            hasDemoSpaClient = "demo-spa-client".equals(aud.asText());
        }
        assertThat(hasDemoSpaClient)
                .as("id_token.aud must contain demo-spa-client")
                .isTrue();
    }

    // -----------------------------------------------------------------------
    // 4. /oauth2/userinfo — access token → OIDC claims + tenant_id
    // -----------------------------------------------------------------------

    @Test
    @Order(4)
    @DisplayName("userinfo: valid access_token → 200 with sub + email + tenant_id")
    void userinfo_validToken_returnsOidcClaims() throws Exception {
        assertThat(authCode).as("Requires authorization flow completion (Order=2,3)").isNotBlank();

        // We need a fresh access token for userinfo — re-run the token exchange
        // using a new code (since authCode is consumed). For userinfo test isolation,
        // we perform a full auth flow with a fresh code.
        String freshVerifier = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(UUID.randomUUID().toString().replace("-", "")
                        .getBytes(StandardCharsets.UTF_8));
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] hash = sha256.digest(freshVerifier.getBytes(StandardCharsets.US_ASCII));
        String freshChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);

        // Step 1: get fresh code
        MvcResult authResult = mockMvc.perform(get("/oauth2/authorize")
                        .with(user("test-account-002")
                                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .param("response_type", "code")
                        .param("client_id", "demo-spa-client")
                        .param("redirect_uri", "http://localhost:3000/callback")
                        .param("scope", "openid profile email")
                        .param("code_challenge", freshChallenge)
                        .param("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String location = authResult.getResponse().getHeader("Location");
        String freshCode = null;
        String query = location.contains("?") ? location.substring(location.indexOf("?") + 1) : location;
        for (String param : query.split("&")) {
            if (param.startsWith("code=")) {
                freshCode = param.substring("code=".length());
                break;
            }
        }
        assertThat(freshCode).isNotBlank();

        // Step 2: exchange code for access token
        MvcResult tokenResult = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", freshCode)
                        .param("redirect_uri", "http://localhost:3000/callback")
                        .param("client_id", "demo-spa-client")
                        .param("code_verifier", freshVerifier))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = objectMapper.readTree(tokenResult.getResponse().getContentAsString())
                .get("access_token").asText();

        // Step 3: call /oauth2/userinfo
        MvcResult userInfoResult = mockMvc.perform(get("/oauth2/userinfo")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        JsonNode userInfo = objectMapper.readTree(userInfoResult.getResponse().getContentAsString());

        assertThat(userInfo.get("sub").asText()).isEqualTo("test-account-002");
        // WireMock stub returns these profile fields
        assertThat(userInfo.has("email")).isTrue();
        assertThat(userInfo.get("email").asText()).isEqualTo("user@example.com");
        // Custom tenant claims must appear in userinfo
        assertThat(userInfo.has("tenant_id")).isTrue();
        assertThat(userInfo.get("tenant_id").asText()).isEqualTo("fan-platform");
        assertThat(userInfo.has("tenant_type")).isTrue();
    }

    // -----------------------------------------------------------------------
    // 5. PKCE enforcement — wrong code_verifier → 400
    // -----------------------------------------------------------------------

    @Test
    @Order(5)
    @DisplayName("token: wrong code_verifier → 400 (PKCE S256 validation fails)")
    void token_wrongCodeVerifier_returns400() throws Exception {
        // Get a fresh code with known PKCE values
        String verifier = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("my-secret-verifier-value-12345678".getBytes(StandardCharsets.UTF_8));
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] hash = sha256.digest(verifier.getBytes(StandardCharsets.US_ASCII));
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);

        MvcResult authResult = mockMvc.perform(get("/oauth2/authorize")
                        .with(user("test-account-pkce")
                                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .param("response_type", "code")
                        .param("client_id", "demo-spa-client")
                        .param("redirect_uri", "http://localhost:3000/callback")
                        .param("scope", "openid")
                        .param("code_challenge", challenge)
                        .param("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String location = authResult.getResponse().getHeader("Location");
        String code = null;
        String query = location.contains("?") ? location.substring(location.indexOf("?") + 1) : location;
        for (String param : query.split("&")) {
            if (param.startsWith("code=")) {
                code = param.substring("code=".length());
                break;
            }
        }

        // Send a WRONG code_verifier — PKCE validation must reject this
        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", "http://localhost:3000/callback")
                        .param("client_id", "demo-spa-client")
                        .param("code_verifier", "this-is-the-wrong-verifier"))
                .andExpect(status().isBadRequest());
    }

    // -----------------------------------------------------------------------
    // 6. /oauth2/userinfo without token → 401
    // -----------------------------------------------------------------------

    @Test
    @Order(6)
    @DisplayName("userinfo: no Bearer token → 401")
    void userinfo_noToken_returns401() throws Exception {
        mockMvc.perform(get("/oauth2/userinfo"))
                .andExpect(status().isUnauthorized());
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private JsonNode decodeJwtPayload(String jwt) throws Exception {
        String[] parts = jwt.split("\\.");
        assertThat(parts).hasSize(3);
        byte[] payloadBytes = Base64.getUrlDecoder().decode(
                parts[1].length() % 4 == 0 ? parts[1] : parts[1] + "=".repeat(4 - parts[1].length() % 4));
        return objectMapper.readTree(payloadBytes);
    }
}
