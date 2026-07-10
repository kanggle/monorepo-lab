package com.example.auth.integration;

import com.example.auth.application.port.OAuthClient;
import com.example.auth.application.port.OAuthClientProvider;
import com.example.auth.domain.oauth.OAuthProvider;
import com.example.auth.domain.oauth.OAuthUserInfo;
import com.example.testsupport.integration.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the SAS browser-session social-login flow (TASK-BE-396, ADR-006 option B).
 *
 * <p>Drives the full sequence end-to-end against real MySQL / Redis / Kafka (Testcontainers):
 * <ol>
 *   <li>{@code GET /oauth2/authorize} (an ecommerce public PKCE client) for an
 *       unauthenticated browser → 302 to {@code /login} (the request is saved in
 *       {@code HttpSessionRequestCache}).</li>
 *   <li>{@code GET /login} renders the social buttons (anchor to
 *       {@code /login/oauth/google}).</li>
 *   <li>{@code GET /login/oauth/google} → 302 to Google (state stored in Redis).</li>
 *   <li>{@code GET /login/oauth/google/callback} (the mocked {@link OAuthClient}
 *       returns a CUSTOMER-eligible userInfo) → the SAS session is established and the
 *       saved {@code /oauth2/authorize} is resumed.</li>
 *   <li>Re-driving {@code /oauth2/authorize} with the established session yields a
 *       {@code code}; {@code POST /oauth2/token} returns an access token whose
 *       {@code roles} claim is {@code [CUSTOMER]} (auto-seeded by RoleSeedPolicy keyed
 *       on the initiating client's platform {@code ecommerce}).</li>
 * </ol>
 *
 * <p>NOTE: a test public PKCE client {@code it-ecommerce-public-client} (NONE auth,
 * {@code tenant_id=ecommerce}) is inserted per-test so the token exchange works without
 * the confidential {@code ecommerce-web-store-client}'s opaque client secret — the
 * platform key (ecommerce) that drives the {@code CUSTOMER} seed is identical.
 *
 * <p>Testcontainers is BLOCKED on the Windows dev host — this IT is written but runs
 * only in CI (skipped locally via {@code AbstractIntegrationTest}'s
 * {@code DockerAvailableCondition}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SocialLoginSasBrowserIntegrationTest extends AbstractIntegrationTest {

    private static final String CLIENT_ID = "it-ecommerce-public-client";
    private static final String REDIRECT_URI = "http://localhost:3000/it/callback";

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    static WireMockServer wireMock;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

        // The test SAS issuer is http://localhost (application-test.yml), so the
        // issuer-derived browser callback is http://localhost/login/oauth/google/callback.
        // Register it as google's allowed redirect (the only provider this IT drives) —
        // the production allowlist carries the iam.local + localhost:8081 variants.
        registry.add("oauth.google.allowed-redirect-uris",
                () -> "http://localhost/login/oauth/google/callback");

        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        registry.add("auth.account-service.base-url", wireMock::baseUrl);

        // Profile lookup for OidcUserInfoMapper (id_token / userinfo).
        wireMock.stubFor(WireMock.get(WireMock.urlMatching("/internal/accounts/.+/profile"))
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "accountId": "social-acc-1",
                                  "email": "social.customer@example.com",
                                  "emailVerified": true,
                                  "displayName": "Social Customer",
                                  "preferredUsername": "socialcustomer",
                                  "locale": "ko-KR",
                                  "tenantId": "ecommerce",
                                  "tenantType": "B2C"
                                }
                                """)));

        // Effective entitled-domains lookup (TenantClaimTokenCustomizer keystone).
        // TASK-BE-491 (ADR-MONO-047 D6): moved to the dedicated endpoint, whose response is
        // already narrowed by the org-node ceiling.
        wireMock.stubFor(WireMock.get(WireMock.urlPathMatching("/internal/tenants/.+/entitled-domains"))
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                { "tenantId": "ecommerce", "domainKeys": [ "ecommerce" ] }
                                """)));

        // account_roles lookup → empty so RoleSeedPolicy seeds [CUSTOMER] on platform=ecommerce.
        wireMock.stubFor(WireMock.get(WireMock.urlMatching("/internal/tenants/.+/accounts/.+/roles"))
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                { "accountId": "social-acc-1", "tenantId": "ecommerce", "roles": [] }
                                """)));

        // social-signup → resolves the social identity to a new born-unified account.
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/internal/accounts/social-signup"))
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                { "accountId": "social-acc-1", "accountStatus": "ACTIVE", "newAccount": true }
                                """)));

        // account status → ACTIVE.
        wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/internal/accounts/social-acc-1/status"))
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                { "accountId": "social-acc-1", "status": "ACTIVE",
                                  "statusChangedAt": "2026-01-01T00:00:00Z" }
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // GAP client_credentials Bearer is minted via a SAS self-call unreachable in MockMvc.
    @MockitoBean
    com.example.auth.infrastructure.client.IamClientCredentialsTokenProvider gapTokenProvider;

    // Replace the provider-client selector so the social token+userinfo exchange is hermetic.
    @MockitoBean
    OAuthClientProvider oAuthClientProvider;

    @BeforeEach
    void setUp() {
        Mockito.when(gapTokenProvider.currentBearer()).thenReturn("test-jwt");

        // Mocked Google client returns a CUSTOMER-eligible userInfo (any code).
        OAuthClient googleClient = Mockito.mock(OAuthClient.class);
        Mockito.when(googleClient.exchangeCodeForUserInfo(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(new OAuthUserInfo(
                        "google-social-001", "social.customer@example.com",
                        "Social Customer", OAuthProvider.GOOGLE));
        Mockito.when(oAuthClientProvider.getClient(OAuthProvider.GOOGLE)).thenReturn(googleClient);

        // Insert a test public PKCE client scoped to tenant_id=ecommerce so the token
        // exchange needs no client secret. Platform key (ecommerce) == seed key.
        jdbcTemplate.update("DELETE FROM oauth_clients WHERE client_id = ?", CLIENT_ID);
        jdbcTemplate.update("""
                INSERT INTO oauth_clients (
                  id, client_id, tenant_id, tenant_type, client_secret_hash, client_name,
                  client_authentication_methods, authorization_grant_types, redirect_uris,
                  scopes, client_settings, token_settings, created_at, updated_at
                ) VALUES (?, ?, 'ecommerce', 'B2C', NULL, 'IT ecommerce public',
                  '["none"]', '["authorization_code","refresh_token"]', ?,
                  '["openid","profile","email"]',
                  '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":true,"settings.client.require-authorization-consent":false}',
                  '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":false,"settings.token.x509-certificate-bound-access-tokens":false,"settings.token.access-token-time-to-live":["java.time.Duration",900.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",86400.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000],"settings.token.device-code-time-to-live":["java.time.Duration",300.000000000]}',
                  NOW(), NOW())
                """, UUID.randomUUID().toString(), CLIENT_ID, "[\"" + REDIRECT_URI + "\"]");

        jdbcTemplate.update("DELETE FROM social_identities");
    }

    @Test
    @DisplayName("SAS browser social login: /oauth2/authorize → /login → google callback → "
            + "session → resumed authorize → token roles:[CUSTOMER]")
    void socialLogin_sasBrowserFlow_issuesCustomerRoleToken() throws Exception {
        String verifier = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(UUID.randomUUID().toString().replace("-", "").getBytes(StandardCharsets.UTF_8));
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        String challenge = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sha256.digest(verifier.getBytes(StandardCharsets.US_ASCII)));

        // 1. Unauthenticated /oauth2/authorize → 302 to /login (request saved in session).
        // Accept: text/html mimics a browser — the SAS LoginUrlAuthenticationEntryPoint
        // redirect is scoped to text/html requests (AuthorizationServerConfig
        // buildHtmlOnlyRequestMatcher, TASK-MONO-046-1); without it the unauth
        // /oauth2/authorize returns 401 (API-client semantics) instead of redirecting.
        MvcResult authorizeRedirect = mockMvc.perform(get("/oauth2/authorize")
                        .accept(MediaType.TEXT_HTML)
                        .queryParam("response_type", "code")
                        .queryParam("client_id", CLIENT_ID)
                        .queryParam("redirect_uri", REDIRECT_URI)
                        .queryParam("scope", "openid profile email")
                        .queryParam("code_challenge", challenge)
                        .queryParam("code_challenge_method", "S256")
                        .queryParam("state", "browser-state-1"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        HttpSession session = authorizeRedirect.getRequest().getSession(false);
        assertThat(session).isNotNull();
        assertThat(authorizeRedirect.getResponse().getHeader("Location")).endsWith("/login");

        // 2. /login renders the Google social button.
        MvcResult loginPage = mockMvc.perform(get("/login").session(toMockSession(session)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(loginPage.getResponse().getContentAsString())
                .contains("/login/oauth/google");

        // 3. /login/oauth/google → 302 to Google (real state stored in Redis).
        // Capture the generated state from the Google authorization URL — the callback
        // must echo it back (the state round-trip through Redis is real, not mocked;
        // only the provider token/userinfo exchange is mocked).
        MvcResult startResult = mockMvc.perform(get("/login/oauth/google").session(toMockSession(session)))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String googleAuthUrl = startResult.getResponse().getHeader("Location");
        String realState = extractParam(googleAuthUrl, "state");
        assertThat(realState).as("state generated + stored by authorize()").isNotBlank();

        // 4. Google callback → session established → 302 back to saved /oauth2/authorize.
        MvcResult callback = mockMvc.perform(get("/login/oauth/google/callback")
                        .session(toMockSession(session))
                        .queryParam("code", "google-auth-code")
                        .queryParam("state", realState))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String resumed = callback.getResponse().getHeader("Location");
        assertThat(resumed).contains("/oauth2/authorize");
        assertThat(resumed).contains("client_id=" + CLIENT_ID);

        // social_identity row was upserted for the resolved account.
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM social_identities WHERE provider='GOOGLE' "
                        + "AND provider_user_id='google-social-001' AND account_id='social-acc-1'",
                Integer.class);
        assertThat(rows).isEqualTo(1);

        // 5. Resume /oauth2/authorize with the now-authenticated session → code.
        MvcResult resumedAuthorize = mockMvc.perform(get("/oauth2/authorize")
                        .session(toMockSession(session))
                        .queryParam("response_type", "code")
                        .queryParam("client_id", CLIENT_ID)
                        .queryParam("redirect_uri", REDIRECT_URI)
                        .queryParam("scope", "openid profile email")
                        .queryParam("code_challenge", challenge)
                        .queryParam("code_challenge_method", "S256")
                        .queryParam("state", "browser-state-2"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String codeRedirect = resumedAuthorize.getResponse().getHeader("Location");
        assertThat(codeRedirect).startsWith(REDIRECT_URI);
        String code = extractParam(codeRedirect, "code");
        assertThat(code).isNotBlank();

        // 6. /oauth2/token (public PKCE) → access token roles:[CUSTOMER].
        MvcResult token = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("client_id", CLIENT_ID)
                        .param("code_verifier", verifier))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode tokenBody = objectMapper.readTree(token.getResponse().getContentAsString());
        JsonNode accessPayload = decodeJwtPayload(tokenBody.get("access_token").asText());

        // ADR-MONO-040 Phase 2 (TASK-MONO-295): `sub` is now the account UUID
        // (jwt-standard-claims.md), not the login email. The resolved social
        // identity's account_id is `social-acc-1` (the social-signup / profile
        // stubs above) — TenantClaimTokenCustomizer overrides `sub` to it. The
        // social email surfaces via the `email` claim, not `sub`.
        assertThat(accessPayload.get("sub").asText()).isEqualTo("social-acc-1");
        assertThat(accessPayload.get("tenant_id").asText()).isEqualTo("ecommerce");
        assertThat(accessPayload.has("roles")).as("roles claim present").isTrue();
        assertThat(accessPayload.get("roles").isArray()).isTrue();
        boolean hasCustomer = false;
        for (JsonNode r : accessPayload.get("roles")) {
            if ("CUSTOMER".equals(r.asText())) { hasCustomer = true; break; }
        }
        assertThat(hasCustomer).as("token roles must contain CUSTOMER (RoleSeedPolicy on ecommerce)").isTrue();
    }

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------

    /**
     * Bridges the container-managed {@link HttpSession} returned by one MockMvc call
     * into a {@code MockHttpSession} reusable on the next call. MockMvc returns a
     * {@code MockHttpSession}, so a direct cast is safe.
     */
    private org.springframework.mock.web.MockHttpSession toMockSession(HttpSession session) {
        return (org.springframework.mock.web.MockHttpSession) session;
    }

    private String extractParam(String url, String name) {
        String query = url.contains("?") ? url.substring(url.indexOf('?') + 1) : url;
        for (String param : query.split("&")) {
            if (param.startsWith(name + "=")) {
                return param.substring((name + "=").length());
            }
        }
        return null;
    }

    private JsonNode decodeJwtPayload(String jwt) throws Exception {
        String[] parts = jwt.split("\\.");
        assertThat(parts).hasSize(3);
        byte[] payloadBytes = Base64.getUrlDecoder().decode(
                parts[1].length() % 4 == 0 ? parts[1] : parts[1] + "=".repeat(4 - parts[1].length() % 4));
        return objectMapper.readTree(payloadBytes);
    }
}
