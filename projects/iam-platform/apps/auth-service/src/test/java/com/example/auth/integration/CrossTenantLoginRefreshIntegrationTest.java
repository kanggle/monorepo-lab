package com.example.auth.integration;

import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.credentials.CredentialHash;
import com.example.auth.domain.repository.RefreshTokenRepository;
import com.example.auth.domain.token.RefreshToken;
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
import org.springframework.mock.web.MockHttpSession;
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
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-604 AC-3 — which accounts may log into which clients, and whether the session a
 * cross-tenant login produces keeps refreshing once SAS's built-in refresh provider is gone.
 *
 * <p>One account, credential in {@code fan-platform} ONLY, driven through the real browser
 * path (unauthenticated {@code /oauth2/authorize} saves the request → {@code POST /login}
 * resolves the initiating client from it → resumed authorize → code → token → refresh):
 * <ol>
 *   <li><b>(a)</b> the console client ({@code platform-console-web}, tenant {@code iam}) — the
 *       credential is reached through the console-only cross-tenant fallback (the
 *       ADR-MONO-044 D5 operator shape). Refresh must be 200 and the rotated mirror row must
 *       carry {@code fan-platform}, the login-time tenant — not the client's {@code iam}.</li>
 *   <li><b>(b)</b> a consumer client of another tenant ({@code ecommerce-web-store-client}) —
 *       the login is refused exactly like a wrong password.</li>
 *   <li><b>(c)</b> control — a consumer client of the account's own tenant
 *       ({@code demo-spa-client}, {@code fan-platform}) logs in and refreshes.</li>
 * </ol>
 *
 * <p>Why (a) is the cell that matters: before BE-604 its refresh tripped
 * {@code TOKEN_TENANT_MISMATCH} on every call (mirror row {@code fan-platform} vs client
 * {@code iam}) and was answered 200 only by the built-in provider re-running the grant —
 * reproduced in the 2026-09-26 demo window (BE-604 AC-0 ③(b)). Removing that provider
 * without re-defining the comparison would have turned it into a 400.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CrossTenantLoginRefreshIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    /** Form login looks up the account status (TASK-BE-600) and fails closed without an answer. */
    static WireMockServer accountService;

    private static final String ACCOUNT_ID = "0199de70-0000-7000-8000-0000000b6040";
    private static final String EMAIL = "cross-tenant-604@example.com";
    private static final String PASSWORD = "CrossTenantPassw0rd!";
    private static final String ACCOUNT_TENANT = "fan-platform";

    private static final String CONSOLE_CLIENT_ID = "platform-console-web";
    private static final String CONSOLE_REDIRECT_URI = "http://localhost:3000/api/auth/callback";
    private static final String STORE_CLIENT_ID = "ecommerce-web-store-client";
    private static final String STORE_REDIRECT_URI = "http://localhost:3000/api/auth/callback/iam";
    private static final String FAN_CLIENT_ID = "demo-spa-client";
    private static final String FAN_REDIRECT_URI = "http://localhost:3000/callback";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

        accountService = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        accountService.start();
        registry.add("auth.account-service.base-url", accountService::baseUrl);
        accountService.stubFor(WireMock.get(WireMock.urlPathEqualTo(
                        "/internal/accounts/" + ACCOUNT_ID + "/status"))
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                { "accountId": "%s", "status": "ACTIVE",
                                  "statusChangedAt": "2026-01-01T00:00:00Z" }
                                """.formatted(ACCOUNT_ID))));
    }

    @AfterAll
    static void stopAccountService() {
        if (accountService != null && accountService.isRunning()) {
            accountService.stop();
        }
    }

    @MockitoBean
    IamClientCredentialsTokenProvider gapTokenProvider;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CredentialJpaRepository credentialJpaRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void seedFanPlatformOnlyCredential() {
        Mockito.when(gapTokenProvider.currentBearer()).thenReturn("test-jwt");
        credentialJpaRepository.deleteAll();
        credentialJpaRepository.save(CredentialJpaEntity.fromDomain(Credential.create(
                ACCOUNT_ID, ACCOUNT_TENANT, EMAIL,
                CredentialHash.argon2id(new Argon2idPasswordHasher().hash(PASSWORD)), Instant.now())));
    }

    @Test
    @DisplayName("BE-604 AC-3 (a): fan-platform 전용 자격 → 콘솔 client 로그인(콘솔 한정 교차 폴백) → "
            + "refresh 200 · 회전 미러 행 테넌트 = fan-platform")
    void consoleClient_crossTenantSession_refreshes_andRotatedRowKeepsLoginTenant() throws Exception {
        Pkce pkce = Pkce.create();
        MockHttpSession authed = loginThrough(CONSOLE_CLIENT_ID, CONSOLE_REDIRECT_URI, pkce);
        assertThat(authed).as("the console login must succeed through the fallback").isNotNull();

        JsonNode tokens = exchangeCode(authed, CONSOLE_CLIENT_ID, CONSOLE_REDIRECT_URI, pkce);
        assertThat(claim(tokens.get("access_token").asText(), "tenant_id"))
                .as("the token carries the login-time (account) tenant, not the console's iam")
                .isEqualTo(ACCOUNT_TENANT);
        String issued = tokens.get("refresh_token").asText();
        assertThat(refreshTokenRepository.findByJti(issued).orElseThrow().getTenantId())
                .isEqualTo(ACCOUNT_TENANT);

        JsonNode refreshed = refresh(issued, CONSOLE_CLIENT_ID, 200);
        String rotated = refreshed.get("refresh_token").asText();
        RefreshToken rotatedRow = refreshTokenRepository.findByJti(rotated).orElseThrow();
        assertThat(rotatedRow.getTenantId())
                .as("the rotated row carries the login-time tenant (before BE-604: the client's iam)")
                .isEqualTo(ACCOUNT_TENANT);
        assertThat(rotatedRow.getRotatedFrom()).isEqualTo(issued);
        assertThat(claim(refreshed.get("access_token").asText(), "tenant_id")).isEqualTo(ACCOUNT_TENANT);

        // And the rotated row is accepted by the next refresh — first and rotated rows agree.
        refresh(rotated, CONSOLE_CLIENT_ID, 200);
    }

    @Test
    @DisplayName("BE-604 AC-3 (b): 같은 계정 → 다른 테넌트의 소비자 client(ecommerce) 폼 로그인 → "
            + "302 /login?error (틀린 비밀번호와 같은 응답)")
    void consumerClientOfAnotherTenant_loginRefused() throws Exception {
        Pkce pkce = Pkce.create();
        MockHttpSession saved = startAuthorize(STORE_CLIENT_ID, STORE_REDIRECT_URI, pkce);

        mockMvc.perform(post("/login")
                        .session(saved)
                        .with(csrf())
                        .param("username", EMAIL)
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));

        assertThat(saved.getAttribute("SPRING_SECURITY_CONTEXT"))
                .as("no authenticated session for a consumer client of another tenant")
                .isNull();
        // The status lookup never ran: no credential was resolved for this client.
        accountService.verify(0, WireMock.getRequestedFor(WireMock.urlPathEqualTo(
                "/internal/accounts/" + ACCOUNT_ID + "/status")));
    }

    @Test
    @DisplayName("BE-604 AC-3 (c) control: 같은 계정 → 자기 테넌트 소비자 client(fan-platform) → 로그인 · refresh 200")
    void consumerClientOfOwnTenant_logsInAndRefreshes() throws Exception {
        Pkce pkce = Pkce.create();
        MockHttpSession authed = loginThrough(FAN_CLIENT_ID, FAN_REDIRECT_URI, pkce);
        assertThat(authed).isNotNull();

        JsonNode tokens = exchangeCode(authed, FAN_CLIENT_ID, FAN_REDIRECT_URI, pkce);
        String issued = tokens.get("refresh_token").asText();

        JsonNode refreshed = refresh(issued, FAN_CLIENT_ID, 200);
        assertThat(refreshTokenRepository.findByJti(refreshed.get("refresh_token").asText())
                .orElseThrow().getTenantId()).isEqualTo(ACCOUNT_TENANT);
    }

    // -----------------------------------------------------------------------
    // Helpers — the browser path, step by step
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

    /** Unauthenticated browser authorize → 302 /login; the request is saved in the returned session. */
    private MockHttpSession startAuthorize(String clientId, String redirectUri, Pkce pkce) throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth2/authorize")
                        .accept(MediaType.TEXT_HTML)
                        .queryParam("response_type", "code")
                        .queryParam("client_id", clientId)
                        .queryParam("redirect_uri", redirectUri)
                        .queryParam("scope", "openid profile email")
                        .queryParam("code_challenge", pkce.challenge())
                        .queryParam("code_challenge_method", "S256")
                        .queryParam("state", "be-604-start"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(result.getResponse().getHeader("Location")).endsWith("/login");
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).as("the authorize request must be saved in a session").isNotNull();
        return session;
    }

    /** startAuthorize + a successful POST /login; returns the post-login (rotated) session. */
    private MockHttpSession loginThrough(String clientId, String redirectUri, Pkce pkce) throws Exception {
        MockHttpSession saved = startAuthorize(clientId, redirectUri, pkce);
        MvcResult login = mockMvc.perform(post("/login")
                        .session(saved)
                        .with(csrf())
                        .param("username", EMAIL)
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(login.getResponse().getRedirectedUrl())
                .as("a successful login resumes the saved authorize request, not /login?error")
                .contains("/oauth2/authorize")
                .contains("client_id=" + clientId);
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    /** Resumed authorize with the authenticated session → code → POST /oauth2/token. */
    private JsonNode exchangeCode(MockHttpSession authed, String clientId, String redirectUri,
                                  Pkce pkce) throws Exception {
        MvcResult authorize = mockMvc.perform(get("/oauth2/authorize")
                        .session(authed)
                        .queryParam("response_type", "code")
                        .queryParam("client_id", clientId)
                        .queryParam("redirect_uri", redirectUri)
                        .queryParam("scope", "openid profile email")
                        .queryParam("code_challenge", pkce.challenge())
                        .queryParam("code_challenge_method", "S256")
                        .queryParam("state", "be-604-resume"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = authorize.getResponse().getHeader("Location");
        assertThat(location).startsWith(redirectUri).contains("code=");
        String code = queryParam(location, "code");

        // Both clients driven to a token here are public PKCE clients (client_id, no secret).
        MvcResult token = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", redirectUri)
                        .param("client_id", clientId)
                        .param("code_verifier", pkce.verifier()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(token.getResponse().getContentAsString());
    }

    private JsonNode refresh(String refreshToken, String clientId, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", refreshToken)
                        .param("client_id", clientId))
                .andReturn();
        assertThat(result.getResponse().getStatus())
                .as("refresh status — body: " + result.getResponse().getContentAsString())
                .isEqualTo(expectedStatus);
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String claim(String jwt, String name) throws Exception {
        String[] parts = jwt.split("\\.");
        JsonNode payload = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
        JsonNode value = payload.get(name);
        return value == null ? null : value.asText();
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
