package com.example.auth.integration;

import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.credentials.CredentialHash;
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
 * TASK-BE-605 AC-1 — single sign-on across clients of DIFFERENT tenants asks for that client's
 * login instead of reusing the session (owner decision ① (b), 2026-09-26).
 *
 * <p>One person, the {@code demo@demo.com} shape: the same email and password as a credential in
 * {@code ecommerce} AND in {@code fan-platform} — two accounts (per-tenant {@code (tenant_id,
 * email)} uniqueness). Driven through the real browser path:
 * <ol>
 *   <li><b>(a)</b> logged in through the storefront client ({@code ecommerce}), then opening the fan
 *       client ({@code demo-spa-client}, {@code fan-platform}) → {@code /login}, NOT a code; the
 *       form login (scoped lookup, BE-604) picks the fan credential → the resumed authorize issues a
 *       code (no loop) → the token is {@code tenant_id=fan-platform}, {@code sub} = the fan
 *       account, {@code roles} contains {@code FAN}. Before BE-605 the same step returned a code
 *       at once and the token carried {@code tenant_id=ecommerce} with no role (BE-604 § ⑧).</li>
 *   <li><b>(b)</b> console exemption — a {@code fan-platform} session opening the console client
 *       ({@code platform-console-web}, tenant {@code iam}) gets a code without logging in again
 *       (ADR-MONO-044 D5 operators hold no {@code iam} credential).</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SsoTenantGateIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    static WireMockServer accountService;

    private static final String EMAIL = "sso-gate-605@example.com";
    private static final String PASSWORD = "SsoGatePassw0rd!";
    private static final String STORE_ACCOUNT_ID = "0199de70-0000-7000-8000-0000000e6050";
    private static final String FAN_ACCOUNT_ID = "0199de70-0000-7000-8000-0000000f6050";

    private static final String STORE_CLIENT_ID = "ecommerce-web-store-client";
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
        // Form login looks up the account status (TASK-BE-600) and fails closed without an answer.
        for (String accountId : List.of(STORE_ACCOUNT_ID, FAN_ACCOUNT_ID)) {
            accountService.stubFor(WireMock.get(WireMock.urlPathEqualTo(
                            "/internal/accounts/" + accountId + "/status"))
                    .willReturn(WireMock.aResponse().withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    { "accountId": "%s", "status": "ACTIVE",
                                      "statusChangedAt": "2026-01-01T00:00:00Z" }
                                    """.formatted(accountId))));
        }
        // No stored account_roles → the platform seed decides (RoleSeedPolicy), which fires only
        // when the session tenant IS the client's platform — the property this test is about.
        accountService.stubFor(WireMock.get(WireMock.urlPathMatching("/internal/tenants/.+/accounts/.+/roles"))
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ \"roles\": [] }")));
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

    @BeforeEach
    void seedOnePersonInTwoTenants() {
        Mockito.when(gapTokenProvider.currentBearer()).thenReturn("test-jwt");
        accountService.resetRequests();
        credentialJpaRepository.deleteAll();
        String hash = new Argon2idPasswordHasher().hash(PASSWORD);
        credentialJpaRepository.save(CredentialJpaEntity.fromDomain(Credential.create(
                STORE_ACCOUNT_ID, "ecommerce", EMAIL, CredentialHash.argon2id(hash), Instant.now())));
        credentialJpaRepository.save(CredentialJpaEntity.fromDomain(Credential.create(
                FAN_ACCOUNT_ID, "fan-platform", EMAIL, CredentialHash.argon2id(hash), Instant.now())));
    }

    @Test
    @DisplayName("BE-605 AC-1 (a): 스토어(ecommerce) 세션 → 팬 client authorize → /login(코드 아님) → "
            + "팬 자격 로그인 → 코드 → tenant_id=fan-platform · roles FAN")
    void storeSession_openingFanClient_reauthenticatesIntoTheFanCredential() throws Exception {
        Pkce storePkce = Pkce.create();
        MockHttpSession session = loginThrough(STORE_CLIENT_ID, STORE_REDIRECT_URI, storePkce);

        // Control: the same session still gets single sign-on on a client of ITS tenant.
        String storeCode = authorizeExpectingCode(session, STORE_CLIENT_ID, STORE_REDIRECT_URI, Pkce.create());
        assertThat(storeCode).isNotBlank();

        // The gate: a client of another consumer tenant sends the browser to its login.
        Pkce fanPkce = Pkce.create();
        MvcResult gated = mockMvc.perform(authorize(session, FAN_CLIENT_ID, FAN_REDIRECT_URI, fanPkce))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(gated.getResponse().getHeader("Location"))
                .as("before BE-605 this was a code for a tenant_id=ecommerce, role-less token")
                .endsWith("/login")
                .doesNotContain("code=");

        // Log in again — the saved request is now the fan authorize, so the scoped lookup picks
        // the fan-platform credential.
        MvcResult relogin = mockMvc.perform(post("/login")
                        .session(session)
                        .with(csrf())
                        .param("username", EMAIL)
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(relogin.getResponse().getRedirectedUrl())
                .as("the login resumes the gated fan authorize")
                .contains("/oauth2/authorize")
                .contains("client_id=" + FAN_CLIENT_ID);
        MockHttpSession fanSession = (MockHttpSession) relogin.getRequest().getSession(false);

        // No loop: the resumed authorize now passes the gate and yields a code.
        JsonNode tokens = exchangeFanCode(fanSession, fanPkce);
        String accessToken = tokens.get("access_token").asText();
        assertThat(claim(accessToken, "tenant_id")).isEqualTo("fan-platform");
        assertThat(claim(accessToken, "sub"))
                .as("the fan account — a different account row from the store one")
                .isEqualTo(FAN_ACCOUNT_ID);
        assertThat(roles(accessToken)).contains("FAN");

        // The documented UX cost: going back to the store now asks for the store login once.
        MvcResult back = mockMvc.perform(authorize(fanSession, STORE_CLIENT_ID, STORE_REDIRECT_URI, Pkce.create()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(back.getResponse().getHeader("Location")).endsWith("/login");
    }

    @Test
    @DisplayName("BE-605 AC-1 (b) 대조군: fan-platform 세션 → 콘솔 client(iam) authorize → 재로그인 없이 코드 (콘솔 면제)")
    void fanSession_openingConsole_isExempt() throws Exception {
        MockHttpSession session = loginThrough(FAN_CLIENT_ID, FAN_REDIRECT_URI, Pkce.create());

        Pkce consolePkce = Pkce.create();
        String code = authorizeExpectingCode(session, CONSOLE_CLIENT_ID, CONSOLE_REDIRECT_URI, consolePkce);

        MvcResult token = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", CONSOLE_REDIRECT_URI)
                        .param("client_id", CONSOLE_CLIENT_ID)
                        .param("code_verifier", consolePkce.verifier()))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = objectMapper.readTree(token.getResponse().getContentAsString())
                .get("access_token").asText();
        assertThat(claim(accessToken, "tenant_id"))
                .as("the console keeps the login-time tenant (BE-604 D)")
                .isEqualTo("fan-platform");
    }

    // -----------------------------------------------------------------------
    // Helpers — the browser path, step by step (CrossTenantLoginRefreshIntegrationTest shape)
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

    /** A browser authorize (text/html — the /login entry point is scoped to browsers). */
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authorize(
            MockHttpSession session, String clientId, String redirectUri, Pkce pkce) {
        var builder = get("/oauth2/authorize")
                .accept(MediaType.TEXT_HTML)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", "openid profile email")
                .queryParam("code_challenge", pkce.challenge())
                .queryParam("code_challenge_method", "S256")
                .queryParam("state", "be-605");
        return session != null ? builder.session(session) : builder;
    }

    /** Unauthenticated authorize → /login → POST /login; returns the post-login session. */
    private MockHttpSession loginThrough(String clientId, String redirectUri, Pkce pkce) throws Exception {
        MvcResult start = mockMvc.perform(authorize(null, clientId, redirectUri, pkce))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(start.getResponse().getHeader("Location")).endsWith("/login");
        MockHttpSession saved = (MockHttpSession) start.getRequest().getSession(false);
        assertThat(saved).isNotNull();

        MvcResult login = mockMvc.perform(post("/login")
                        .session(saved)
                        .with(csrf())
                        .param("username", EMAIL)
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(login.getResponse().getRedirectedUrl())
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
        assertThat(location).startsWith(redirectUri).contains("code=");
        return queryParam(location, "code");
    }

    private JsonNode exchangeFanCode(MockHttpSession session, Pkce pkce) throws Exception {
        String code = authorizeExpectingCode(session, FAN_CLIENT_ID, FAN_REDIRECT_URI, pkce);
        MvcResult token = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", FAN_REDIRECT_URI)
                        .param("client_id", FAN_CLIENT_ID)
                        .param("code_verifier", pkce.verifier()))
                .andExpect(status().isOk())
                .andReturn();
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
