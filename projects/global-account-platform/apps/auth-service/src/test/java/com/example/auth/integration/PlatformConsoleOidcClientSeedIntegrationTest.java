package com.example.auth.integration;

import com.example.testsupport.integration.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
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
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-296: verifies the Flyway V0015 seed of the {@code platform-console-web}
 * OIDC <b>public</b> client.
 *
 * <p>Asserts:
 * <ol>
 *   <li>The client is resolvable via the real {@link RegisteredClientRepository}
 *       (JPA-backed, loaded from the Flyway-seeded row).</li>
 *   <li>It is a public client: {@code client_authentication_methods=[none]},
 *       no client secret, PKCE required.</li>
 *   <li>Grants are exactly {@code authorization_code} + {@code refresh_token};
 *       redirect uri + scopes per the contract.</li>
 *   <li>Full Authorization Code + PKCE flow issues tokens.</li>
 *   <li>{@code refresh_token} rotation behaves like the existing public-client
 *       lineage (ADR-003): a NEW refresh token is returned (rotation, not
 *       reuse) WITHOUT any client secret.</li>
 *   <li>Regression: V0015 added <em>exactly</em> the platform-console-web row
 *       and disturbed no pre-existing oauth_clients row. Pre-existing
 *       fan/wms/scm rows are asserted intact at the JDBC row level (existence +
 *       identity-defining columns); demo-spa-client + platform-console-web are
 *       additionally validated through the full RegisteredClient mapping path.
 *       The pre-existing rows are deliberately NOT loaded via
 *       {@code findByClientId} for the full RegisteredClient mapping because
 *       V0011/V0012 clients carry a hand-written array-valued
 *       {@code settings.client.post-logout-redirect-uris} that trips a
 *       pre-existing, orthogonal {@code OAuthClientMapper} deserialization
 *       defect (Jackson {@code InvalidTypeIdException}) unrelated to V0015 —
 *       see the task file's "Discovered pre-existing defect" note.</li>
 * </ol>
 *
 * <p>Skipped automatically when Docker is unavailable
 * ({@code AbstractIntegrationTest} DockerAvailableCondition).
 *
 * <h3>Discovered pre-existing defect (out of scope for TASK-BE-296)</h3>
 *
 * <p>Running this class's regression case on CI (PR #568, Linux
 * {@code :auth-service:integrationTest}) surfaced a <b>pre-existing latent
 * defect orthogonal to and NOT caused by V0015</b>. The
 * {@code regression_existingClientsUnaffected} test was the first test in any
 * suite to call {@code findByClientId("fan-platform-user-flow-client")}, so
 * no prior test exercised this code path.
 *
 * <p><b>Symptom:</b>
 * <pre>
 * OAuthClientMappingException: Cannot deserialize ClientSettings for
 *   clientId=fan-platform-user-flow-client
 *   at OAuthClientMapper.deserializeClientSettings(OAuthClientMapper.java:218)
 * Caused by: com.fasterxml.jackson.databind.exc.InvalidTypeIdException:
 *   Could not resolve type id 'http://localhost:3000/' as a subtype of
 *   java.lang.Object: no such class found
 * </pre>
 *
 * <p><b>Root cause hypothesis:</b> {@code OAuthClientMapper.buildSasMapper()}
 * calls {@code SecurityJackson2Modules.enableDefaultTyping(mapper)}, which
 * applies polymorphic default typing — collections are written/read as a
 * {@code [typeId, value]} tuple (e.g. {@code ["java.util.ArrayList", [...]]}).
 * The V0011 {@code fan-platform-user-flow-client} seed (and the V0012
 * ecommerce clients) embed a <b>hand-written plain JSON array</b> for the
 * custom {@code settings.client.post-logout-redirect-uris} setting:
 * {@code ["http://localhost:3000/","http://fan-platform.local/"]}. On read,
 * default typing treats this 2-element array as a polymorphic
 * {@code [typeId, value]} tuple, takes element 0
 * ({@code "http://localhost:3000/"}) as the Java type id, and fails. Clients
 * <i>without</i> an array-valued custom ClientSettings entry
 * ({@code demo-spa-client}, {@code test-internal-client}, all {@code wms-*} /
 * {@code scm-*} clients, and the new {@code platform-console-web})
 * deserialize cleanly.
 *
 * <p><b>V0015 causal role: none.</b> V0015 inserts a single new row whose
 * {@code client_settings} contains only scalar booleans and does not modify,
 * update, or reference any existing row. The failure occurs while mapping a
 * V0011 row that V0015 never touched.
 *
 * <p><b>Handling:</b> {@code regression_existingClientsUnaffected} was
 * narrowed to validate the actual BE-296 invariant — "V0015 added exactly
 * {@code platform-console-web} and disturbed no pre-existing row" — at the
 * JDBC row level for the pre-existing fan/wms/scm rows, while still exercising
 * the full {@code RegisteredClient} mapping for clients proven to deserialize
 * cleanly ({@code demo-spa-client}, {@code platform-console-web}). The defect
 * itself is intentionally <b>NOT fixed in this PR</b> — fixing it touches an
 * unrelated existing client + the shared mapper, risking regression to live
 * fan-platform / ecommerce OAuth clients.
 *
 * <p><b>Follow-up status: RESOLVED by TASK-BE-297.</b> The corrective forward
 * Flyway migration {@code V0016__fix_post_logout_redirect_uris_default_typing.sql}
 * re-serializes the three affected rows'
 * {@code settings.client.post-logout-redirect-uris} into the SAS default-typed
 * {@code ["java.util.ArrayList",[...]]} wrapper-array envelope (byte-equivalent
 * effective ClientSettings — same key, same ordered {@code List<String>}). The
 * shared {@code OAuthClientMapper} / {@code SecurityJackson2Modules} config and
 * every clean client are untouched (least blast radius). Severity verdict
 * (BE-297): genuine latent PRODUCTION break, not a test-only artifact —
 * {@code JpaRegisteredClientRepository} is the production
 * {@link RegisteredClientRepository} bean on the {@code @Order(1)} SAS chain.
 * Round-trip + clean-client regression coverage:
 * {@code OAuthClientPostLogoutRedirectUriSeedIntegrationTest} +
 * {@code OAuthClientMapperTest} (TASK-BE-297 cases).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PlatformConsoleOidcClientSeedIntegrationTest extends AbstractIntegrationTest {

    private static final String CONSOLE_CLIENT_ID = "platform-console-web";
    private static final String CONSOLE_REDIRECT_URI = "http://console.local/api/auth/callback";

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    static WireMockServer wireMock;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        registry.add("auth.account-service.base-url", wireMock::baseUrl);

        // OidcUserInfoMapper / token customizer profile lookup stub.
        wireMock.stubFor(WireMock.get(WireMock.urlMatching("/internal/accounts/.+/profile"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "accountId": "op-account",
                                  "email": "operator@example.com",
                                  "emailVerified": true,
                                  "displayName": "Console Operator",
                                  "preferredUsername": "operator",
                                  "locale": "ko-KR",
                                  "tenantId": "gap",
                                  "tenantType": "B2B_ENTERPRISE"
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

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static String codeVerifier;
    private static String codeChallenge;

    @BeforeAll
    static void generatePkce() throws Exception {
        codeVerifier = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(UUID.randomUUID().toString().replace("-", "")
                        .getBytes(StandardCharsets.UTF_8));
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] hash = sha256.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
        codeChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    // -----------------------------------------------------------------------
    // 1. Seed verification — public client config
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("V0015 seed: platform-console-web is a resolvable PUBLIC client (no secret, PKCE, none auth)")
    void seed_publicClientConfig() {
        RegisteredClient client = registeredClientRepository.findByClientId(CONSOLE_CLIENT_ID);

        assertThat(client).as("platform-console-web must be Flyway-seeded").isNotNull();
        assertThat(client.getClientId()).isEqualTo(CONSOLE_CLIENT_ID);

        // Public client: only NONE auth method, no usable secret.
        assertThat(client.getClientAuthenticationMethods())
                .as("public client → client_authentication_methods must be exactly [none]")
                .containsExactly(ClientAuthenticationMethod.NONE);

        // PKCE required.
        assertThat(client.getClientSettings().isRequireProofKey())
                .as("PKCE must be required (require-proof-key=true)")
                .isTrue();

        // Grants: authorization_code + refresh_token only.
        assertThat(client.getAuthorizationGrantTypes())
                .containsExactlyInAnyOrder(
                        AuthorizationGrantType.AUTHORIZATION_CODE,
                        AuthorizationGrantType.REFRESH_TOKEN);

        // Redirect uri per console-registry-api.md / multi-tenancy.md.
        assertThat(client.getRedirectUris()).contains(CONSOLE_REDIRECT_URI);

        // Scopes per contract.
        assertThat(client.getScopes())
                .contains("openid", "profile", "email", "tenant.read");

        // Rotation (not reuse) — consistent with demo-spa-client public lineage.
        assertThat(client.getTokenSettings().isReuseRefreshTokens())
                .as("refresh tokens must rotate (reuse-refresh-tokens=false)")
                .isFalse();

        // tenant_id carried in ClientSettings (Option B), scoped to 'gap'.
        assertThat(client.getClientSettings().<String>getSetting("custom.tenant_id"))
                .isEqualTo("gap");
    }

    // -----------------------------------------------------------------------
    // 2. Auth Code + PKCE issues tokens; refresh rotation w/o client secret
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("AuthCode+PKCE → tokens; refresh_token (no secret) → rotated RT (ADR-003 lineage)")
    void authCodePkce_thenRefreshRotation_noClientSecret() throws Exception {
        // Step 1: /oauth2/authorize (authenticated user) → code
        MvcResult authResult = mockMvc.perform(get("/oauth2/authorize")
                        .with(user("op-account-001")
                                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .queryParam("response_type", "code")
                        .queryParam("client_id", CONSOLE_CLIENT_ID)
                        .queryParam("redirect_uri", CONSOLE_REDIRECT_URI)
                        .queryParam("scope", "openid profile email tenant.read")
                        .queryParam("code_challenge", codeChallenge)
                        .queryParam("code_challenge_method", "S256")
                        .queryParam("state", "console-state"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String location = authResult.getResponse().getHeader("Location");
        assertThat(location).isNotNull().startsWith(CONSOLE_REDIRECT_URI).contains("code=");
        String code = extractParam(location, "code");
        assertThat(code).isNotBlank();

        // Step 2: /oauth2/token (authorization_code + code_verifier, NO secret)
        MvcResult tokenResult = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", CONSOLE_REDIRECT_URI)
                        .param("client_id", CONSOLE_CLIENT_ID)
                        .param("code_verifier", codeVerifier))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode tokens = objectMapper.readTree(tokenResult.getResponse().getContentAsString());
        assertThat(tokens.get("access_token").asText()).isNotBlank();
        assertThat(tokens.has("refresh_token"))
                .as("refresh_token must be issued (offline rotation lineage)")
                .isTrue();
        String refreshToken = tokens.get("refresh_token").asText();

        // Step 3: refresh_token grant — PUBLIC client, NO client secret.
        // Mirrors ADR-003 옵션 B closure: PublicClientRefreshTokenAuthentication
        // Converter authenticates this NONE-method client and rotation returns a
        // NEW refresh token.
        MvcResult refreshResult = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", refreshToken)
                        .param("client_id", CONSOLE_CLIENT_ID))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode rotated = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
        assertThat(rotated.get("access_token").asText()).isNotBlank();
        assertThat(rotated.has("refresh_token"))
                .as("rotation must mint a new refresh_token (reuse-refresh-tokens=false)")
                .isTrue();
        assertThat(rotated.get("refresh_token").asText())
                .as("rotated refresh_token must differ from the original")
                .isNotEqualTo(refreshToken);
    }

    @Test
    @DisplayName("public client must NOT accept a client secret on the token endpoint")
    void publicClient_rejectsClientSecret() throws Exception {
        // A public client presenting a client_secret via Basic auth must be
        // rejected (no secret is registered → invalid_client).
        String basic = "Basic " + Base64.getEncoder()
                .encodeToString((CONSOLE_CLIENT_ID + ":anything").getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(post("/oauth2/token")
                        .header("Authorization", basic)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", "irrelevant")
                        .param("redirect_uri", CONSOLE_REDIRECT_URI)
                        .param("client_id", CONSOLE_CLIENT_ID)
                        .param("code_verifier", codeVerifier))
                .andExpect(status().is4xxClientError());
    }

    // -----------------------------------------------------------------------
    // 3. Regression — existing clients unaffected
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("regression: V0015 added exactly platform-console-web; demo-spa public + fan/wms/scm rows unchanged")
    void regression_existingClientsUnaffected() {
        // ---------------------------------------------------------------
        // Invariant 1 — V0015 is a single-row INSERT: it adds exactly the
        // platform-console-web row and disturbs no pre-existing oauth_clients
        // row. We assert the seeded baseline set is intact at the row level.
        //
        // NOTE: this assertion deliberately reads the oauth_clients table via
        // JdbcTemplate rather than RegisteredClientRepository#findByClientId
        // for every client. fan-platform-user-flow-client (V0011) and the
        // ecommerce clients (V0012) carry an array-valued
        // `settings.client.post-logout-redirect-uris` in client_settings that
        // was hand-written as a plain JSON array, NOT in the SAS
        // default-typed [typeId,value] form the OAuthClientMapper's
        // SecurityJackson2Modules-enriched ObjectMapper expects on read. Their
        // full RegisteredClient deserialization throws
        // OAuthClientMappingException (Jackson InvalidTypeIdException, type id
        // 'http://localhost:3000/'). That is a PRE-EXISTING latent defect in
        // those seeds — orthogonal to and NOT caused by V0015 (V0015 is a pure
        // single-row INSERT of platform-console-web; it modifies / references
        // no existing oauth_clients row, and its own client_settings carry no
        // array-valued custom setting). No prior integration test ever loaded
        // fan-platform-user-flow-client via findByClientId, so the defect was
        // never exercised before this BE-296 regression. Fixing it touches an
        // unrelated existing client + the shared OAuthClientMapper (risking
        // regression to live fan-platform / ecommerce OAuth clients) and is
        // therefore OUT OF SCOPE for BE-296 — recommended as a separate future
        // task (full root-cause writeup is in this test's class Javadoc). To
        // keep this BE-296 regression honest about its actual invariant
        // ("V0015 did not disturb existing clients") without coupling it to
        // that unrelated mapper defect, the pre-existing-rows assertions
        // operate at the DB row level (existence + identity-defining columns),
        // while full RegisteredClient mapping is exercised only for clients
        // proven to deserialize cleanly (demo-spa-client, platform-console-web
        // — both in the passing tests' lineage; neither carries
        // post-logout-redirect-uris).
        // ---------------------------------------------------------------
        Integer consoleRowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oauth_clients WHERE client_id = ?",
                Integer.class, CONSOLE_CLIENT_ID);
        assertThat(consoleRowCount)
                .as("V0015 must have inserted exactly one platform-console-web row")
                .isEqualTo(1);

        // The pre-existing seed rows (V0008/V0010/V0011/V0013) must still be
        // present, with their identity-defining columns untouched by V0015.
        // (client_authentication_methods + tenant_id are the columns BE-296
        // could only have regressed if V0015 had mistakenly UPDATEd a row.)
        assertSeedRowIntact("fan-platform-user-flow-client", "client_secret_basic", "fan-platform");
        assertSeedRowIntact("wms-internal-services-client", "client_secret_basic", "wms");
        assertSeedRowIntact("scm-platform-internal-services-client", "client_secret_basic", "scm");
        assertSeedRowIntact("demo-spa-client", "none", "fan-platform");

        // ---------------------------------------------------------------
        // Invariant 2 — demo-spa-client still resolves as a PUBLIC client
        // through the full RegisteredClient mapping path. It deserializes
        // cleanly (V0008 client_settings has no array-valued custom setting),
        // so this confirms V0015 did not perturb the public-client lineage
        // that platform-console-web joins.
        // ---------------------------------------------------------------
        RegisteredClient demoSpa = registeredClientRepository.findByClientId("demo-spa-client");
        assertThat(demoSpa).as("demo-spa-client must still resolve").isNotNull();
        assertThat(demoSpa.getClientAuthenticationMethods())
                .as("demo-spa-client must still be a public client")
                .containsExactly(ClientAuthenticationMethod.NONE);
        assertThat(demoSpa.getClientSettings().<String>getSetting("custom.tenant_id"))
                .isEqualTo("fan-platform");

        // ---------------------------------------------------------------
        // Invariant 3 — platform-console-web itself resolves through the same
        // full mapping path (no array-valued custom setting; safe), confirming
        // V0015's own row is well-formed and a public client distinct from the
        // pre-existing ones.
        // ---------------------------------------------------------------
        RegisteredClient console = registeredClientRepository.findByClientId(CONSOLE_CLIENT_ID);
        assertThat(console).as("platform-console-web must resolve").isNotNull();
        assertThat(console.getClientAuthenticationMethods())
                .containsExactly(ClientAuthenticationMethod.NONE);
        assertThat(console.getId()).isNotEqualTo(demoSpa.getId());
    }

    /**
     * Asserts a pre-existing seeded oauth_clients row still exists with its
     * identity-defining columns unchanged. Operates purely at the JDBC row
     * level so it does NOT trip the pre-existing
     * {@code post-logout-redirect-uris} ClientSettings deserialization defect
     * carried by V0011/V0012 clients (orthogonal to BE-296 / V0015).
     *
     * @param clientId               the seeded client_id
     * @param expectedAuthMethodJson the literal token expected inside the
     *                               client_authentication_methods JSON array
     *                               (e.g. {@code client_secret_basic} / {@code none})
     * @param expectedTenantId       the expected tenant_id column value
     */
    private void assertSeedRowIntact(String clientId,
                                     String expectedAuthMethodJson,
                                     String expectedTenantId) {
        var row = jdbcTemplate.queryForMap(
                "SELECT client_authentication_methods, tenant_id "
                        + "FROM oauth_clients WHERE client_id = ?",
                clientId);
        assertThat(String.valueOf(row.get("client_authentication_methods")))
                .as("%s client_authentication_methods must be unchanged by V0015", clientId)
                .contains(expectedAuthMethodJson);
        assertThat(String.valueOf(row.get("tenant_id")))
                .as("%s tenant_id must be unchanged by V0015", clientId)
                .isEqualTo(expectedTenantId);
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private static String extractParam(String location, String name) {
        String query = location.contains("?")
                ? location.substring(location.indexOf("?") + 1) : location;
        for (String param : query.split("&")) {
            if (param.startsWith(name + "=")) {
                return param.substring((name + "=").length());
            }
        }
        return null;
    }
}
