package com.example.admin.integration;

import com.example.admin.support.OperatorJwtTestFixture;
import com.example.testsupport.integration.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-298 / ADR-MONO-014 — end-to-end Testcontainers IT for
 * {@code POST /api/admin/auth/token-exchange}.
 *
 * <p>Boots admin-service against real MySQL + Kafka (AbstractIntegrationTest)
 * + Redis, with a WireMock server serving the auth-service JWKS. The test
 * mints GAP OIDC {@code platform-console-web} subject tokens with the JWKS key
 * and verifies:
 * <ul>
 *   <li>valid subject token + mapped operator → operator token
 *       ({@code token_type=admin}, {@code iss=admin-service}), scope from the
 *       operator row;</li>
 *   <li>SUPER_ADMIN operator → the exchanged token resolves to the
 *       {@code tenant_id='*'} row, not anything from the OIDC token;</li>
 *   <li>valid OIDC token + NO {@code admin_operators} mapping → 401
 *       fail-closed (no token minted);</li>
 *   <li>subject token wrong-issuer / wrong-audience / expired / wrong-sig
 *       → 401;</li>
 *   <li><b>regression</b>: the existing {@code POST /api/admin/auth/login}
 *       operator-token path is unchanged AND
 *       {@code OperatorAuthenticationFilter} still rejects a RAW GAP OIDC
 *       token on a normal {@code /api/admin/**} endpoint (ADR-014 D1 Option A
 *       stays rejected).</li>
 * </ul>
 *
 * <p>Skipped automatically when Docker is unavailable
 * ({@code AbstractIntegrationTest} DockerAvailableCondition) — expected on the
 * local Windows host; CI Linux is authoritative.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class TokenExchangeIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static final String OIDC_ISSUER = "http://localhost:8081";
    private static final String CONSOLE_CLIENT = "platform-console-web";
    private static final String JWKS_KID = "auth-test-kid";

    static WireMockServer wireMock;
    static KeyPair authKeyPair;            // GAP OIDC signing key (auth-service)
    static OperatorJwtTestFixture jwt;     // raw-OIDC regression helper / token signer
    static String adminSigningKeyPem;

    private static final String MAPPED_OP_UUID  = "00000000-0000-7000-8000-0000000be298";
    private static final String MAPPED_OP_OIDC  = "oidc-sub-mapped-0001";
    private static final String SUPER_OP_UUID   = "00000000-0000-7000-8000-0000000be299";
    private static final String SUPER_OP_OIDC   = "oidc-sub-super-0001";
    private static final String UNMAPPED_OIDC   = "oidc-sub-not-provisioned";
    // TASK-BE-377 / ADR-MONO-035 4c — an OIDC-only operator (NULL password_hash):
    // no local break-glass password, authenticates purely via OIDC token-exchange.
    private static final String OIDC_ONLY_OP_UUID = "00000000-0000-7000-8000-0000000be377";
    private static final String OIDC_ONLY_OP_OIDC = "oidc-sub-oidc-only-0001";

    @BeforeAll
    static void setupShared() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        authKeyPair = gen.generateKeyPair();

        jwt = new OperatorJwtTestFixture();
        java.security.PrivateKey adminPk = extractPrivateKey(jwt);
        adminSigningKeyPem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(adminPk.getEncoded())
                + "\n-----END PRIVATE KEY-----\n";

        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/auth/jwks"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jwksJson((RSAPublicKey) authKeyPair.getPublic()))));
    }

    @AfterAll
    static void tearDownShared() {
        if (wireMock != null) wireMock.stop();
    }

    private static java.security.PrivateKey extractPrivateKey(OperatorJwtTestFixture fixture) {
        try {
            var f = OperatorJwtTestFixture.class.getDeclaredField("keyPair");
            f.setAccessible(true);
            return ((KeyPair) f.get(fixture)).getPrivate();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String jwksJson(RSAPublicKey key) {
        String n = b64Url(key.getModulus().toByteArray());
        String e = b64Url(key.getPublicExponent().toByteArray());
        return "{\"keys\":[{\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"RS256\",\"kid\":\""
                + JWKS_KID + "\",\"n\":\"" + n + "\",\"e\":\"" + e + "\"}]}";
    }

    private static String b64Url(byte[] bytes) {
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] s = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, s, 0, s.length);
            bytes = s;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("admin.jwt.active-signing-kid", () -> "test-key-001");
        registry.add("admin.jwt.signing-keys.test-key-001", () -> adminSigningKeyPem);
        registry.add("admin.jwt.issuer", () -> "admin-service");
        registry.add("admin.jwt.expected-token-type", () -> "admin");
        // GAP OIDC subject-token validation → WireMock JWKS.
        registry.add("admin.oidc.jwks-uri", () -> wireMock.baseUrl() + "/internal/auth/jwks");
        registry.add("admin.oidc.issuer", () -> OIDC_ISSUER);
        registry.add("admin.oidc.audience", () -> CONSOLE_CLIENT);
        registry.add("admin.account-service.base-url", wireMock::baseUrl);
        registry.add("admin.auth-service.base-url", wireMock::baseUrl);
        registry.add("admin.security-service.base-url", wireMock::baseUrl);
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void seed() {
        seedOperator(MAPPED_OP_UUID, "wms", MAPPED_OP_OIDC, "ACTIVE");
        seedOperator(SUPER_OP_UUID, "*", SUPER_OP_OIDC, "ACTIVE");
        seedOidcOnlyOperator(OIDC_ONLY_OP_UUID, "wms", OIDC_ONLY_OP_OIDC, "ACTIVE");
    }

    /**
     * TASK-BE-377 / ADR-MONO-035 4c — seed an OIDC-only operator with a NULL
     * {@code password_hash} (no break-glass local password). Proves the V0037
     * nullable demotion persists + the operator authenticates only via OIDC
     * token-exchange (and cannot local-login).
     */
    private void seedOidcOnlyOperator(String operatorId, String tenantId, String oidcSubject, String status) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_operators WHERE operator_id = ?",
                Integer.class, operatorId);
        if (existing == null || existing == 0) {
            jdbcTemplate.update("""
                    INSERT INTO admin_operators
                      (operator_id, tenant_id, email, password_hash, display_name,
                       status, oidc_subject, created_at, updated_at, version)
                    VALUES (?, ?, ?, NULL, ?, ?, ?, NOW(6), NOW(6), 0)
                    """, operatorId, tenantId, operatorId + "@example.com",
                    "OidcOnly " + operatorId.substring(operatorId.length() - 4),
                    status, oidcSubject);
        } else {
            jdbcTemplate.update(
                    "UPDATE admin_operators SET status = ?, oidc_subject = ?, password_hash = NULL "
                            + "WHERE operator_id = ?",
                    status, oidcSubject, operatorId);
        }
    }

    private void seedOperator(String operatorId, String tenantId, String oidcSubject, String status) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_operators WHERE operator_id = ?",
                Integer.class, operatorId);
        if (existing == null || existing == 0) {
            jdbcTemplate.update("""
                    INSERT INTO admin_operators
                      (operator_id, tenant_id, email, password_hash, display_name,
                       status, oidc_subject, created_at, updated_at, version)
                    VALUES (?, ?, ?, 'x', ?, ?, ?, NOW(6), NOW(6), 0)
                    """, operatorId, tenantId, operatorId + "@example.com",
                    "Op " + operatorId.substring(operatorId.length() - 4),
                    status, oidcSubject);
        } else {
            jdbcTemplate.update(
                    "UPDATE admin_operators SET status = ?, oidc_subject = ? WHERE operator_id = ?",
                    status, oidcSubject, operatorId);
        }
    }

    // --- GAP OIDC subject-token minting helpers ---------------------------

    private JwtBuilder oidcToken(String sub) {
        Instant now = Instant.now();
        return Jwts.builder()
                .header().keyId(JWKS_KID).and()
                .subject(sub)
                .issuer(OIDC_ISSUER)
                .audience().add(CONSOLE_CLIENT).and()
                .claim("tenant_id", "fan-platform")   // deliberately a DIFFERENT
                .claim("tenant_type", "B2C_CONSUMER") // tenant than the operator row
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(15, ChronoUnit.MINUTES)));
    }

    private String sign(JwtBuilder b) {
        return b.signWith(authKeyPair.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private String exchangeBody(String subjectToken) {
        return """
                {"grant_type":"urn:ietf:params:oauth:grant-type:token-exchange",
                 "subject_token":"%s",
                 "subject_token_type":"urn:ietf:params:oauth:token-type:access_token"}
                """.formatted(subjectToken);
    }

    // --- Tests ------------------------------------------------------------

    @Test
    @DisplayName("valid GAP OIDC token + mapped operator → operator token (token_type=admin, iss=admin-service)")
    void validExchange_returnsOperatorToken() throws Exception {
        String subject = sign(oidcToken(MAPPED_OP_OIDC));

        MvcResult res = mockMvc.perform(post("/api/admin/auth/token-exchange")
                        .contentType("application/json")
                        .content(exchangeBody(subject)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("admin"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        String operatorToken = objectMapper.readTree(
                res.getResponse().getContentAsString()).get("accessToken").asText();
        // The exchanged token is the SAME operator token shape OperatorJwt
        // TestFixture verifies — it must pass a normal /api/admin/** endpoint.
        mockMvc.perform(get("/api/admin/me")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operatorId").value(MAPPED_OP_UUID));
    }

    @Test
    @DisplayName("BE-377: OIDC-only operator (NULL password_hash) → token-exchange succeeds; minted token authenticates /api/admin/**")
    void oidcOnlyOperator_nullPasswordHash_exchangesAndAuthenticates() throws Exception {
        // ADR-MONO-035 4c: an operator with NO local break-glass password
        // (password_hash NULL) is OIDC-PRIMARY — the unified IAM OIDC credential,
        // exchanged into an operator token, is its only required login. Proves the
        // V0037 nullable demotion persists AND the OIDC path works for such an operator.
        String subject = sign(oidcToken(OIDC_ONLY_OP_OIDC));

        MvcResult res = mockMvc.perform(post("/api/admin/auth/token-exchange")
                        .contentType("application/json")
                        .content(exchangeBody(subject)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("admin"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        String operatorToken = objectMapper.readTree(
                res.getResponse().getContentAsString()).get("accessToken").asText();
        mockMvc.perform(get("/api/admin/me")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operatorId").value(OIDC_ONLY_OP_UUID));

        // Sanity: the row really has a NULL password_hash (break-glass absent).
        String hash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM admin_operators WHERE operator_id = ?",
                String.class, OIDC_ONLY_OP_UUID);
        assertThat(hash).isNull();
    }

    @Test
    @DisplayName("BE-377: OIDC-only operator (NULL password_hash) CANNOT local-login → 401 INVALID_CREDENTIALS (break-glass absent)")
    void oidcOnlyOperator_localLogin_returns401() throws Exception {
        // The break-glass password login fail-closes for a null-hash operator: it must
        // authenticate via OIDC, never the local password (security.md §Operator
        // Credential Convergence). Any password presented → 401 (after the timing dummy).
        String loginBody = """
                {"operatorId":"%s","password":"AnyPass1!"}
                """.formatted(OIDC_ONLY_OP_UUID);
        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType("application/json")
                        .content(loginBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("SUPER_ADMIN operator → exchanged token resolves to the tenant_id='*' row, not the OIDC tenant claim")
    void superAdmin_scopeFromRowNotOidc() throws Exception {
        // The OIDC token carries tenant_id=fan-platform; the operator row is
        // tenant_id='*'. The exchanged operator token must resolve to the
        // SUPER_ADMIN operator (its row scope), proving scope-from-row.
        String subject = sign(oidcToken(SUPER_OP_OIDC));

        MvcResult res = mockMvc.perform(post("/api/admin/auth/token-exchange")
                        .contentType("application/json")
                        .content(exchangeBody(subject)))
                .andExpect(status().isOk())
                .andReturn();

        String operatorToken = objectMapper.readTree(
                res.getResponse().getContentAsString()).get("accessToken").asText();
        mockMvc.perform(get("/api/admin/me")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operatorId").value(SUPER_OP_UUID));

        String rowTenant = jdbcTemplate.queryForObject(
                "SELECT tenant_id FROM admin_operators WHERE operator_id = ?",
                String.class, SUPER_OP_UUID);
        assertThat(rowTenant).isEqualTo("*");
    }

    @Test
    @DisplayName("valid OIDC token but NO admin_operators mapping → 401 fail-closed (no token)")
    void noMapping_returns401() throws Exception {
        String subject = sign(oidcToken(UNMAPPED_OIDC));
        mockMvc.perform(post("/api/admin/auth/token-exchange")
                        .contentType("application/json")
                        .content(exchangeBody(subject)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("mapped operator is DISABLED → 401 fail-closed")
    void deactivatedOperator_returns401() throws Exception {
        jdbcTemplate.update("UPDATE admin_operators SET status='DISABLED' WHERE operator_id=?",
                MAPPED_OP_UUID);
        String subject = sign(oidcToken(MAPPED_OP_OIDC));
        mockMvc.perform(post("/api/admin/auth/token-exchange")
                        .contentType("application/json")
                        .content(exchangeBody(subject)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("subject token wrong issuer → 401")
    void wrongIssuer_returns401() throws Exception {
        String subject = sign(oidcToken(MAPPED_OP_OIDC).issuer("https://evil.example.com"));
        mockMvc.perform(post("/api/admin/auth/token-exchange")
                        .contentType("application/json")
                        .content(exchangeBody(subject)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("subject token wrong audience (different client) → 401")
    void wrongAudience_returns401() throws Exception {
        Instant now = Instant.now();
        String subject = sign(Jwts.builder()
                .header().keyId(JWKS_KID).and()
                .subject(MAPPED_OP_OIDC)
                .issuer(OIDC_ISSUER)
                .audience().add("a-different-client").and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(15, ChronoUnit.MINUTES))));
        mockMvc.perform(post("/api/admin/auth/token-exchange")
                        .contentType("application/json")
                        .content(exchangeBody(subject)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("expired subject token → 401")
    void expiredToken_returns401() throws Exception {
        Instant past = Instant.now().minus(2, ChronoUnit.HOURS);
        String subject = sign(Jwts.builder()
                .header().keyId(JWKS_KID).and()
                .subject(MAPPED_OP_OIDC)
                .issuer(OIDC_ISSUER)
                .audience().add(CONSOLE_CLIENT).and()
                .issuedAt(Date.from(past))
                .expiration(Date.from(past.plus(5, ChronoUnit.MINUTES))));
        mockMvc.perform(post("/api/admin/auth/token-exchange")
                        .contentType("application/json")
                        .content(exchangeBody(subject)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("subject token signed by an untrusted key → 401")
    void untrustedSignature_returns401() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair attacker = gen.generateKeyPair();
        String subject = oidcToken(MAPPED_OP_OIDC)
                .signWith(attacker.getPrivate(), Jwts.SIG.RS256).compact();
        mockMvc.perform(post("/api/admin/auth/token-exchange")
                        .contentType("application/json")
                        .content(exchangeBody(subject)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("wrong RFC 8693 grant_type → 400 BAD_REQUEST")
    void wrongGrantType_returns400() throws Exception {
        String subject = sign(oidcToken(MAPPED_OP_OIDC));
        String body = """
                {"grant_type":"authorization_code",
                 "subject_token":"%s",
                 "subject_token_type":"urn:ietf:params:oauth:token-type:access_token"}
                """.formatted(subject);
        mockMvc.perform(post("/api/admin/auth/token-exchange")
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("missing subject_token → 400 VALIDATION_ERROR")
    void missingSubjectToken_returns400() throws Exception {
        String body = """
                {"grant_type":"urn:ietf:params:oauth:grant-type:token-exchange",
                 "subject_token_type":"urn:ietf:params:oauth:token-type:access_token"}
                """;
        mockMvc.perform(post("/api/admin/auth/token-exchange")
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // --- Regression: ADR-014 D1 Option A stays rejected -------------------

    @Test
    @DisplayName("REGRESSION: raw GAP OIDC token on a normal /api/admin/** endpoint still 401s (Option A rejected)")
    void rawOidcTokenOnProtectedEndpoint_still401() throws Exception {
        // A raw GAP OIDC token is NOT an operator token; OperatorAuthentication
        // Filter must still reject it on a normal protected endpoint — the
        // exchange is the ONLY way an OIDC identity reaches /api/admin/**.
        String rawOidc = sign(oidcToken(MAPPED_OP_OIDC));
        mockMvc.perform(get("/api/admin/me")
                        .header("Authorization", "Bearer " + rawOidc))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("REGRESSION: existing operator JWT path (/api/admin/me) still works unchanged")
    void existingOperatorTokenPath_unchanged() throws Exception {
        // The password+TOTP login mint is unchanged: an OperatorJwtTestFixture
        // token (canonical login-shape operator JWT) still authenticates a
        // normal /api/admin/** endpoint. (Token-exchange refactored
        // AdminLoginService.mintAccessToken to delegate to the shared issuer;
        // this pins the login-shape contract did not drift.)
        String operatorToken = jwt.operatorToken(MAPPED_OP_UUID);
        mockMvc.perform(get("/api/admin/me")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operatorId").value(MAPPED_OP_UUID));
    }

    @Test
    @DisplayName("token carrying a token_type claim (admin-minted) presented as subject token → 401")
    void operatorTokenAsSubjectToken_rejected() throws Exception {
        // Smuggling an admin-service operator token through the exchange must
        // be rejected (security.md validation #5 — token_type-absent guard).
        String operatorToken = jwt.operatorToken(MAPPED_OP_UUID);
        mockMvc.perform(post("/api/admin/auth/token-exchange")
                        .contentType("application/json")
                        .content(exchangeBody(operatorToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }
}
