package com.example.auth.integration;

import com.example.auth.application.port.OAuthClient;
import com.example.auth.application.port.OAuthClientProvider;
import com.example.auth.domain.oauth.OAuthProvider;
import com.example.auth.domain.oauth.OAuthUserInfo;
import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import com.example.testsupport.integration.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-617 (ADR-MONO-078 D2 · D4; oauth-social-login.md § 계정 연결 전략; multi-tenancy.md § 소비자 계정 풀)
 * — social login on the consumer pool, driven through the real browser path: {@code /oauth2/authorize} →
 * {@code /login/oauth/google} → callback (real Redis state, mocked provider exchange) → resumed authorize →
 * code → token. account-service is WireMock: its answers are the ones {@code ConsumerPoolSocialSignupIntegrationTest}
 * (account-service, MySQL) proves the real service gives.
 *
 * <ul>
 *   <li><b>AC-1</b> — a new fan social signup is a POOL principal: the identity row is {@code consumer-pool},
 *       the fan token is {@code tenant_id=fan-platform} with the pool account as {@code sub}, and the same
 *       session on the store client gets the store's first-visit consent screen — no {@code /login}.</li>
 *   <li><b>AC-1</b> — a returning pool identity on the store client resolves to the same pool account
 *       ({@code sub} identical) with {@code tenant_id=ecommerce}.</li>
 *   <li>🔴 <b>AC-2</b> — an email equal to a PASSWORD pool account's: account-service refuses
 *       ({@code 409 ACCOUNT_ALREADY_EXISTS}) → {@code /login?error=email_registered}, no identity row, no session.</li>
 *   <li><b>AC-3</b> — a pre-ADR-MONO-078 SITE identity (fan-platform row) still resolves to its site account
 *       with no social-signup call.</li>
 * </ul>
 *
 * <p>Testcontainers is unavailable on the Windows dev host — written here, run by CI's iam integration job.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConsumerPoolSocialLoginIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static WireMockServer accountService;

    private static final String FAN_CLIENT_ID = "demo-spa-client";
    private static final String FAN_REDIRECT_URI = "http://localhost:3000/callback";
    private static final String STORE_CLIENT_ID = "ecommerce-web-store-client";
    private static final String STORE_CLIENT_SECRET = "ecommerce-dev";
    private static final String STORE_REDIRECT_URI = "http://localhost:3000/api/auth/callback/iam";

    /** AC-1: signs up socially at the fan site → a pool account (member of fan only). */
    private static final String NEW_POOL = "0199de70-0000-7000-8000-0000000a0617";
    private static final String NEW_POOL_EMAIL = "social-new-617@example.com";
    private static final String NEW_POOL_UID = "google-new-617";
    /** AC-1: already has a consumer-pool identity row; member of the store. */
    private static final String RETURNING_POOL = "0199de70-0000-7000-8000-0000000b0617";
    private static final String RETURNING_POOL_EMAIL = "social-returning-617@example.com";
    private static final String RETURNING_POOL_UID = "google-returning-617";
    /** AC-2: the email of an existing password pool account — the provider asserts it. */
    private static final String VICTIM_EMAIL = "password-pool-617@example.com";
    private static final String ATTACKER_UID = "google-attacker-617";
    /** AC-3: a pre-078 fan SITE account with a fan-platform identity row. */
    private static final String SITE_ACCOUNT = "0199de70-0000-7000-8000-0000000c0617";
    private static final String SITE_EMAIL = "social-site-617@example.com";
    private static final String SITE_UID = "google-site-617";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("oauth.google.allowed-redirect-uris", () -> "http://localhost/login/oauth/google/callback");
        // TASK-BE-623: the test-* default pair reads as «not configured», which blocks /login/oauth/google
        // before it reaches the flow this IT drives — the same override SocialLoginSasBrowserIntegrationTest has.
        registry.add("oauth.google.client-id", () -> "it-google-client-id");
        registry.add("oauth.google.client-secret", () -> "it-google-client-secret");

        accountService = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        accountService.start();
        registry.add("auth.account-service.base-url", accountService::baseUrl);

        accountService.stubFor(WireMock.get(WireMock.urlPathMatching("/internal/tenants/.+/entitled-domains"))
                .willReturn(json("{ \"domainKeys\": [] }")));
        accountService.stubFor(WireMock.get(WireMock.urlPathMatching("/internal/tenants/.+/accounts/.+/roles"))
                .willReturn(json("{ \"roles\": [] }")));
        accountService.stubFor(WireMock.get(WireMock.urlMatching("/internal/accounts/.+/profile"))
                .willReturn(json("{ \"accountId\": \"x\", \"email\": \"x@example.com\", \"locale\": \"ko-KR\" }")));

        statusWithTenant(NEW_POOL, "consumer-pool");
        statusWithTenant(RETURNING_POOL, "consumer-pool");
        statusWithTenant(SITE_ACCOUNT, "fan-platform");

        membership("fan-platform", NEW_POOL, "\"ACTIVE\"");
        membership("ecommerce", NEW_POOL, "null");
        membership("ecommerce", RETURNING_POOL, "\"ACTIVE\"");
        membership("fan-platform", RETURNING_POOL, "null");

        // account-service's social-signup answers, keyed on the provider-asserted email.
        accountService.stubFor(WireMock.post(WireMock.urlPathEqualTo("/internal/accounts/social-signup"))
                .withRequestBody(WireMock.matchingJsonPath("$.email", WireMock.equalTo(NEW_POOL_EMAIL)))
                .willReturn(WireMock.aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("""
                                { "accountId": "%s", "email": "%s", "status": "ACTIVE", "tenantId": "consumer-pool" }
                                """.formatted(NEW_POOL, NEW_POOL_EMAIL))));
        accountService.stubFor(WireMock.post(WireMock.urlPathEqualTo("/internal/accounts/social-signup"))
                .withRequestBody(WireMock.matchingJsonPath("$.email", WireMock.equalTo(VICTIM_EMAIL)))
                .willReturn(WireMock.aResponse().withStatus(409).withHeader("Content-Type", "application/json")
                        .withBody("""
                                { "code": "ACCOUNT_ALREADY_EXISTS", "message": "Account already exists" }
                                """)));
    }

    private static ResponseDefinitionBuilder json(String body) {
        return WireMock.aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }

    private static void statusWithTenant(String accountId, String tenant) {
        accountService.stubFor(WireMock.get(WireMock.urlPathEqualTo("/internal/accounts/" + accountId + "/status-with-tenant"))
                .willReturn(json("""
                        { "accountId": "%s", "tenantId": "%s", "status": "ACTIVE", "statusChangedAt": "2026-10-01T00:00:00Z" }
                        """.formatted(accountId, tenant))));
    }

    private static void membership(String site, String accountId, String statusJson) {
        accountService.stubFor(WireMock.get(WireMock.urlPathEqualTo(
                        "/internal/tenants/" + site + "/consumer-members/" + accountId))
                .willReturn(json("""
                        { "accountId": "%s", "siteTenantId": "%s", "consumerSite": true,
                          "siteTenantType": "B2C_CONSUMER", "membershipStatus": %s, "siteRoles": [] }
                        """.formatted(accountId, site, statusJson))));
    }

    @AfterAll
    static void stopAccountService() {
        if (accountService != null && accountService.isRunning()) {
            accountService.stop();
        }
    }

    @MockitoBean IamClientCredentialsTokenProvider gapTokenProvider;
    @MockitoBean OAuthClientProvider oAuthClientProvider;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final OAuthClient googleClient = Mockito.mock(OAuthClient.class);

    @BeforeEach
    void setUp() {
        Mockito.when(gapTokenProvider.currentBearer()).thenReturn("test-jwt");
        Mockito.when(oAuthClientProvider.getClient(OAuthProvider.GOOGLE)).thenReturn(googleClient);
        accountService.resetRequests();
        jdbcTemplate.update("DELETE FROM social_identities");
        jdbcTemplate.update("INSERT INTO social_identities (account_id, tenant_id, provider, provider_user_id, "
                + "provider_email, connected_at, last_used_at) VALUES (?, 'consumer-pool', 'GOOGLE', ?, ?, NOW(6), NOW(6))",
                RETURNING_POOL, RETURNING_POOL_UID, RETURNING_POOL_EMAIL);
        jdbcTemplate.update("INSERT INTO social_identities (account_id, tenant_id, provider, provider_user_id, "
                + "provider_email, connected_at, last_used_at) VALUES (?, 'fan-platform', 'GOOGLE', ?, ?, NOW(6), NOW(6))",
                SITE_ACCOUNT, SITE_UID, SITE_EMAIL);
    }

    private void providerAsserts(String uid, String email) {
        Mockito.when(googleClient.exchangeCodeForUserInfo(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(new OAuthUserInfo(uid, email, "Social User", OAuthProvider.GOOGLE));
    }

    // ── AC-1 ──────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-1: 팬 소셜 신규 가입 → 신원 행 consumer-pool · 팬 토큰 sub=풀 계정 · tenant_id=fan-platform → 같은 세션의 스토어는 동의 화면(재로그인 없음)")
    void newFanSocialSignup_poolPrincipal_storeShowsConsentNotLogin() throws Exception {
        providerAsserts(NEW_POOL_UID, NEW_POOL_EMAIL);
        Pkce fanPkce = Pkce.create();
        MockHttpSession session = startAt(FAN_CLIENT_ID, FAN_REDIRECT_URI, fanPkce);

        assertThat(socialCallback(session)).contains("/oauth2/authorize").contains("client_id=" + FAN_CLIENT_ID);

        assertThat(jdbcTemplate.queryForList(
                "SELECT tenant_id FROM social_identities WHERE provider_user_id = ?", String.class, NEW_POOL_UID))
                .as("the new identity row lives in the pool, not in fan-platform")
                .containsExactly("consumer-pool");

        String fanAccess = exchange(authorizeExpectingCode(session, FAN_CLIENT_ID, FAN_REDIRECT_URI, fanPkce),
                FAN_CLIENT_ID, FAN_REDIRECT_URI, fanPkce).get("access_token").asText();
        assertThat(claim(fanAccess, "sub")).isEqualTo(NEW_POOL);
        assertThat(claim(fanAccess, "tenant_id")).isEqualTo("fan-platform").isNotEqualTo("consumer-pool");
        assertThat(roles(fanAccess)).contains("FAN");

        // The store: no membership yet → the first-visit consent screen, never the login form.
        MvcResult store = mockMvc.perform(authorize(session, STORE_CLIENT_ID, STORE_REDIRECT_URI, Pkce.create()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(store.getResponse().getHeader("Location"))
                .as("consent only — no re-login, no code before consent")
                .endsWith("/consent")
                .doesNotContain("/login")
                .doesNotContain("code=");
    }

    @Test
    @DisplayName("AC-1: 풀 신원으로 스토어 client 로그인 → 같은 풀 계정(sub) · tenant_id=ecommerce · 가입 호출 없음")
    void returningPoolIdentity_onStore_sameAccount() throws Exception {
        providerAsserts(RETURNING_POOL_UID, RETURNING_POOL_EMAIL);
        Pkce storePkce = Pkce.create();
        MockHttpSession session = startAt(STORE_CLIENT_ID, STORE_REDIRECT_URI, storePkce);

        socialCallback(session);

        String storeAccess = exchange(authorizeExpectingCode(session, STORE_CLIENT_ID, STORE_REDIRECT_URI, storePkce),
                STORE_CLIENT_ID, STORE_REDIRECT_URI, storePkce).get("access_token").asText();
        assertThat(claim(storeAccess, "sub")).isEqualTo(RETURNING_POOL);
        assertThat(claim(storeAccess, "tenant_id")).isEqualTo("ecommerce");
        assertThat(roles(storeAccess)).contains("CUSTOMER").doesNotContain("FAN");

        accountService.verify(0, WireMock.postRequestedFor(WireMock.urlPathEqualTo("/internal/accounts/social-signup")));
        assertThat(jdbcTemplate.queryForList(
                "SELECT tenant_id FROM social_identities WHERE provider_user_id = ?", String.class, RETURNING_POOL_UID))
                .as("no ecommerce row beside the pool row").containsExactly("consumer-pool");
    }

    // ── AC-2 ──────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("🔴 AC-2 대조군: 비밀번호 풀 계정의 이메일을 주장하는 소셜 → /login?error=email_registered · 신원 행 0 · 인증 세션 없음")
    void socialEmailOfPasswordPoolAccount_notLinked() throws Exception {
        providerAsserts(ATTACKER_UID, VICTIM_EMAIL);
        Pkce fanPkce = Pkce.create();
        MockHttpSession session = startAt(FAN_CLIENT_ID, FAN_REDIRECT_URI, fanPkce);

        assertThat(socialCallback(session)).isEqualTo("/login?error=email_registered");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM social_identities WHERE provider_user_id = ?", Integer.class, ATTACKER_UID))
                .as("the provider identity is attached to no account").isZero();
        // The resumed authorize is still unauthenticated → back to /login, no code for anyone.
        MvcResult again = mockMvc.perform(authorize(session, FAN_CLIENT_ID, FAN_REDIRECT_URI, fanPkce))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(again.getResponse().getHeader("Location")).endsWith("/login").doesNotContain("code=");
    }

    // ── AC-3 ──────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-3: 078 이전 팬 사이트 소셜 신원 → 그 사이트 계정 그대로 (sub=사이트 계정 · tenant_id=fan-platform · 가입 호출 없음)")
    void preDecisionSiteIdentity_unchanged() throws Exception {
        providerAsserts(SITE_UID, SITE_EMAIL);
        Pkce fanPkce = Pkce.create();
        MockHttpSession session = startAt(FAN_CLIENT_ID, FAN_REDIRECT_URI, fanPkce);

        socialCallback(session);

        String fanAccess = exchange(authorizeExpectingCode(session, FAN_CLIENT_ID, FAN_REDIRECT_URI, fanPkce),
                FAN_CLIENT_ID, FAN_REDIRECT_URI, fanPkce).get("access_token").asText();
        assertThat(claim(fanAccess, "sub")).isEqualTo(SITE_ACCOUNT);
        assertThat(claim(fanAccess, "tenant_id")).isEqualTo("fan-platform");
        accountService.verify(0, WireMock.postRequestedFor(WireMock.urlPathEqualTo("/internal/accounts/social-signup")));
        assertThat(jdbcTemplate.queryForList(
                "SELECT tenant_id FROM social_identities WHERE provider_user_id = ?", String.class, SITE_UID))
                .containsExactly("fan-platform");
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────────────

    private record Pkce(String verifier, String challenge) {
        static Pkce create() throws Exception {
            String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    UUID.randomUUID().toString().replace("-", "").getBytes(StandardCharsets.UTF_8));
            String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
            return new Pkce(verifier, challenge);
        }
    }

    private MockHttpServletRequestBuilder authorize(MockHttpSession session, String clientId, String redirectUri,
                                                    Pkce pkce) {
        var builder = get("/oauth2/authorize")
                .accept(MediaType.TEXT_HTML)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", "openid profile email")
                .queryParam("code_challenge", pkce.challenge())
                .queryParam("code_challenge_method", "S256")
                .queryParam("state", "be-617");
        return session != null ? builder.session(session) : builder;
    }

    /** Unauthenticated authorize → /login; returns the session holding the saved request. */
    private MockHttpSession startAt(String clientId, String redirectUri, Pkce pkce) throws Exception {
        MvcResult start = mockMvc.perform(authorize(null, clientId, redirectUri, pkce))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(start.getResponse().getHeader("Location")).endsWith("/login");
        return (MockHttpSession) start.getRequest().getSession(false);
    }

    /** /login/oauth/google → provider callback (real Redis state); returns the callback's redirect target. */
    private String socialCallback(MockHttpSession session) throws Exception {
        MvcResult start = mockMvc.perform(get("/login/oauth/google").session(session))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String state = queryParam(start.getResponse().getHeader("Location"), "state");
        MvcResult callback = mockMvc.perform(get("/login/oauth/google/callback")
                        .session(session)
                        .queryParam("code", "google-auth-code")
                        .queryParam("state", state))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return callback.getResponse().getHeader("Location");
    }

    private String authorizeExpectingCode(MockHttpSession session, String clientId, String redirectUri, Pkce pkce)
            throws Exception {
        MvcResult result = mockMvc.perform(authorize(session, clientId, redirectUri, pkce))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = result.getResponse().getHeader("Location");
        assertThat(location).as("a code, not a /login bounce").startsWith(redirectUri).contains("code=");
        return queryParam(location, "code");
    }

    private JsonNode exchange(String code, String clientId, String redirectUri, Pkce pkce) throws Exception {
        var request = post("/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code")
                .param("code", code)
                .param("redirect_uri", redirectUri)
                .param("code_verifier", pkce.verifier());
        if (STORE_CLIENT_ID.equals(clientId)) {
            request.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                    (STORE_CLIENT_ID + ":" + STORE_CLIENT_SECRET).getBytes(StandardCharsets.UTF_8)));
        } else {
            request.param("client_id", clientId);
        }
        MvcResult token = mockMvc.perform(request).andReturn();
        assertThat(token.getResponse().getStatus())
                .as("token status — body: " + token.getResponse().getContentAsString())
                .isEqualTo(200);
        return objectMapper.readTree(token.getResponse().getContentAsString());
    }

    private JsonNode payload(String jwt) throws Exception {
        return objectMapper.readTree(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]));
    }

    private String claim(String jwt, String name) throws Exception {
        JsonNode value = payload(jwt).get(name);
        return value == null ? null : value.asText();
    }

    private List<String> roles(String jwt) throws Exception {
        List<String> roles = new ArrayList<>();
        JsonNode node = payload(jwt).get("roles");
        if (node != null) {
            node.forEach(r -> roles.add(r.asText()));
        }
        return roles;
    }

    private static String queryParam(String url, String name) {
        String query = url.substring(url.indexOf('?') + 1);
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }
}
