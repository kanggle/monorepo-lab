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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-641 — RP-initiated logout ({@code GET /connect/logout}, the discovery
 * {@code end_session_endpoint}) on a REAL Tomcat, driven by a real HTTP client.
 *
 * <h3>Why a real server and not MockMvc</h3>
 *
 * <p>The defect lives in the container's error path. SAS 1.4.1's default logout error handling calls
 * {@code sendError(400)}; Tomcat then dispatches to {@code /error}, which no chain except the
 * {@code @Order(2)} one matches, and that chain's {@code anyRequest().denyAll()} turns the dispatch into
 * <b>401 {@code Missing or invalid internal credentials}</b> — what the console showed the owner in the
 * 32nd demo window. MockMvc performs no ERROR dispatch, so there a bad logout request looks like a bodiless
 * 400 and the 401 can never appear (the only earlier {@code /connect/logout} test,
 * {@code ConsumerPoolSsoIntegrationTest}, is MockMvc and was green throughout). This class is the
 * {@code TASK-MONO-801} probe shape: {@code RANDOM_PORT} + {@link HttpClient}, redirects not followed,
 * cookies carried by hand.
 *
 * <h3>Profile</h3>
 *
 * <p>Not {@code test}: under {@code test} the {@code InternalApiFilter} bypass authenticates every
 * {@code /internal/**} request, which would make the internal-credential control below pass for the wrong
 * reason. {@code integration-test} keeps the bundled classpath JWT keys legal ({@code JwtKeyPathValidator})
 * without switching the bypass on; the test configuration itself is loaded from
 * {@code application-test.yml} as an additional config location.
 *
 * <p>Skipped when Docker is unavailable ({@code AbstractIntegrationTest}); the CI
 * {@code :auth-service:integrationTest} lane is authoritative.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                // Additional location (not import): its values override application.yml's defaults,
                // exactly as the `test` profile document does for every sibling IT.
                "spring.config.additional-location=classpath:application-test.yml",
                "internal.api.bypass-when-unconfigured=false"
        })
@Testcontainers
@ActiveProfiles("integration-test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OidcLogoutEndpointRealServerIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    /** BE-600: the password login asks account-service for the account status (fail-closed). */
    static WireMockServer accountService;

    private static final String EMAIL = "be641-logout@example.com";
    private static final String PASSWORD = "Be641LogoutPassw0rd!";
    private static final String ACCOUNT_ID = "be641-logout-account-001";
    private static final String TENANT_ID = "fan-platform";
    // The console client — the one the owner logged out of. V0015 redirect_uri, V0021 post-logout URI.
    private static final String CLIENT_ID = "platform-console-web";
    private static final String REDIRECT_URI = "http://localhost:3000/api/auth/callback";
    private static final String POST_LOGOUT_REDIRECT_URI = "http://localhost:3000/login";

    /** The Order 2 entry point's body (SecurityConfig#onAuthenticationFailure) — must never answer a logout. */
    private static final String INTERNAL_CREDENTIALS_MESSAGE = "Missing or invalid internal credentials";

    private static final Pattern CSRF = Pattern.compile(
            "<input[^>]*name=\"_csrf\"[^>]*value=\"([^\"]+)\"|<input[^>]*value=\"([^\"]+)\"[^>]*name=\"_csrf\"");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

        accountService = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        accountService.start();
        registry.add("auth.account-service.base-url", accountService::baseUrl);
        accountService.stubFor(WireMock.get(WireMock.urlPathEqualTo("/internal/accounts/" + ACCOUNT_ID + "/status"))
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

    // The GAP client_credentials Bearer is minted by a SAS self-call — not under test here.
    @MockitoBean
    IamClientCredentialsTokenProvider gapTokenProvider;

    @LocalServerPort
    private int port;

    @Autowired
    private CredentialJpaRepository credentialJpaRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @BeforeEach
    void seedCredential() {
        Mockito.when(gapTokenProvider.currentBearer()).thenReturn("test-jwt");
        credentialJpaRepository.deleteAll();
        String hash = new Argon2idPasswordHasher().hash(PASSWORD);
        credentialJpaRepository.save(CredentialJpaEntity.fromDomain(Credential.create(
                ACCOUNT_ID, TENANT_ID, EMAIL, CredentialHash.argon2id(hash), Instant.now())));
    }

    // ── AC-1 / AC-2: the error path answers OIDC, not the internal-credential 401 ─────────────────

    @Test
    @DisplayName("id_token_hint 없음 → 400 application/json {error=invalid_request, error_description=…id_token_hint} (수정 전: 401 내부 자격)")
    void missingIdTokenHint_answers400OidcError() throws Exception {
        HttpResponse<String> response = get("/connect/logout", Map.of());

        assertOidcError(response, "invalid_request", "id_token_hint");
    }

    @Test
    @DisplayName("IdP 가 모르는 id_token_hint → 400 {error=invalid_token, …id_token_hint} (수정 전: 401 내부 자격)")
    void unknownIdTokenHint_answers400OidcError() throws Exception {
        HttpResponse<String> response = get("/connect/logout?id_token_hint=not-an-id-token"
                + "&post_logout_redirect_uri=" + enc(POST_LOGOUT_REDIRECT_URI)
                + "&client_id=" + CLIENT_ID, Map.of());

        assertOidcError(response, "invalid_token", "id_token_hint");
    }

    // ── AC-1: the normal request still 302s and ends the IdP session ──────────────────────────────

    @Test
    @DisplayName("정상: 로그인 → 코드 → id_token → /connect/logout → 302 post_logout_redirect_uri · 다음 authorize 는 /login")
    void validLogout_redirectsToRegisteredUri_andEndsIdpSession() throws Exception {
        Map<String, String> cookies = new LinkedHashMap<>();

        // 1. The login page — session cookie + CSRF token.
        HttpResponse<String> loginPage = get("/login", cookies);
        assertThat(loginPage.statusCode()).as("GET /login").isEqualTo(200);
        String csrf = csrfToken(loginPage.body());

        // 2. Password login.
        HttpResponse<String> login = postForm("/login", cookies, Map.of(
                "username", EMAIL, "password", PASSWORD, "_csrf", csrf));
        assertThat(login.statusCode()).as("POST /login").isEqualTo(302);
        assertThat(location(login)).as("login must succeed, not bounce to ?error").doesNotContain("error");

        // 3. Authorize → code.
        Pkce pkce = Pkce.create();
        HttpResponse<String> authorize = get(authorizePath(pkce), cookies);
        assertThat(authorize.statusCode()).as("authorize").isEqualTo(302);
        assertThat(location(authorize)).as("a code, not a /login bounce")
                .startsWith(REDIRECT_URI).contains("code=");
        String code = queryParam(location(authorize), "code");

        // 4. Code → tokens (public PKCE client).
        HttpResponse<String> token = postForm("/oauth2/token", new LinkedHashMap<>(), Map.of(
                "grant_type", "authorization_code",
                "code", code,
                "redirect_uri", REDIRECT_URI,
                "client_id", CLIENT_ID,
                "code_verifier", pkce.verifier()));
        assertThat(token.statusCode()).as("token — body: " + token.body()).isEqualTo(200);
        String idToken = objectMapper.readTree(token.body()).get("id_token").asText();

        // 5. RP-initiated logout with the browser's IdP session.
        HttpResponse<String> logout = get("/connect/logout?id_token_hint=" + enc(idToken)
                + "&post_logout_redirect_uri=" + enc(POST_LOGOUT_REDIRECT_URI)
                + "&client_id=" + CLIENT_ID
                + "&state=be-641", cookies);
        assertThat(logout.statusCode()).as("logout — body: " + logout.body()).isEqualTo(302);
        assertThat(location(logout)).isEqualTo(POST_LOGOUT_REDIRECT_URI + "?state=be-641");

        // 6. The IdP session is gone: the same browser must log in again.
        HttpResponse<String> again = getHtml(authorizePath(Pkce.create()), cookies);
        assertThat(again.statusCode()).isEqualTo(302);
        assertThat(location(again)).as("no silent SSO after logout").endsWith("/login");
    }

    // ── AC-2 controls: the Order 2 chain is unchanged ─────────────────────────────────────────────

    @Test
    @DisplayName("대조: /internal/auth/credentials Bearer 없음 → 여전히 401 내부 자격 (Order 2 는 그대로)")
    void internalEndpoint_withoutBearer_stillAnswersInternalCredentials401() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/internal/auth/credentials"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"accountId\":\"a\",\"email\":\"a@example.com\",\"password\":\"p\"}"))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("code").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(body.get("message").asText()).isEqualTo(INTERNAL_CREDENTIALS_MESSAGE);
    }

    @Test
    @DisplayName("대조: /connect/** 의 다른 경로는 열리지 않았다 → 401 내부 자격 (Failure Scenario 1)")
    void otherConnectPath_isNotOpened() throws Exception {
        HttpResponse<String> response = get("/connect/not-an-endpoint", Map.of());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains(INTERNAL_CREDENTIALS_MESSAGE);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void assertOidcError(HttpResponse<String> response, String error, String namedParameter)
            throws Exception {
        assertThat(response.statusCode())
                .as("status — body: " + response.body())
                .isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .startsWith("application/json");
        assertThat(response.body()).doesNotContain(INTERNAL_CREDENTIALS_MESSAGE);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("error").asText()).isEqualTo(error);
        assertThat(body.get("error_description").asText())
                .isEqualTo("OpenID Connect 1.0 Logout Request Parameter: " + namedParameter);
        assertThat(body.size()).as("exactly {error, error_description}: " + response.body()).isEqualTo(2);
    }

    private String authorizePath(Pkce pkce) {
        return "/oauth2/authorize?response_type=code"
                + "&client_id=" + CLIENT_ID
                + "&redirect_uri=" + enc(REDIRECT_URI)
                + "&scope=" + enc("openid profile email tenant.read")
                + "&code_challenge=" + pkce.challenge()
                + "&code_challenge_method=S256"
                + "&state=be-641";
    }

    private URI uri(String pathAndQuery) {
        return URI.create("http://localhost:" + port + pathAndQuery);
    }

    private HttpResponse<String> get(String pathAndQuery, Map<String, String> cookies) throws Exception {
        return send(HttpRequest.newBuilder(uri(pathAndQuery)).GET(), cookies);
    }

    /** A browser navigation — {@code Accept: text/html} selects the login-page entry point. */
    private HttpResponse<String> getHtml(String pathAndQuery, Map<String, String> cookies) throws Exception {
        return send(HttpRequest.newBuilder(uri(pathAndQuery)).header("Accept", "text/html").GET(), cookies);
    }

    private HttpResponse<String> postForm(String path, Map<String, String> cookies, Map<String, String> form)
            throws Exception {
        String body = form.entrySet().stream()
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&"));
        return send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)), cookies);
    }

    /** Sends with the cookie jar and folds every {@code Set-Cookie} back into it (session fixation rotates it). */
    private HttpResponse<String> send(HttpRequest.Builder builder, Map<String, String> cookies) throws Exception {
        if (!cookies.isEmpty()) {
            builder.header("Cookie", cookies.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("; ")));
        }
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (cookies instanceof LinkedHashMap) {
            for (String setCookie : response.headers().allValues("Set-Cookie")) {
                String pair = setCookie.split(";", 2)[0];
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    cookies.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
                }
            }
        }
        return response;
    }

    private static String location(HttpResponse<String> response) {
        return response.headers().firstValue("Location").orElse("");
    }

    private static String csrfToken(String html) {
        Matcher m = CSRF.matcher(html);
        assertThat(m.find()).as("the login page carries a _csrf field").isTrue();
        return m.group(1) != null ? m.group(1) : m.group(2);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String queryParam(String url, String name) {
        String query = url.substring(url.indexOf('?') + 1);
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private record Pkce(String verifier, String challenge) {
        static Pkce create() throws Exception {
            String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    UUID.randomUUID().toString().replace("-", "").getBytes(StandardCharsets.UTF_8));
            String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
            return new Pkce(verifier, challenge);
        }
    }
}
