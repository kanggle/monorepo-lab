package com.example.auth.integration;

import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import com.example.testsupport.integration.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-642 — the error path of {@code GET /oauth2/authorize} on a REAL Tomcat, driven by a real HTTP client.
 *
 * <h3>Why a real server and not MockMvc</h3>
 *
 * <p>Same mechanism as TASK-BE-641. For an error SAS 1.4.1 may not redirect (no trusted {@code redirect_uri}),
 * its default {@code OAuth2AuthorizationEndpointFilter.sendErrorResponse} calls {@code sendError(400)}; Tomcat
 * then dispatches to {@code /error}, which only the {@code @Order(2)} chain matches, and its
 * {@code anyRequest().denyAll()} answers <b>401 {@code Missing or invalid internal credentials}</b> — the symptom
 * {@code V0034__add_console_vercel_domain_redirect_uri.sql:82-91} records for a missing console callback row.
 * MockMvc performs no ERROR dispatch, so there the request looks like a bodiless 400 (Failure Scenario 2). The
 * probe shape is {@code TASK-MONO-801}'s / {@code OidcLogoutEndpointRealServerIntegrationTest}'s:
 * {@code RANDOM_PORT} + {@link HttpClient}, redirects not followed.
 *
 * <p>No login is needed: SAS validates the client, the redirect URI, the scopes, PKCE and {@code prompt} before
 * it asks whether the principal is authenticated, so every error below happens for an anonymous browser.
 *
 * <h3>Profile</h3>
 *
 * <p>Not {@code test}, for the reason {@code OidcLogoutEndpointRealServerIntegrationTest} gives: under
 * {@code test} the {@code InternalApiFilter} bypass authenticates every {@code /internal/**} request, so the
 * internal-credential control would pass for the wrong reason.
 *
 * <p>Skipped when Docker is unavailable ({@code AbstractIntegrationTest}); the CI
 * {@code :auth-service:integrationTest} lane is authoritative.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.additional-location=classpath:application-test.yml",
                "internal.api.bypass-when-unconfigured=false"
        })
@Testcontainers
@ActiveProfiles("integration-test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthorizeEndpointErrorRealServerIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    // The console client — V0015: public, PKCE required, this redirect URI, scopes openid profile email tenant.read.
    private static final String CLIENT_ID = "platform-console-web";
    private static final String REDIRECT_URI = "http://localhost:3000/api/auth/callback";
    private static final String UNREGISTERED_REDIRECT_URI = "https://be642-unregistered.example/api/auth/callback";
    private static final String UNKNOWN_CLIENT_ID = "be642-no-such-client";
    // PKCE challenge of an arbitrary verifier — the code is never exchanged here.
    private static final String CODE_CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
    private static final String STATE = "be-642";

    /** The Order 2 entry point's body (SecurityConfig#onAuthenticationFailure) — must never answer an authorize. */
    private static final String INTERNAL_CREDENTIALS_MESSAGE = "Missing or invalid internal credentials";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    // The GAP client_credentials Bearer is minted by a SAS self-call — not under test here.
    @MockitoBean
    IamClientCredentialsTokenProvider gapTokenProvider;

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @BeforeEach
    void stubGapBearer() {
        Mockito.when(gapTokenProvider.currentBearer()).thenReturn("test-jwt");
    }

    // ── AC-1 / AC-2: errors SAS may not redirect answer 400 OAuth JSON, not the internal-credential 401 ────

    @Test
    @DisplayName("모르는 client_id → 400 {invalid_request, OAuth 2.0 Parameter: client_id} (수정 전: 401 내부 자격)")
    void unknownClientId_answers400() throws Exception {
        HttpResponse<String> response = browserGet(authorize(UNKNOWN_CLIENT_ID, UNREGISTERED_REDIRECT_URI,
                "code", "openid", true, null));

        assertOAuthError(response, "invalid_request", "client_id");
    }

    @Test
    @DisplayName("미등록 redirect_uri → 400 {invalid_request, OAuth 2.0 Parameter: redirect_uri} (수정 전: 401 내부 자격)")
    void unregisteredRedirectUri_answers400() throws Exception {
        HttpResponse<String> response = browserGet(authorize(CLIENT_ID, UNREGISTERED_REDIRECT_URI,
                "code", "openid profile", true, null));

        assertOAuthError(response, "invalid_request", "redirect_uri");
    }

    @Test
    @DisplayName("client_id 없음(converter) → 400 {invalid_request, …client_id} (수정 전: 401 내부 자격)")
    void missingClientId_answers400() throws Exception {
        HttpResponse<String> response = browserGet("/oauth2/authorize?response_type=code"
                + "&redirect_uri=" + enc(REDIRECT_URI) + "&scope=openid&state=" + STATE);

        assertOAuthError(response, "invalid_request", "client_id");
    }

    @Test
    @DisplayName("response_type=token(converter) → SAS 1.4.1 은 redirect 하지 않는다 → 400 {unsupported_response_type} (수정 전: 401)")
    void unsupportedResponseType_answers400_becauseSasDoesNotRedirectIt() throws Exception {
        HttpResponse<String> response = browserGet(authorize(CLIENT_ID, REDIRECT_URI,
                "token", "openid", true, null));

        assertOAuthError(response, "unsupported_response_type", "response_type");
    }

    // ── AC-2 controls: redirectable errors still go back to the client, unchanged ───────────────────────

    @Test
    @DisplayName("대조: 허용 안 된 scope → 그대로 302 redirect_uri?error=invalid_scope … &state=")
    void invalidScope_stillRedirectsToClient() throws Exception {
        HttpResponse<String> response = browserGet(authorize(CLIENT_ID, REDIRECT_URI,
                "code", "openid be642-not-a-scope", true, null));

        assertThat(response.statusCode()).as("body: " + response.body()).isEqualTo(302);
        assertThat(location(response))
                .startsWith(REDIRECT_URI + "?error=invalid_scope&error_description=OAuth%202.0%20Parameter%3A%20scope")
                .endsWith("&state=" + STATE);
    }

    @Test
    @DisplayName("대조: PKCE 필수 client 에 code_challenge 없음 → 그대로 302 redirect_uri?error=invalid_request")
    void missingCodeChallenge_stillRedirectsToClient() throws Exception {
        HttpResponse<String> response = browserGet(authorize(CLIENT_ID, REDIRECT_URI,
                "code", "openid", false, null));

        assertThat(response.statusCode()).as("body: " + response.body()).isEqualTo(302);
        assertThat(location(response))
                .startsWith(REDIRECT_URI + "?error=invalid_request&error_description=OAuth%202.0%20Parameter%3A%20code_challenge")
                .endsWith("&state=" + STATE);
    }

    @Test
    @DisplayName("대조: prompt=none · 미인증 → 그대로 302 redirect_uri?error=login_required (auth-api.md § prompt)")
    void promptNone_unauthenticated_stillRedirectsLoginRequired() throws Exception {
        HttpResponse<String> response = browserGet(authorize(CLIENT_ID, REDIRECT_URI,
                "code", "openid", true, "none"));

        assertThat(response.statusCode()).as("body: " + response.body()).isEqualTo(302);
        assertThat(location(response)).startsWith(REDIRECT_URI + "?error=login_required");
    }

    @Test
    @DisplayName("대조: 정상 요청 · 미인증 브라우저 → 그대로 302 /login")
    void validRequest_unauthenticated_stillGoesToLogin() throws Exception {
        HttpResponse<String> response = browserGet(authorize(CLIENT_ID, REDIRECT_URI,
                "code", "openid profile", true, null));

        assertThat(response.statusCode()).as("body: " + response.body()).isEqualTo(302);
        assertThat(location(response)).endsWith("/login");
    }

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

    // ── AC-0: the other SAS endpoints do not take the /error route (their filters write the body) ──────

    @Test
    @DisplayName("AC-0: /oauth2/token 의 오류는 자기 OAuth JSON 을 쓴다 — 내부 자격 401 아님")
    void tokenEndpointError_isItsOwnOAuthJson() throws Exception {
        String form = "grant_type=authorization_code&code=be642-not-a-code"
                + "&redirect_uri=" + enc(REDIRECT_URI) + "&client_id=" + CLIENT_ID + "&code_verifier=be642-verifier";
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri("/oauth2/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).as("body: " + response.body()).isIn(400, 401);
        assertThat(response.body()).doesNotContain(INTERNAL_CREDENTIALS_MESSAGE);
        assertThat(objectMapper.readTree(response.body()).has("error")).as(response.body()).isTrue();
    }

    @Test
    @DisplayName("AC-0: /oauth2/userinfo Bearer 없음 → 내부 자격 401 아님")
    void userInfoWithoutBearer_isNotTheInternalCredential401() throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri("/oauth2/userinfo")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        // Which entry point answers (bearer 401 or the login redirect) is not this ticket's question — only that
        // the request never reaches Order 2 through /error.
        assertThat(response.statusCode()).as("body: " + response.body()).isIn(302, 401, 403);
        assertThat(response.body()).doesNotContain(INTERNAL_CREDENTIALS_MESSAGE);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void assertOAuthError(HttpResponse<String> response, String error, String namedParameter)
            throws Exception {
        assertThat(response.statusCode())
                .as("status — body: " + response.body())
                .isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .startsWith("application/json");
        assertThat(response.headers().firstValue("Location")).as("an untrusted redirect_uri is never followed")
                .isEmpty();
        assertThat(response.body())
                .doesNotContain(INTERNAL_CREDENTIALS_MESSAGE)
                .as("the body never repeats a request value (Failure Scenario 3)")
                .doesNotContain("be642-unregistered.example")
                .doesNotContain(UNKNOWN_CLIENT_ID)
                .doesNotContain(STATE);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("error").asText()).isEqualTo(error);
        assertThat(body.get("error_description").asText()).isEqualTo("OAuth 2.0 Parameter: " + namedParameter);
        assertThat(body.size()).as("exactly {error, error_description}: " + response.body()).isEqualTo(2);
    }

    private static String authorize(String clientId, String redirectUri, String responseType, String scope,
                                     boolean pkce, String prompt) {
        StringBuilder path = new StringBuilder("/oauth2/authorize?response_type=").append(responseType)
                .append("&client_id=").append(enc(clientId))
                .append("&redirect_uri=").append(enc(redirectUri))
                .append("&scope=").append(enc(scope))
                .append("&state=").append(STATE);
        if (pkce) {
            path.append("&code_challenge=").append(CODE_CHALLENGE).append("&code_challenge_method=S256");
        }
        if (prompt != null) {
            path.append("&prompt=").append(prompt);
        }
        return path.toString();
    }

    private URI uri(String pathAndQuery) {
        return URI.create("http://localhost:" + port + pathAndQuery);
    }

    /** A browser navigation — {@code Accept: text/html} selects the login-page entry point. */
    private HttpResponse<String> browserGet(String pathAndQuery) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(pathAndQuery)).header("Accept", "text/html").GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static String location(HttpResponse<String> response) {
        return response.headers().firstValue("Location").orElse("");
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
