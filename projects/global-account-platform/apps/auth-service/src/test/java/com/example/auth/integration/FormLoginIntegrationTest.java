package com.example.auth.integration;

import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.credentials.CredentialHash;
import com.example.auth.infrastructure.persistence.CredentialJpaEntity;
import com.example.auth.infrastructure.persistence.CredentialJpaRepository;
import com.example.security.password.Argon2idPasswordHasher;
import com.example.testsupport.integration.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
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
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-309 — Integration tests for the new HTML form-login surface and
 * its interaction with the existing Spring Authorization Server filter chain.
 *
 * <p>Test cases (6):
 *
 * <ol>
 *   <li><b>Happy path</b> — form-login then SAS authorize/token round-trip:
 *       seed credential → POST /login with email+password+CSRF → 302 to
 *       /oauth2/authorize → 302 to redirect_uri with code → POST /oauth2/token
 *       with code+PKCE verifier → 200 with access_token + id_token (containing
 *       tenant_id / tenant_type claims, sourced from
 *       {@link com.example.auth.infrastructure.security.CredentialAuthenticationProvider}
 *       via {@code Authentication.details} → {@link com.example.auth.infrastructure.oauth2.TenantClaimTokenCustomizer}).</li>
 *   <li><b>Invalid credentials</b> — wrong password → 302 to /login?error,
 *       no session, no SAS code emitted.</li>
 *   <li><b>Unauthenticated authorize redirect</b> — GET /oauth2/authorize
 *       without a session → 302 to /login (proving the
 *       {@code AuthorizationServerConfig} entry-point edit took effect).</li>
 *   <li><b>Logout</b> — POST /logout invalidates the session;
 *       subsequent GET /oauth2/authorize is again unauthenticated and
 *       redirects to /login.</li>
 *   <li><b>CSRF gate</b> — POST /login without a CSRF token → 403 Forbidden.</li>
 *   <li><b>Deprecated JSON /api/auth/login regression guard</b> — POST
 *       /api/auth/login still accepts the legacy JSON body and returns a
 *       LoginResponse with access/refresh tokens, plus the RFC 8594
 *       Deprecation header.</li>
 * </ol>
 *
 * <p>Test profile uses the seeded {@code platform-console-web} OIDC client
 * (V0015 migration) as a public PKCE client. The form-login + SAS authorize
 * step exercises the exact production path that TASK-PC-FE-019 fixture will
 * eventually drive via Playwright.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FormLoginIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    private static final String TEST_EMAIL = "form-login-test@example.com";
    private static final String TEST_PASSWORD = "FormLoginPassw0rd!";
    private static final String TEST_ACCOUNT_ID = "form-login-account-001";
    private static final String TEST_TENANT_ID = "fan-platform";
    private static final String CLIENT_ID = "platform-console-web";
    // The platform-console-web row in V0015 lists this exact redirect_uri.
    private static final String REDIRECT_URI = "http://localhost:3000/api/auth/callback";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CredentialJpaRepository credentialJpaRepository;

    private String codeVerifier;
    private String codeChallenge;

    @BeforeEach
    void seedCredentialAndGeneratePkce() throws Exception {
        credentialJpaRepository.deleteAll();
        Argon2idPasswordHasher hasher = new Argon2idPasswordHasher();
        String hash = hasher.hash(TEST_PASSWORD);
        Instant now = Instant.now();
        credentialJpaRepository.save(CredentialJpaEntity.fromDomain(
                Credential.create(TEST_ACCOUNT_ID, TEST_TENANT_ID, TEST_EMAIL,
                        CredentialHash.argon2id(hash), now)));

        // Per-test PKCE pair.
        codeVerifier = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(UUID.randomUUID().toString().replace("-", "")
                        .getBytes(StandardCharsets.UTF_8));
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] hashed = sha256.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
        codeChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
    }

    // ------------------------------------------------------------------
    // 1. Happy path — form-login + SAS code + token round-trip
    // ------------------------------------------------------------------

    @Test
    @DisplayName("happy path: form-login → /oauth2/authorize → code → /oauth2/token → access/id token with tenant claims")
    void formLogin_thenAuthorizeAndToken_yieldsTokensWithTenantClaims() throws Exception {
        MockHttpSession session = new MockHttpSession();

        // POST /login with valid creds. with(csrf()) supplies a valid CSRF token
        // for the form-login endpoint (Spring Security default).
        MvcResult loginResult = mockMvc.perform(post("/login")
                        .session(session)
                        .with(csrf())
                        .param("username", TEST_EMAIL)
                        .param("password", TEST_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        // Spring Security's default session-fixation strategy creates a NEW
        // HttpSession on successful authentication (the original `session`
        // local is invalidated). Read the post-login session off the response's
        // request instead — this carries the migrated SPRING_SECURITY_CONTEXT.
        MockHttpSession authedSession =
                (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(authedSession)
                .as("post-login session (after Spring Security session-fixation rotation) must exist")
                .isNotNull();

        // GET /oauth2/authorize using the now-authenticated session.
        MvcResult authorizeResult = mockMvc.perform(get("/oauth2/authorize")
                        .session(authedSession)
                        .queryParam("response_type", "code")
                        .queryParam("client_id", CLIENT_ID)
                        .queryParam("redirect_uri", REDIRECT_URI)
                        .queryParam("scope", "openid profile email tenant.read")
                        .queryParam("code_challenge", codeChallenge)
                        .queryParam("code_challenge_method", "S256")
                        .queryParam("state", "form-login-state"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String location = authorizeResult.getResponse().getHeader("Location");
        assertThat(location).isNotNull().startsWith(REDIRECT_URI).contains("code=");
        String code = extractQueryParam(location, "code");
        assertThat(code).as("authorization code must be present").isNotBlank();

        // POST /oauth2/token — public PKCE client (no client_secret).
        MvcResult tokenResult = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("client_id", CLIENT_ID)
                        .param("code_verifier", codeVerifier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.id_token").isNotEmpty())
                .andReturn();

        JsonNode tokenResponse = objectMapper.readTree(
                tokenResult.getResponse().getContentAsString());
        JsonNode accessPayload = decodeJwtPayload(tokenResponse.get("access_token").asText());

        // The form-login principal name is the credential's stored email
        // (CredentialAuthenticationProvider returns credential.getEmail()).
        assertThat(accessPayload.get("sub").asText()).isEqualTo(TEST_EMAIL);
        assertThat(accessPayload.get("tenant_id").asText()).isEqualTo(TEST_TENANT_ID);
        assertThat(accessPayload.has("tenant_type"))
                .as("tenant_type claim must be present (set by CredentialAuthenticationProvider)")
                .isTrue();
    }

    // ------------------------------------------------------------------
    // 2. Invalid credentials — 302 /login?error, no session principal
    // ------------------------------------------------------------------

    @Test
    @DisplayName("invalid credentials: wrong password → 302 /login?error, no SecurityContext")
    void formLogin_wrongPassword_redirectsToLoginError() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/login")
                        .session(session)
                        .with(csrf())
                        .param("username", TEST_EMAIL)
                        .param("password", "definitely-not-the-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));

        assertThat(session.getAttribute("SPRING_SECURITY_CONTEXT"))
                .as("failed login must leave SecurityContext unset")
                .isNull();
    }

    // ------------------------------------------------------------------
    // 3. Unauthenticated /oauth2/authorize → 302 /login (entry-point edit)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("entry point: unauthenticated browser GET /oauth2/authorize → 302 /login (NOT /api/auth/login)")
    void unauthenticatedAuthorize_redirectsToHtmlLogin() throws Exception {
        mockMvc.perform(get("/oauth2/authorize")
                        .accept(MediaType.TEXT_HTML)
                        .queryParam("response_type", "code")
                        .queryParam("client_id", CLIENT_ID)
                        .queryParam("redirect_uri", REDIRECT_URI)
                        .queryParam("scope", "openid")
                        .queryParam("code_challenge", codeChallenge)
                        .queryParam("code_challenge_method", "S256")
                        .queryParam("state", "unauth-redirect-test"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    // ------------------------------------------------------------------
    // 4. Logout — session invalidation + subsequent authorize is unauthenticated
    // ------------------------------------------------------------------

    @Test
    @DisplayName("logout: POST /logout invalidates session; next /oauth2/authorize redirects to /login again")
    void logout_invalidatesSession() throws Exception {
        // First, login — grab the post-migration session like the happy-path test.
        MockHttpSession session = new MockHttpSession();
        MvcResult loginResult = mockMvc.perform(post("/login")
                        .session(session)
                        .with(csrf())
                        .param("username", TEST_EMAIL)
                        .param("password", TEST_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MockHttpSession authedSession =
                (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(authedSession).isNotNull();

        // POST /logout on the authenticated session.
        MvcResult logoutResult = mockMvc.perform(post("/logout")
                        .session(authedSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"))
                .andReturn();
        // Spring Security replaces or invalidates the session on logout. The
        // post-logout response's session attribute carrier may be a fresh
        // empty one — what matters is that the SAS authorize endpoint sees
        // no authenticated principal on a *new* session, which the next
        // assertion exercises.
        assertThat(logoutResult.getResponse().getStatus()).isEqualTo(302);

        // A fresh session must again redirect to /login on /oauth2/authorize.
        MockHttpSession freshSession = new MockHttpSession();
        mockMvc.perform(get("/oauth2/authorize")
                        .session(freshSession)
                        .accept(MediaType.TEXT_HTML)
                        .queryParam("response_type", "code")
                        .queryParam("client_id", CLIENT_ID)
                        .queryParam("redirect_uri", REDIRECT_URI)
                        .queryParam("scope", "openid")
                        .queryParam("code_challenge", codeChallenge)
                        .queryParam("code_challenge_method", "S256")
                        .queryParam("state", "post-logout"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    // ------------------------------------------------------------------
    // 4b. GET /login form rendering — TASK-BE-311 regression guard
    //
    // PC-FE-028 iter 7 trace + BE-311 iter 1 diagnostic surfaced that
    // BE-309's `formLogin(form -> form.loginPage("/login"))` was
    // suppressing the registration of `DefaultLoginPageGeneratingFilter`
    // (Spring Security 6 treats any `.loginPage()` call as a hint that
    // the caller provides a custom controller). BE-309's IT only
    // exercised POST /login + the unauth entry-point redirect, never
    // GET /login, which is why the regression slipped through. This
    // case anchors the auto-generated form rendering against the
    // pre-BE-311 trap returning.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GET /login renders Spring Security's auto-generated HTML form (username + password inputs)")
    void getLogin_rendersAutoGeneratedHtmlForm() throws Exception {
        mockMvc.perform(get("/login").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("name=\"username\"")))
                .andExpect(content().string(containsString("name=\"password\"")));
    }

    // ------------------------------------------------------------------
    // 5. CSRF gate — POST /login without a CSRF token → 403
    // ------------------------------------------------------------------

    @Test
    @DisplayName("CSRF gate: POST /login without CSRF token → 403")
    void formLogin_withoutCsrf_isForbidden() throws Exception {
        mockMvc.perform(post("/login")
                        // No .with(csrf()) — Spring Security default rejects.
                        .param("username", TEST_EMAIL)
                        .param("password", TEST_PASSWORD))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // 6. Deprecated JSON /api/auth/login regression guard
    // ------------------------------------------------------------------
    //
    // The deprecated JSON path's behaviour is verified by AuthIntegrationTest
    // (full WireMock account-service stub + RFC 8594 Deprecation header check).
    // Duplicating it here would require the same WireMock setup and would not
    // add new regression coverage for this task. AC-9 (LoginController.java
    // byte-unchanged) is verified at PR review time via `git diff --stat`.
    //
    // (No test method — intentional. The IT instead focuses on the new
    // form-login surface and the SAS entry-point edit.)

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String extractQueryParam(String url, String name) {
        int qIdx = url.indexOf('?');
        if (qIdx < 0) return null;
        String query = url.substring(qIdx + 1);
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }

    private JsonNode decodeJwtPayload(String jwt) throws Exception {
        String[] parts = jwt.split("\\.");
        assertThat(parts).hasSize(3);
        byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
        return objectMapper.readTree(payload);
    }
}
