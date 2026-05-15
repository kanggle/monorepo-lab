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
 *   <li>Regression: the pre-existing demo-spa-client / fan / wms / scm clients
 *       are unaffected (still resolvable, demo-spa still public, fan/wms/scm
 *       still confidential).</li>
 * </ol>
 *
 * <p>Skipped automatically when Docker is unavailable
 * ({@code AbstractIntegrationTest} DockerAvailableCondition).
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
    @DisplayName("regression: demo-spa public + fan/wms/scm confidential clients unaffected by V0015")
    void regression_existingClientsUnaffected() {
        // demo-spa-client — still public (V0008).
        RegisteredClient demoSpa = registeredClientRepository.findByClientId("demo-spa-client");
        assertThat(demoSpa).isNotNull();
        assertThat(demoSpa.getClientAuthenticationMethods())
                .containsExactly(ClientAuthenticationMethod.NONE);
        assertThat(demoSpa.getClientSettings().<String>getSetting("custom.tenant_id"))
                .isEqualTo("fan-platform");

        // fan-platform-user-flow-client — still confidential (V0011).
        RegisteredClient fan = registeredClientRepository.findByClientId("fan-platform-user-flow-client");
        assertThat(fan).isNotNull();
        assertThat(fan.getClientAuthenticationMethods())
                .contains(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);

        // wms-internal-services-client — still confidential client_credentials (V0010).
        RegisteredClient wms = registeredClientRepository.findByClientId("wms-internal-services-client");
        assertThat(wms).isNotNull();
        assertThat(wms.getClientAuthenticationMethods())
                .contains(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        assertThat(wms.getClientSettings().<String>getSetting("custom.tenant_id")).isEqualTo("wms");

        // scm-platform-internal-services-client — still confidential (V0013).
        RegisteredClient scm = registeredClientRepository.findByClientId(
                "scm-platform-internal-services-client");
        assertThat(scm).isNotNull();
        assertThat(scm.getClientAuthenticationMethods())
                .contains(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        assertThat(scm.getClientSettings().<String>getSetting("custom.tenant_id")).isEqualTo("scm");
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
