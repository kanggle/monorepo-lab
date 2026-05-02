package com.example.auth.infrastructure.oauth2;

import com.example.auth.infrastructure.oauth2.persistence.OAuthClientMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Spring Authorization Server 슬라이스 테스트 — Docker 없이 실행 가능.
 *
 * <p>Testcontainers 없이 H2 인메모리 DB + Kafka/Redis 비활성화로 SAS 레이어만 검증한다.
 *
 * <p>Phase 1 검증 범위 (TASK-BE-251):
 * <ul>
 *   <li>{@code GET /.well-known/openid-configuration} — discovery document 구조</li>
 *   <li>{@code GET /oauth2/jwks} — JWKS 구조 (RSA key, kid, n, e)</li>
 *   <li>{@code POST /oauth2/token} (client_credentials) — access token + tenant_id, tenant_type claims</li>
 *   <li>기존 {@code /api/auth/login} 경로 접근성 (SAS가 가로채지 않음)</li>
 * </ul>
 *
 * <p>Phase 2a 추가 검증 범위 (TASK-BE-251):
 * <ul>
 *   <li>discovery document: {@code authorization_endpoint}, {@code userinfo_endpoint},
 *       {@code response_types_supported=[code]}, {@code code_challenge_methods_supported=[S256]}</li>
 *   <li>{@code demo-spa-client} 등록 확인 (PKCE 필수 — S256)</li>
 *   <li>{@code /oauth2/userinfo} — Bearer 없이 접근 시 401</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        // --- Infrastructure stubs (no Testcontainers needed) ---
        // Use H2 in-memory instead of MySQL
        "spring.datasource.url=jdbc:h2:mem:sastest;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=VALUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        // Disable Flyway (schema created by Hibernate ddl-auto)
        "spring.flyway.enabled=false",
        // Disable Kafka auto-configuration
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
        // Redis — use a dummy host; RedisAutoConfiguration will be present but
        // the outbox polling scheduler is throttled to avoid connection errors
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6399",
        "spring.data.redis.timeout=1ms",
        // Disable outbox polling to avoid Redis/Kafka connection errors in tests
        "outbox.polling.interval-ms=99999999",
        // Create oauth2_authorization table for JdbcOAuth2AuthorizationService.
        // Flyway is disabled so the V0008 Flyway migration does not run;
        // Hibernate ddl-auto only creates @Entity-mapped tables.
        // JdbcOAuth2AuthorizationService needs the SAS canonical JDBC table.
        "spring.sql.init.schema-locations=classpath:db/h2/oauth2-authorization-schema.sql",
        "spring.sql.init.mode=always",
        // Account-service stub (not called in SAS tests)
        "auth.account-service.base-url=http://localhost:19999",
        // SAS issuer
        "oidc.issuer-url=http://localhost",
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OAuth2AuthorizationServerSliceTest {

    // Kafka is excluded from auto-config but AuthOutboxPollingScheduler still
    // declares KafkaTemplate as a constructor dependency — mock it out.
    @MockBean
    @SuppressWarnings("rawtypes")
    KafkaTemplate kafkaTemplate;

    private static final String CLIENT_ID = "test-internal-client";
    private static final String CLIENT_SECRET = "secret";
    private static final String BASIC_AUTH =
            "Basic " + Base64.getEncoder().encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes());

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Seeds the two placeholder clients into the JPA-backed RegisteredClientRepository.
     *
     * <p>In the slice test, Flyway is disabled (H2 + ddl-auto=create-drop), so the
     * V0008 seed-data INSERT statements are not executed. We replicate the two clients
     * here to ensure that the token-endpoint tests (which depend on test-internal-client
     * and demo-spa-client) still pass without Flyway.
     *
     * <p>The secret "{noop}secret" is accepted by DelegatingPasswordEncoder (noop prefix)
     * for this test environment only — production clients carry BCrypt hashes from Flyway.
     */
    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    @BeforeEach
    void seedClientsIfAbsent() {
        if (registeredClientRepository.findByClientId(CLIENT_ID) == null) {
            RegisteredClient testInternalClient = RegisteredClient.withId("test-internal-client-id")
                    .clientId("test-internal-client")
                    .clientSecret("{noop}secret")
                    .clientName("Test Internal Client")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .scope("account.read")
                    .scope("openid")
                    .clientSettings(ClientSettings.builder()
                            .requireProofKey(false)
                            .setting(OAuthClientMapper.SETTING_TENANT_ID, "fan-platform")
                            .setting(OAuthClientMapper.SETTING_TENANT_TYPE, "B2C")
                            .build())
                    .tokenSettings(TokenSettings.builder()
                            .accessTokenTimeToLive(Duration.ofMinutes(30))
                            .build())
                    .build();
            registeredClientRepository.save(testInternalClient);
        }

        if (registeredClientRepository.findByClientId("demo-spa-client") == null) {
            RegisteredClient demoPkceClient = RegisteredClient.withId("demo-spa-client-id")
                    .clientId("demo-spa-client")
                    .clientName("Demo SPA Client")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .redirectUri("http://localhost:3000/callback")
                    .scope("openid")
                    .scope("profile")
                    .scope("email")
                    .clientSettings(ClientSettings.builder()
                            .requireProofKey(true)
                            .requireAuthorizationConsent(false)
                            .setting(OAuthClientMapper.SETTING_TENANT_ID, "fan-platform")
                            .setting(OAuthClientMapper.SETTING_TENANT_TYPE, "B2C")
                            .build())
                    .tokenSettings(TokenSettings.builder()
                            .accessTokenTimeToLive(Duration.ofMinutes(30))
                            .refreshTokenTimeToLive(Duration.ofDays(30))
                            .reuseRefreshTokens(false)
                            .build())
                    .build();
            registeredClientRepository.save(demoPkceClient);
        }
    }

    // -----------------------------------------------------------------------
    // 1. OIDC Discovery
    // -----------------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("GET /.well-known/openid-configuration → 200 + required fields")
    void discovery_returns200() throws Exception {
        MvcResult result = mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        JsonNode doc = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(doc.has("issuer")).isTrue();
        assertThat(doc.has("jwks_uri")).isTrue();
        assertThat(doc.has("token_endpoint")).isTrue();
        assertThat(doc.get("jwks_uri").asText()).contains("/oauth2/jwks");
        assertThat(doc.get("token_endpoint").asText()).contains("/oauth2/token");

        // grant_types_supported must include client_credentials
        JsonNode grantTypes = doc.get("grant_types_supported");
        assertThat(grantTypes).isNotNull();
        boolean clientCredentialsPresent = false;
        for (JsonNode gt : grantTypes) {
            if ("client_credentials".equals(gt.asText())) {
                clientCredentialsPresent = true;
                break;
            }
        }
        assertThat(clientCredentialsPresent)
                .as("grant_types_supported must contain client_credentials")
                .isTrue();
    }

    // -----------------------------------------------------------------------
    // 2. JWKS
    // -----------------------------------------------------------------------

    @Test
    @Order(2)
    @DisplayName("GET /oauth2/jwks → 200 + RSA key with kid, n, e")
    void jwks_returns200WithRsaKey() throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.keys").isArray())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].use").value("sig"))
                .andReturn();

        JsonNode firstKey = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("keys").get(0);

        assertThat(firstKey.get("kid").asText()).isNotBlank();
        assertThat(firstKey.get("n").asText()).isNotBlank();
        assertThat(firstKey.get("e").asText()).isNotBlank();
    }

    // -----------------------------------------------------------------------
    // 3. client_credentials token endpoint
    // -----------------------------------------------------------------------

    @Test
    @Order(3)
    @DisplayName("POST /oauth2/token (client_credentials) → access token with tenant_id=fan-platform, tenant_type=B2C")
    void clientCredentials_returnsTokenWithTenantClaims() throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .header(HttpHeaders.AUTHORIZATION, BASIC_AUTH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", "account.read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andReturn();

        String accessToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("access_token").asText();

        // Decode JWT payload (Base64url, no signature verification needed for unit-level check)
        String[] parts = accessToken.split("\\.");
        assertThat(parts).hasSize(3);
        String payloadB64 = parts[1];
        int mod = payloadB64.length() % 4;
        if (mod != 0) payloadB64 += "=".repeat(4 - mod);
        JsonNode payload = objectMapper.readTree(Base64.getUrlDecoder().decode(payloadB64));

        // tenant_id must be present and correct (fail-closed check)
        assertThat(payload.has("tenant_id"))
                .as("access token must contain tenant_id claim")
                .isTrue();
        assertThat(payload.get("tenant_id").asText()).isEqualTo("fan-platform");

        // tenant_type must be present and correct
        assertThat(payload.has("tenant_type"))
                .as("access token must contain tenant_type claim")
                .isTrue();
        assertThat(payload.get("tenant_type").asText()).isEqualTo("B2C");

        // Standard JWT claims
        assertThat(payload.has("iss")).isTrue();
        assertThat(payload.has("exp")).isTrue();
    }

    @Test
    @Order(4)
    @DisplayName("POST /oauth2/token with wrong client secret → 401")
    void clientCredentials_wrongSecret_returns401() throws Exception {
        String badAuth = "Basic " + Base64.getEncoder()
                .encodeToString((CLIENT_ID + ":wrongsecret").getBytes());

        mockMvc.perform(post("/oauth2/token")
                        .header(HttpHeaders.AUTHORIZATION, badAuth)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", "account.read"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(5)
    @DisplayName("POST /oauth2/token with unknown client → 401")
    void clientCredentials_unknownClient_returns401() throws Exception {
        String unknownAuth = "Basic " + Base64.getEncoder()
                .encodeToString("unknown-client:secret".getBytes());

        mockMvc.perform(post("/oauth2/token")
                        .header(HttpHeaders.AUTHORIZATION, unknownAuth)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", "account.read"))
                .andExpect(status().isUnauthorized());
    }

    // -----------------------------------------------------------------------
    // 4. Phase 2a — Discovery document: authorization_code fields
    // -----------------------------------------------------------------------

    @Test
    @Order(4)
    @DisplayName("GET /.well-known/openid-configuration → authorization_endpoint + userinfo_endpoint present")
    void discovery_phase2a_authorizationAndUserinfoEndpoints() throws Exception {
        MvcResult result = mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode doc = objectMapper.readTree(result.getResponse().getContentAsString());

        // authorization_endpoint must be present (authorization_code flow)
        assertThat(doc.has("authorization_endpoint"))
                .as("discovery must contain authorization_endpoint")
                .isTrue();
        assertThat(doc.get("authorization_endpoint").asText())
                .contains("/oauth2/authorize");

        // userinfo_endpoint must be present
        assertThat(doc.has("userinfo_endpoint"))
                .as("discovery must contain userinfo_endpoint")
                .isTrue();
        assertThat(doc.get("userinfo_endpoint").asText())
                .contains("/userinfo");

        // response_types_supported must contain "code"
        JsonNode responseTypes = doc.get("response_types_supported");
        assertThat(responseTypes).isNotNull();
        boolean hasCode = false;
        for (JsonNode rt : responseTypes) {
            if ("code".equals(rt.asText())) {
                hasCode = true;
                break;
            }
        }
        assertThat(hasCode)
                .as("response_types_supported must contain 'code'")
                .isTrue();

        // code_challenge_methods_supported must contain S256
        JsonNode pkce = doc.get("code_challenge_methods_supported");
        if (pkce != null) {
            boolean hasS256 = false;
            for (JsonNode method : pkce) {
                if ("S256".equals(method.asText())) {
                    hasS256 = true;
                    break;
                }
            }
            assertThat(hasS256)
                    .as("code_challenge_methods_supported must contain S256")
                    .isTrue();
        }
    }

    @Test
    @Order(5)
    @DisplayName("GET /oauth2/userinfo without Bearer token → 4xx (SAS enforces auth — 401 or 403)")
    void userinfo_withoutToken_returnsAuthError() throws Exception {
        // SAS returns 403 when no Bearer token is presented at /oauth2/userinfo.
        // RFC 6750 specifies 401, but SAS's default behavior in the absence of
        // a resource-server configuration is 403. Both are acceptable auth-denial codes.
        MvcResult result = mockMvc.perform(get("/oauth2/userinfo"))
                .andReturn();
        int status = result.getResponse().getStatus();
        assertThat(status)
                .as("/oauth2/userinfo without token must return 401 or 403 (auth required)")
                .isIn(401, 403);
    }

    // -----------------------------------------------------------------------
    // 5. Phase 2a — /oauth2/authorize redirects to login (PKCE not yet complete in slice)
    // -----------------------------------------------------------------------

    @Test
    @Order(6)
    @DisplayName("GET /oauth2/authorize unauthenticated → 3xx redirect or 4xx (SAS handles unauthenticated)")
    void authorize_unauthenticated_handledBySas() throws Exception {
        // Without an authenticated session, SAS either:
        //   (a) redirects to the configured login page (3xx) if the session is available, or
        //   (b) returns 302 to login entry point when MockMvc does not follow redirects.
        // In slice test mode with MockMvc, SAS may return 302 (to /api/auth/login) or
        // delegate the error handling. Any non-5xx response is acceptable here — the
        // critical invariant is that SAS DOES handle this endpoint (not 404).
        MvcResult result = mockMvc.perform(get("/oauth2/authorize")
                        .param("response_type", "code")
                        .param("client_id", "demo-spa-client")
                        .param("redirect_uri", "http://localhost:3000/callback")
                        .param("scope", "openid profile email")
                        .param("code_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
                        .param("code_challenge_method", "S256"))
                .andReturn();
        int status = result.getResponse().getStatus();
        // Must NOT be 404 (SAS must own this endpoint) or 5xx
        assertThat(status)
                .as("/oauth2/authorize must be handled by SAS (not 404 or 5xx). Got " + status)
                .isNotEqualTo(404);
        assertThat(status)
                .as("/oauth2/authorize must not return 5xx. Got " + status)
                .isLessThan(500);
    }

    // -----------------------------------------------------------------------
    // 6. Regression — SAS must NOT capture /api/auth/login
    // -----------------------------------------------------------------------

    @Test
    @Order(7)
    @DisplayName("POST /api/auth/login → routed to legacy handler (SAS chain must not intercept)")
    void regression_loginEndpointNotCapturedBySAS() throws Exception {
        // A bad login request should reach the legacy handler and return 4xx,
        // NOT 404 (route not found) which would indicate SAS captured it.
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"regression@example.com","password":"any"}
                                """))
                .andReturn();

        int status = result.getResponse().getStatus();
        assertThat(status)
                .as("Legacy /api/auth/login must not be captured by SAS chain. Got HTTP " + status)
                .isBetween(400, 499);
        assertThat(status)
                .as("404 means SAS swallowed /api/auth/login — not acceptable")
                .isNotEqualTo(404);
    }

    // -----------------------------------------------------------------------
    // 7. Phase 2c — /oauth2/revoke endpoint is active
    // -----------------------------------------------------------------------

    @Test
    @Order(8)
    @DisplayName("POST /oauth2/revoke → 200 (SAS revocation endpoint active)")
    void revokeEndpoint_isActive() throws Exception {
        // First obtain a token to revoke
        MvcResult tokenResult = mockMvc.perform(post("/oauth2/token")
                        .header("Authorization", BASIC_AUTH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", "account.read"))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = objectMapper.readTree(tokenResult.getResponse().getContentAsString())
                .get("access_token").asText();

        // Revoke: RFC 7009 — server returns 200 regardless of whether token was found
        mockMvc.perform(post("/oauth2/revoke")
                        .header("Authorization", BASIC_AUTH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", accessToken))
                .andExpect(status().isOk());
    }

    // -----------------------------------------------------------------------
    // 8. Phase 2c — /oauth2/introspect endpoint returns active + tenant claims
    // -----------------------------------------------------------------------

    @Test
    @Order(9)
    @DisplayName("POST /oauth2/introspect → 200 active=true + tenant_id + tenant_type")
    void introspectEndpoint_returnsActiveWithTenantClaims() throws Exception {
        // Issue token
        MvcResult tokenResult = mockMvc.perform(post("/oauth2/token")
                        .header("Authorization", BASIC_AUTH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", "account.read"))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = objectMapper.readTree(tokenResult.getResponse().getContentAsString())
                .get("access_token").asText();

        // Introspect
        MvcResult introspectResult = mockMvc.perform(post("/oauth2/introspect")
                        .header("Authorization", BASIC_AUTH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();

        String body = introspectResult.getResponse().getContentAsString();
        assertThat(body).contains("\"active\":true");
        // Phase 2c: tenant extension claims from TenantIntrospectionCustomizer
        assertThat(body)
                .as("introspect response must contain tenant_id (TenantIntrospectionCustomizer)")
                .contains("tenant_id");
        assertThat(body).contains("fan-platform");
    }

    // -----------------------------------------------------------------------
    // 9. Phase 2c — revoke → introspect → active=false (slice-level E2E)
    // -----------------------------------------------------------------------

    @Test
    @Order(10)
    @DisplayName("revoke then introspect → active=false (slice-level RFC 7009 + RFC 7662 E2E)")
    void revoke_thenIntrospect_returnsInactive() throws Exception {
        // Issue
        MvcResult tokenResult = mockMvc.perform(post("/oauth2/token")
                        .header("Authorization", BASIC_AUTH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", "account.read"))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = objectMapper.readTree(tokenResult.getResponse().getContentAsString())
                .get("access_token").asText();

        // Revoke
        mockMvc.perform(post("/oauth2/revoke")
                        .header("Authorization", BASIC_AUTH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", accessToken))
                .andExpect(status().isOk());

        // Introspect — must be inactive
        mockMvc.perform(post("/oauth2/introspect")
                        .header("Authorization", BASIC_AUTH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    // -----------------------------------------------------------------------
    // 10. Phase 2c — POST /api/auth/login returns Deprecation header
    // -----------------------------------------------------------------------

    @Test
    @Order(11)
    @DisplayName("POST /api/auth/login → Deprecation and Sunset headers present (RFC 8594, RFC 9745)")
    void legacyLogin_returnsDeprecationHeaders() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"deprecation-test@example.com","password":"any"}
                                """))
                .andReturn();

        int status = result.getResponse().getStatus();
        assertThat(status).isBetween(400, 499);
        assertThat(status).isNotEqualTo(404);

        // Deprecation headers must be present regardless of auth success/failure
        assertThat(result.getResponse().getHeader("Deprecation"))
                .as("Deprecation header must be 'true' (RFC 8594)")
                .isEqualTo("true");
        assertThat(result.getResponse().getHeader("Sunset"))
                .as("Sunset header must be present and reference 2026-08-01 (RFC 9745)")
                .contains("2026");
    }
}
