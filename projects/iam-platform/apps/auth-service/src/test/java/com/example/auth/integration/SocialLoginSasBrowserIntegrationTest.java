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

        // account status + the account's own tenant → ACTIVE in ecommerce. TASK-BE-602: the social
        // path now asks /status-with-tenant (not the fan-platform-pinned /status). Without this stub
        // the lookup would 404 → rule not applied → the happy path would still pass, hiding a
        // wrong path; the stub makes the ACTIVE answer the one the flow actually reads.
        wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo(
                        "/internal/accounts/social-acc-1/status-with-tenant"))
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                { "accountId": "social-acc-1", "tenantId": "ecommerce",
                                  "status": "ACTIVE", "statusChangedAt": "2026-01-01T00:00:00Z" }
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
    com.example.security.oauth2.client.IamClientCredentialsTokenProvider gapTokenProvider;

    // Replace the provider-client selector so the social token+userinfo exchange is hermetic.
    @MockitoBean
    OAuthClientProvider oAuthClientProvider;

    @BeforeEach
    void setUp() {
        Mockito.when(gapTokenProvider.currentBearer()).thenReturn("test-jwt");
        // TASK-BE-605: this class now has two tests sharing the static WireMock server. Its
        // request journal outlives a test, so the "exactly 1 status-with-tenant call" verify
        // below would count the other test's calls whenever JUnit ran it first (the BE-604
        // CORRECTION failure mode). Clears the journal only; the stubs stay.
        wireMock.resetRequests();

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

        // TASK-BE-521 (item A): capture the pre-callback JSESSIONID so the assertion
        // below can prove the social callback rotates it (session-fixation defense).
        // The earlier IT reused this session verbatim and never asserted rotation —
        // exactly the blind spot this ticket closes.
        String preCallbackSessionId = session.getId();

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

        // TASK-BE-521 (item A): the session ID MUST have rotated across the callback.
        // Asserting the resumed redirect alone (above) would NOT catch a missing
        // rotation — the vulnerable path also redirects. Assert the property itself.
        assertThat(session.getId())
                .as("social callback rotates the session ID (fixation defense)")
                .isNotEqualTo(preCallbackSessionId);

        // social_identity row was upserted for the resolved account.
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM social_identities WHERE provider='GOOGLE' "
                        + "AND provider_user_id='google-social-001' AND account_id='social-acc-1'",
                Integer.class);
        assertThat(rows).isEqualTo(1);

        // TASK-BE-602: the social status check asked for the account's own tenant (no tenant
        // header sent) — and did not fall back to the fan-platform-pinned /status.
        wireMock.verify(1, WireMock.getRequestedFor(WireMock.urlPathEqualTo(
                        "/internal/accounts/social-acc-1/status-with-tenant"))
                .withoutHeader("X-Tenant-Id"));
        wireMock.verify(0, WireMock.getRequestedFor(WireMock.urlPathEqualTo(
                "/internal/accounts/social-acc-1/status")));

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
        assertThat(audValues(accessPayload))
                .as("TASK-MONO-696 AC-1: aud == issuing client id (the IT public client)")
                .containsExactly(CLIENT_ID);

        // 7. TASK-MONO-696 AC-1 — the SEEDED web-store client, not the IT stand-in.
        // Same authenticated session (same account, same tenant `ecommerce`), re-driven through
        // /oauth2/authorize with `ecommerce-web-store-client` (V0012, confidential
        // client_secret_basic + PKCE; dev secret pinned by BcryptHashPinTest; callback path
        // /api/auth/callback/iam per V0024). The question is only what `aud` the IdP puts on the
        // token a web-store user actually carries to the ecommerce gateway.
        String wsVerifier = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(UUID.randomUUID().toString().replace("-", "").getBytes(StandardCharsets.UTF_8));
        String wsChallenge = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256")
                        .digest(wsVerifier.getBytes(StandardCharsets.US_ASCII)));
        MvcResult wsAuthorize = mockMvc.perform(get("/oauth2/authorize")
                        .session(toMockSession(session))
                        .queryParam("response_type", "code")
                        .queryParam("client_id", WEB_STORE_CLIENT_ID)
                        .queryParam("redirect_uri", WEB_STORE_REDIRECT_URI)
                        .queryParam("scope", "openid profile email")
                        .queryParam("code_challenge", wsChallenge)
                        .queryParam("code_challenge_method", "S256")
                        .queryParam("state", "browser-state-ws"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String wsRedirect = wsAuthorize.getResponse().getHeader("Location");
        assertThat(wsRedirect).startsWith(WEB_STORE_REDIRECT_URI);
        String wsCode = extractParam(wsRedirect, "code");
        assertThat(wsCode).isNotBlank();

        MvcResult wsToken = mockMvc.perform(post("/oauth2/token")
                        .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                                (WEB_STORE_CLIENT_ID + ":" + WEB_STORE_CLIENT_SECRET)
                                        .getBytes(StandardCharsets.UTF_8)))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", wsCode)
                        .param("redirect_uri", WEB_STORE_REDIRECT_URI)
                        .param("code_verifier", wsVerifier))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode wsPayload = decodeJwtPayload(objectMapper.readTree(
                wsToken.getResponse().getContentAsString()).get("access_token").asText());
        assertThat(audValues(wsPayload))
                .as("TASK-MONO-696 AC-1: web-store access token aud == ecommerce-web-store-client, "
                        + "not the platform name `ecommerce`")
                .containsExactly(WEB_STORE_CLIENT_ID);
        assertThat(wsPayload.get("tenant_id").asText()).isEqualTo("ecommerce");
    }

    /**
     * TASK-BE-605 AC-2 (session-tenant axis) — the social path composes with the SSO tenant gate
     * the same way the form path does: a store (ecommerce) social session opening the fan client
     * ({@code demo-spa-client}, fan-platform) is sent to {@code /login}; signing in socially there
     * stamps the fan client's tenant, so the resumed authorize passes the gate (no loop) and the
     * token is {@code tenant_id=fan-platform} with the fan seed role.
     *
     * <p>🔴 Deliberately NOT asserted: WHICH account row the second login resolves to. Today the
     * identity lookup is global, so it is the ecommerce-born account ({@code social-acc-1}) in a
     * fan-platform session — the spec/code mismatch recorded in multi-tenancy.md § 소셜 로그인.
     * Owner decision ② (iii) scopes that lookup to the client tenant after TASK-MONO-672 item 18
     * measures the population; pinning today's account here would freeze the mismatch.
     */
    @Test
    @DisplayName("BE-605: 스토어 소셜 세션 → 팬 client authorize → /login → 소셜 재로그인 → 코드(루프 없음) · tenant_id=fan-platform")
    void socialSession_openingClientOfAnotherTenant_reauthenticatesSocially() throws Exception {
        String verifier = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(UUID.randomUUID().toString().replace("-", "").getBytes(StandardCharsets.UTF_8));
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));

        // 1. Store (ecommerce) social session.
        MvcResult start = mockMvc.perform(browserAuthorize(null, CLIENT_ID, REDIRECT_URI, challenge))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        HttpSession session = start.getRequest().getSession(false);
        assertThat(start.getResponse().getHeader("Location")).endsWith("/login");
        assertThat(socialCallback(session)).contains("client_id=" + CLIENT_ID);

        // 2. The fan client: the ecommerce session is not reused.
        String fanVerifier = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(UUID.randomUUID().toString().replace("-", "").getBytes(StandardCharsets.UTF_8));
        String fanChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(fanVerifier.getBytes(StandardCharsets.US_ASCII)));
        MvcResult gated = mockMvc.perform(browserAuthorize(
                        toMockSession(session), FAN_CLIENT_ID, FAN_REDIRECT_URI, fanChallenge))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(gated.getResponse().getHeader("Location")).endsWith("/login").doesNotContain("code=");

        // 3. Social login again — the saved request is the fan authorize now.
        assertThat(socialCallback(session)).contains("client_id=" + FAN_CLIENT_ID);

        // 4. No loop: the resumed fan authorize yields a code; the token is the fan tenant's.
        MvcResult resumed = mockMvc.perform(browserAuthorize(
                        toMockSession(session), FAN_CLIENT_ID, FAN_REDIRECT_URI, fanChallenge))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String codeRedirect = resumed.getResponse().getHeader("Location");
        assertThat(codeRedirect).startsWith(FAN_REDIRECT_URI).contains("code=");

        MvcResult token = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", extractParam(codeRedirect, "code"))
                        .param("redirect_uri", FAN_REDIRECT_URI)
                        .param("client_id", FAN_CLIENT_ID)
                        .param("code_verifier", fanVerifier))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode payload = decodeJwtPayload(objectMapper.readTree(
                token.getResponse().getContentAsString()).get("access_token").asText());
        assertThat(payload.get("tenant_id").asText()).isEqualTo("fan-platform");
        java.util.List<String> roles = new java.util.ArrayList<>();
        payload.path("roles").forEach(r -> roles.add(r.asText()));
        assertThat(roles).contains("FAN");
    }

    private static final String FAN_CLIENT_ID = "demo-spa-client";
    private static final String FAN_REDIRECT_URI = "http://localhost:3000/callback";

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder browserAuthorize(
            org.springframework.mock.web.MockHttpSession session, String clientId, String redirectUri,
            String challenge) {
        var builder = get("/oauth2/authorize")
                .accept(MediaType.TEXT_HTML)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", "openid profile email")
                .queryParam("code_challenge", challenge)
                .queryParam("code_challenge_method", "S256")
                .queryParam("state", "be-605");
        return session != null ? builder.session(session) : builder;
    }

    /** /login/oauth/google → mocked Google callback; returns the resume redirect. */
    private String socialCallback(HttpSession session) throws Exception {
        MvcResult startResult = mockMvc.perform(get("/login/oauth/google").session(toMockSession(session)))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String state = extractParam(startResult.getResponse().getHeader("Location"), "state");
        MvcResult callback = mockMvc.perform(get("/login/oauth/google/callback")
                        .session(toMockSession(session))
                        .queryParam("code", "google-auth-code")
                        .queryParam("state", state))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return callback.getResponse().getHeader("Location");
    }

    private static final String WEB_STORE_CLIENT_ID = "ecommerce-web-store-client";
    private static final String WEB_STORE_CLIENT_SECRET = "ecommerce-dev";
    private static final String WEB_STORE_REDIRECT_URI = "http://localhost:3000/api/auth/callback/iam";

    /** `aud` values; Nimbus serializes a single-element audience as a bare string. */
    private static java.util.List<String> audValues(JsonNode payload) {
        JsonNode aud = payload.get("aud");
        assertThat(aud).as("access token must carry aud").isNotNull();
        java.util.List<String> values = new java.util.ArrayList<>();
        if (aud.isArray()) {
            aud.forEach(a -> values.add(a.asText()));
        } else {
            values.add(aud.asText());
        }
        return values;
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
