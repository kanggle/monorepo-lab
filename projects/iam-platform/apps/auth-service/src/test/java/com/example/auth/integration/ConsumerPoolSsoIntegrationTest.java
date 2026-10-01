package com.example.auth.integration;

import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.credentials.CredentialHash;
import com.example.auth.domain.repository.RefreshTokenRepository;
import com.example.auth.infrastructure.persistence.CredentialJpaEntity;
import com.example.auth.infrastructure.persistence.CredentialJpaRepository;
import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import com.example.security.password.Argon2idPasswordHasher;
import com.example.testsupport.integration.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-615 (ADR-MONO-078 A; multi-tenancy.md § 소비자 계정 풀 § 4) — a consumer-POOL account
 * driven through the real browser path: form login (pool credential first) → authorize → code →
 * token, then single sign-on to the other consumer site, refresh, the console, and logout.
 *
 * <p>The pool account's credential row is {@code tenant_id = 'consumer-pool'}. account-service is
 * WireMock: status ACTIVE, and the new {@code /internal/tenants/{site}/consumer-members/{id}} read
 * (auth-to-account.md) answers per site. Two pool accounts:
 * <ul>
 *   <li>{@link #BOTH} — ACTIVE member of {@code ecommerce} AND {@code fan-platform}.</li>
 *   <li>{@link #STORE_ONLY} — member of {@code ecommerce} only (signed up at the store; the fan
 *       site's first-visit consent is {@code TASK-BE-616}).</li>
 * </ul>
 * The per-site controls (AC-2, AC-6) are {@code SsoTenantGateIntegrationTest} /
 * {@code CrossTenantLoginRefreshIntegrationTest}, unchanged.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConsumerPoolSsoIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    static WireMockServer accountService;

    private static final String PASSWORD = "PoolSsoPassw0rd!";
    private static final String BOTH = "0199de70-0000-7000-8000-0000000b0615";
    private static final String BOTH_EMAIL = "pool-both-615@example.com";
    private static final String STORE_ONLY = "0199de70-0000-7000-8000-0000000c0615";
    private static final String STORE_ONLY_EMAIL = "pool-store-615@example.com";

    private static final String STORE_CLIENT_ID = "ecommerce-web-store-client";
    private static final String STORE_CLIENT_SECRET = "ecommerce-dev";
    private static final String STORE_REDIRECT_URI = "http://localhost:3000/api/auth/callback/iam";
    private static final String FAN_CLIENT_ID = "demo-spa-client";
    private static final String FAN_REDIRECT_URI = "http://localhost:3000/callback";
    private static final String CONSOLE_CLIENT_ID = "platform-console-web";
    private static final String CONSOLE_REDIRECT_URI = "http://localhost:3000/api/auth/callback";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

        accountService = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        accountService.start();
        registry.add("auth.account-service.base-url", accountService::baseUrl);
        for (String accountId : List.of(BOTH, STORE_ONLY)) {
            accountService.stubFor(WireMock.get(WireMock.urlPathEqualTo("/internal/accounts/" + accountId + "/status"))
                    .willReturn(json("""
                            { "accountId": "%s", "status": "ACTIVE", "statusChangedAt": "2026-10-01T00:00:00Z" }
                            """.formatted(accountId))));
        }
        membership("ecommerce", BOTH, "\"ACTIVE\"");
        membership("fan-platform", BOTH, "\"ACTIVE\"");
        membership("ecommerce", STORE_ONLY, "\"ACTIVE\"");
        membership("fan-platform", STORE_ONLY, "null");
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String body) {
        return WireMock.aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
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

    @MockitoBean
    IamClientCredentialsTokenProvider gapTokenProvider;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CredentialJpaRepository credentialJpaRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedPoolCredentials() {
        Mockito.when(gapTokenProvider.currentBearer()).thenReturn("test-jwt");
        accountService.resetRequests();
        credentialJpaRepository.deleteAll();
        String hash = new Argon2idPasswordHasher().hash(PASSWORD);
        credentialJpaRepository.save(CredentialJpaEntity.fromDomain(Credential.create(
                BOTH, "consumer-pool", BOTH_EMAIL, CredentialHash.argon2id(hash), Instant.now())));
        credentialJpaRepository.save(CredentialJpaEntity.fromDomain(Credential.create(
                STORE_ONLY, "consumer-pool", STORE_ONLY_EMAIL, CredentialHash.argon2id(hash), Instant.now())));
    }

    // ── AC-1 ──────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-1: 풀 계정 팬 로그인 → 스토어 authorize 는 폼 없이 코드 → sub 동일 · tenant_id=ecommerce · roles 에 FAN 없음")
    void poolLoginOnFan_thenStoreWithoutForm_sameSub_siteTenant_noFlattening() throws Exception {
        Pkce fanPkce = Pkce.create();
        MockHttpSession session = loginThrough(BOTH_EMAIL, FAN_CLIENT_ID, FAN_REDIRECT_URI, fanPkce);
        JsonNode fanTokens = exchange(authorizeExpectingCode(session, FAN_CLIENT_ID, FAN_REDIRECT_URI, fanPkce),
                FAN_CLIENT_ID, FAN_REDIRECT_URI, fanPkce, 200);
        String fanAccess = fanTokens.get("access_token").asText();
        assertThat(claim(fanAccess, "tenant_id")).isEqualTo("fan-platform");
        assertThat(claim(fanAccess, "sub")).isEqualTo(BOTH);
        assertThat(roles(fanAccess)).containsExactly("FAN");

        // Single sign-on: the store authorize yields a code at once — no /login.
        Pkce storePkce = Pkce.create();
        String storeCode = authorizeExpectingCode(session, STORE_CLIENT_ID, STORE_REDIRECT_URI, storePkce);
        JsonNode storeTokens = exchange(storeCode, STORE_CLIENT_ID, STORE_REDIRECT_URI, storePkce, 200);
        String storeAccess = storeTokens.get("access_token").asText();
        assertThat(claim(storeAccess, "sub")).as("one account on both sites").isEqualTo(BOTH);
        assertThat(claim(storeAccess, "tenant_id")).isEqualTo("ecommerce").isNotEqualTo("consumer-pool");
        assertThat(roles(storeAccess)).containsExactly("CUSTOMER").doesNotContain("FAN");
        assertThat(claim(storeTokens.get("id_token").asText(), "tenant_id")).isEqualTo("ecommerce");

        // The mirror rows carry the SITE (the claim), never the pool value.
        assertThat(refreshTokenRepository.findByJti(fanTokens.get("refresh_token").asText())
                .orElseThrow().getTenantId()).isEqualTo("fan-platform");
        assertThat(refreshTokenRepository.findByJti(storeTokens.get("refresh_token").asText())
                .orElseThrow().getTenantId()).isEqualTo("ecommerce");
    }

    // ── no membership → no token, no loop ───────────────────────────────────────────────────────

    @Test
    @DisplayName("멤버십 없는 사이트(스토어 전용 풀 계정 → 팬): authorize 는 코드(재로그인 요구 없음 = 루프 없음), 토큰 엔드포인트는 400 invalid_grant")
    void storeOnlyPoolAccount_onFan_noTokenAndNoLoop() throws Exception {
        MockHttpSession session = loginThrough(STORE_ONLY_EMAIL, STORE_CLIENT_ID, STORE_REDIRECT_URI, Pkce.create());

        Pkce fanPkce = Pkce.create();
        String code = authorizeExpectingCode(session, FAN_CLIENT_ID, FAN_REDIRECT_URI, fanPkce);
        JsonNode error = exchange(code, FAN_CLIENT_ID, FAN_REDIRECT_URI, fanPkce, 400);
        assertThat(error.get("error").asText()).isEqualTo("invalid_grant");

        // A second attempt behaves the same — never a /login bounce.
        authorizeExpectingCode(session, FAN_CLIENT_ID, FAN_REDIRECT_URI, Pkce.create());
    }

    // ── AC-5 ──────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-5: 스토어 refresh 로 팬 토큰 못 받음(400) · 스토어 refresh 는 200(ecommerce) · 미러 행을 fan-platform 으로 바꾸면 TOKEN_TENANT_MISMATCH")
    void refresh_storeTokenNeverYieldsFanToken() throws Exception {
        Pkce storePkce = Pkce.create();
        MockHttpSession session = loginThrough(BOTH_EMAIL, STORE_CLIENT_ID, STORE_REDIRECT_URI, storePkce);
        JsonNode storeTokens = exchange(authorizeExpectingCode(session, STORE_CLIENT_ID, STORE_REDIRECT_URI, storePkce),
                STORE_CLIENT_ID, STORE_REDIRECT_URI, storePkce, 200);
        String storeRefresh = storeTokens.get("refresh_token").asText();

        // The fan client presenting the store's refresh token: no fan token.
        MvcResult asFan = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", storeRefresh)
                        .param("client_id", FAN_CLIENT_ID))
                .andReturn();
        assertThat(asFan.getResponse().getStatus()).isEqualTo(400);

        // The store refreshing its own token: 200, still ecommerce.
        JsonNode refreshed = refreshStore(storeRefresh, 200);
        assertThat(claim(refreshed.get("access_token").asText(), "tenant_id")).isEqualTo("ecommerce");
        String rotated = refreshed.get("refresh_token").asText();
        assertThat(refreshTokenRepository.findByJti(rotated).orElseThrow().getTenantId()).isEqualTo("ecommerce");

        // The comparison still bites: a row of another site is refused.
        jdbcTemplate.update("UPDATE refresh_tokens SET tenant_id = 'fan-platform' WHERE jti = ?", rotated);
        JsonNode mismatch = refreshStore(rotated, 400);
        assertThat(mismatch.get("error").asText()).isEqualTo("invalid_grant");
        assertThat(mismatch.get("error_description").asText()).isEqualTo("TOKEN_TENANT_MISMATCH");
    }

    // ── AC-3 ──────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-3: 풀 세션 → 콘솔 client(iam 자격 없음): BE-610 대로 재로그인 없이 코드, 토큰은 400(consumer-pool 발급 거절 — 운영자 권한 없음)")
    void poolSession_onConsole_noConsoleToken() throws Exception {
        MockHttpSession session = loginThrough(BOTH_EMAIL, STORE_CLIENT_ID, STORE_REDIRECT_URI, Pkce.create());

        Pkce consolePkce = Pkce.create();
        String code = authorizeExpectingCode(session, CONSOLE_CLIENT_ID, CONSOLE_REDIRECT_URI, consolePkce);
        JsonNode error = exchange(code, CONSOLE_CLIENT_ID, CONSOLE_REDIRECT_URI, consolePkce, 400);
        assertThat(error.get("error").asText()).isEqualTo("invalid_grant");
    }

    @Test
    @DisplayName("AC-3: 콘솔 폼 로그인의 교차 조회 — 이메일이 풀 한 행뿐이면 그 자격으로 로그인된다(LOGIN_TENANT_AMBIGUOUS 아님), 토큰은 400")
    void consoleFormLogin_poolOnlyEmail_resolvesButNoToken() throws Exception {
        Pkce consolePkce = Pkce.create();
        MockHttpSession session = loginThrough(BOTH_EMAIL, CONSOLE_CLIENT_ID, CONSOLE_REDIRECT_URI, consolePkce);

        String code = authorizeExpectingCode(session, CONSOLE_CLIENT_ID, CONSOLE_REDIRECT_URI, consolePkce);
        assertThat(exchange(code, CONSOLE_CLIENT_ID, CONSOLE_REDIRECT_URI, consolePkce, 400)
                .get("error").asText()).isEqualTo("invalid_grant");
    }

    // ── AC-4 (owner decision: logout ends the WHOLE IAM session) ──────────────────────────────

    @Test
    @DisplayName("AC-4: 스토어에서 RP 로그아웃(id_token_hint) → IAM 세션 종료 → 팬 authorize 는 /login · 이미 받은 팬 refresh 는 그 사이트 만료까지 유효")
    void logoutFromStore_endsIamSession_fanAppSessionFollowsItsOwnExpiry() throws Exception {
        Pkce fanPkce = Pkce.create();
        MockHttpSession session = loginThrough(BOTH_EMAIL, FAN_CLIENT_ID, FAN_REDIRECT_URI, fanPkce);
        JsonNode fanTokens = exchange(authorizeExpectingCode(session, FAN_CLIENT_ID, FAN_REDIRECT_URI, fanPkce),
                FAN_CLIENT_ID, FAN_REDIRECT_URI, fanPkce, 200);
        Pkce storePkce = Pkce.create();
        JsonNode storeTokens = exchange(
                authorizeExpectingCode(session, STORE_CLIENT_ID, STORE_REDIRECT_URI, storePkce),
                STORE_CLIENT_ID, STORE_REDIRECT_URI, storePkce, 200);

        mockMvc.perform(get("/connect/logout")
                        .session(session)
                        .queryParam("id_token_hint", storeTokens.get("id_token").asText()))
                .andExpect(status().is3xxRedirection());
        assertThat(session.isInvalid()).as("the IAM browser session is gone").isTrue();

        // The other site's NEXT authorize needs a login again — no silent SSO after a logout.
        MvcResult fanAgain = mockMvc.perform(authorize(new MockHttpSession(), FAN_CLIENT_ID, FAN_REDIRECT_URI,
                        Pkce.create()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(fanAgain.getResponse().getHeader("Location")).endsWith("/login");

        // What logout does NOT do (the documented scope): the fan app's own session — its refresh
        // token — follows that site's expiry / its own logout.
        MvcResult fanRefresh = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", fanTokens.get("refresh_token").asText())
                        .param("client_id", FAN_CLIENT_ID))
                .andReturn();
        assertThat(fanRefresh.getResponse().getStatus()).isEqualTo(200);
    }

    // -----------------------------------------------------------------------
    // Helpers — the browser path (SsoTenantGateIntegrationTest shape)
    // -----------------------------------------------------------------------

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
                .queryParam("state", "be-615");
        return session != null ? builder.session(session) : builder;
    }

    private MockHttpSession loginThrough(String email, String clientId, String redirectUri, Pkce pkce)
            throws Exception {
        MvcResult start = mockMvc.perform(authorize(null, clientId, redirectUri, pkce))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(start.getResponse().getHeader("Location")).endsWith("/login");
        MockHttpSession saved = (MockHttpSession) start.getRequest().getSession(false);

        MvcResult login = mockMvc.perform(post("/login")
                        .session(saved)
                        .with(csrf())
                        .param("username", email)
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(login.getResponse().getRedirectedUrl())
                .as("the pool credential logs in and resumes the saved authorize")
                .contains("/oauth2/authorize")
                .contains("client_id=" + clientId);
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private String authorizeExpectingCode(MockHttpSession session, String clientId, String redirectUri,
                                          Pkce pkce) throws Exception {
        MvcResult result = mockMvc.perform(authorize(session, clientId, redirectUri, pkce))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = result.getResponse().getHeader("Location");
        assertThat(location).as("a code, not a /login bounce").startsWith(redirectUri).contains("code=");
        return queryParam(location, "code");
    }

    private JsonNode exchange(String code, String clientId, String redirectUri, Pkce pkce, int expectedStatus)
            throws Exception {
        var request = post("/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code")
                .param("code", code)
                .param("redirect_uri", redirectUri)
                .param("code_verifier", pkce.verifier());
        if (STORE_CLIENT_ID.equals(clientId)) {
            request.header("Authorization", basicStore());
        } else {
            request.param("client_id", clientId);
        }
        MvcResult token = mockMvc.perform(request).andReturn();
        assertThat(token.getResponse().getStatus())
                .as("token status — body: " + token.getResponse().getContentAsString())
                .isEqualTo(expectedStatus);
        return objectMapper.readTree(token.getResponse().getContentAsString());
    }

    private JsonNode refreshStore(String refreshToken, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header("Authorization", basicStore())
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", refreshToken))
                .andReturn();
        assertThat(result.getResponse().getStatus())
                .as("refresh status — body: " + result.getResponse().getContentAsString())
                .isEqualTo(expectedStatus);
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String basicStore() {
        return "Basic " + Base64.getEncoder().encodeToString(
                (STORE_CLIENT_ID + ":" + STORE_CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));
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
