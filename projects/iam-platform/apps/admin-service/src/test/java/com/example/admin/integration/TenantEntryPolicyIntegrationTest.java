package com.example.admin.integration;

import com.example.admin.support.OperatorJwtTestFixture;
import com.example.testsupport.integration.AbstractIntegrationTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
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
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-771 S5 — {@code GET|PUT /api/admin/tenants/{tenantId}/entry-policy} against real MySQL (V0047
 * table, V0048 seed) + a WireMock account-service / IAM JWKS, and the AC-4 transition through the REAL
 * write path: the row the API writes is the row the token exchange reads.
 *
 * <ul>
 *   <li>V0048: {@code TENANT_ADMIN} and {@code SUPER_ADMIN} hold {@code tenant.security.manage};</li>
 *   <li>a {@code TENANT_ADMIN} of tenant-x turns its own policy on → 200 · row · audit row
 *       ({@code TENANT_ENTRY_POLICY_SET}, «requireMfa none→true»);</li>
 *   <li>the same admin on tenant-y → 403 {@code TENANT_SCOPE_DENIED}, no row;</li>
 *   <li>{@code '*'} → 400 {@code VALIDATION_ERROR}; unknown tenant → 404, no row;</li>
 *   <li>AC-4: policy ON → an operator of tenant-x exchanging with {@code amr=[pwd]} → 403
 *       {@code MFA_REQUIRED}; with {@code [pwd,otp,mfa]} → 200; policy OFF → {@code [pwd]} → 200.</li>
 * </ul>
 *
 * <p>Skipped when Docker is unavailable (AbstractIntegrationTest) — CI Linux is authoritative.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
class TenantEntryPolicyIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static final String OIDC_ISSUER = "http://localhost:8081";
    private static final String CONSOLE_CLIENT = "platform-console-web";
    private static final String JWKS_KID = "auth-test-kid";

    static WireMockServer wireMock;
    static KeyPair authKeyPair;
    static OperatorJwtTestFixture jwt;
    static String adminSigningKeyPem;

    private static final String TENANT_X = "s5-tenant-x";
    private static final String TENANT_Y = "s5-tenant-y";
    private static final String ADMIN_X_UUID = "00000000-0000-7000-8000-0000000c5a01";
    private static final String MEMBER_X_UUID = "00000000-0000-7000-8000-0000000c5a02";
    private static final String MEMBER_X_OIDC = "oidc-sub-s5-member-x";

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

    private static String b64Url(byte[] bytes) {
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] s = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, s, 0, s.length);
            bytes = s;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String jwksJson(RSAPublicKey key) {
        return "{\"keys\":[{\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"RS256\",\"kid\":\"" + JWKS_KID
                + "\",\"n\":\"" + b64Url(key.getModulus().toByteArray())
                + "\",\"e\":\"" + b64Url(key.getPublicExponent().toByteArray()) + "\"}]}";
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("admin.jwt.active-signing-kid", () -> "test-key-001");
        registry.add("admin.jwt.signing-keys.test-key-001", () -> adminSigningKeyPem);
        registry.add("admin.jwt.issuer", () -> "admin-service");
        registry.add("admin.jwt.expected-token-type", () -> "admin");
        registry.add("admin.oidc.jwks-uri", () -> wireMock.baseUrl() + "/internal/auth/jwks");
        registry.add("admin.oidc.issuer", () -> OIDC_ISSUER);
        registry.add("admin.oidc.audience", () -> CONSOLE_CLIENT);
        registry.add("admin.account-service.base-url", wireMock::baseUrl);
        registry.add("admin.auth-service.base-url", wireMock::baseUrl);
        registry.add("admin.security-service.base-url", wireMock::baseUrl);
        registry.add("iam.internal-client.token-uri", () -> wireMock.baseUrl() + "/oauth2/token");
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        wireMock.resetAll();
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/auth/jwks"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(jwksJson((RSAPublicKey) authKeyPair.getPublic()))));
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/oauth2/token"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"test-jwt\",\"expires_in\":300,\"token_type\":\"Bearer\"}")));
        stubTenant(TENANT_X);
        stubTenant(TENANT_Y);
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/tenants/s5-ghost"))
                .willReturn(aResponse().withStatus(404).withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":\"TENANT_NOT_FOUND\",\"message\":\"no\"}")));

        seedOperator(ADMIN_X_UUID, TENANT_X, null);
        seedOperator(MEMBER_X_UUID, TENANT_X, MEMBER_X_OIDC);
        jdbcTemplate.update("""
                INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
                SELECT o.id, r.id, ?, NOW(6), NULL
                  FROM admin_operators o JOIN admin_roles r ON r.name = 'TENANT_ADMIN'
                 WHERE o.operator_id = ?
                """, TENANT_X, ADMIN_X_UUID);
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM tenant_entry_policy WHERE tenant_id LIKE 's5-%'");
    }

    private void stubTenant(String tenantId) {
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/tenants/" + tenantId))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"tenantId":"%s","displayName":"S5","tenantType":"B2B_ENTERPRISE","status":"ACTIVE",
                                 "createdAt":"2026-10-09T00:00:00Z","updatedAt":"2026-10-09T00:00:00Z"}
                                """.formatted(tenantId))));
    }

    private void seedOperator(String operatorId, String tenantId, String oidcSubject) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_operators WHERE operator_id = ?", Integer.class, operatorId);
        if (existing == null || existing == 0) {
            jdbcTemplate.update("""
                    INSERT INTO admin_operators
                      (operator_id, tenant_id, email, password_hash, display_name,
                       status, oidc_subject, created_at, updated_at, version)
                    VALUES (?, ?, ?, NULL, ?, 'ACTIVE', ?, NOW(6), NOW(6), 0)
                    """, operatorId, tenantId, operatorId + "@example.com", "S5 op", oidcSubject);
        }
    }

    private String adminX() {
        return "Bearer " + jwt.operatorToken(ADMIN_X_UUID);
    }

    private org.springframework.test.web.servlet.ResultActions putPolicy(String tenantId, String body) throws Exception {
        return mockMvc.perform(put("/api/admin/tenants/" + tenantId + "/entry-policy")
                .header("Authorization", adminX())
                .header("X-Operator-Reason", "s5-it")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions exchangeWith(String... amr) throws Exception {
        Instant now = Instant.now();
        String subject = Jwts.builder()
                .header().keyId(JWKS_KID).and()
                .subject(MEMBER_X_OIDC)
                .issuer(OIDC_ISSUER)
                .audience().add(CONSOLE_CLIENT).and()
                .claim("amr", List.of(amr))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(15, ChronoUnit.MINUTES)))
                .signWith(authKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
        return mockMvc.perform(post("/api/admin/auth/token-exchange")
                .contentType("application/json")
                .content("""
                        {"grant_type":"urn:ietf:params:oauth:grant-type:token-exchange",
                         "subject_token":"%s",
                         "subject_token_type":"urn:ietf:params:oauth:token-type:access_token"}
                        """.formatted(subject)));
    }

    private Integer policyRows(String tenantId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tenant_entry_policy WHERE tenant_id = ?", Integer.class, tenantId);
    }

    @Test
    @DisplayName("V0048: tenant.security.manage is seeded onto exactly SUPER_ADMIN and TENANT_ADMIN")
    void seed_holders() {
        List<String> holders = jdbcTemplate.queryForList("""
                SELECT r.name FROM admin_role_permissions p JOIN admin_roles r ON r.id = p.role_id
                 WHERE p.permission_key = 'tenant.security.manage' ORDER BY r.name
                """, String.class);
        assertThat(holders).containsExactly("SUPER_ADMIN", "TENANT_ADMIN");
    }

    @Test
    @DisplayName("TENANT_ADMIN on its own tenant: GET off → PUT on (row + audit «none→true») → GET on")
    void ownTenant_putOn_writesRowAndAudit() throws Exception {
        mockMvc.perform(get("/api/admin/tenants/" + TENANT_X + "/entry-policy").header("Authorization", adminX()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requireMfa").value(false))
                .andExpect(jsonPath("$.updatedAt").isEmpty());

        putPolicy(TENANT_X, "{\"requireMfa\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requireMfa").value(true))
                .andExpect(jsonPath("$.updatedBy").value(ADMIN_X_UUID));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT require_mfa FROM tenant_entry_policy WHERE tenant_id = ?", Boolean.class, TENANT_X)).isTrue();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT downstream_detail FROM admin_actions
                 WHERE actor_id = ? AND action_code = 'TENANT_ENTRY_POLICY_SET' AND outcome = 'SUCCESS'
                   AND target_type = 'TENANT' AND target_id = ? AND permission_used = 'tenant.security.manage'
                 ORDER BY started_at DESC LIMIT 1
                """, String.class, ADMIN_X_UUID, TENANT_X)).isEqualTo("requireMfa none→true");

        mockMvc.perform(get("/api/admin/tenants/" + TENANT_X + "/entry-policy").header("Authorization", adminX()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requireMfa").value(true));
    }

    @Test
    @DisplayName("🔴 TENANT_ADMIN of tenant-x on tenant-y → 403 TENANT_SCOPE_DENIED, no row")
    void otherTenant_403() throws Exception {
        putPolicy(TENANT_Y, "{\"requireMfa\":true}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));
        assertThat(policyRows(TENANT_Y)).isZero();
    }

    @Test
    @DisplayName("'*' → 400 VALIDATION_ERROR, no row")
    void invalidTargets() throws Exception {
        putPolicy("*", "{\"requireMfa\":true}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(policyRows("*")).isZero();
    }

    @Test
    @DisplayName("unknown tenant (account-service 404) → 404 TENANT_NOT_FOUND, no orphan row — needs a SUPER_ADMIN-reach actor")
    void unknownTenant_404() throws Exception {
        // tenant-x's admin is out of scope for s5-ghost (403 would win); widen its grant for this case only.
        jdbcTemplate.update("""
                INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
                SELECT o.id, r.id, 's5-ghost', NOW(6), NULL
                  FROM admin_operators o JOIN admin_roles r ON r.name = 'TENANT_ADMIN'
                 WHERE o.operator_id = ?
                """, ADMIN_X_UUID);
        try {
            putPolicy("s5-ghost", "{\"requireMfa\":true}")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"));
            assertThat(policyRows("s5-ghost")).isZero();
        } finally {
            jdbcTemplate.update("""
                    DELETE b FROM admin_operator_roles b JOIN admin_operators o ON o.id = b.operator_id
                     WHERE o.operator_id = ? AND b.tenant_id = 's5-ghost'
                    """, ADMIN_X_UUID);
        }
    }

    @Test
    @DisplayName("enrolment-summary: real roster query (home ∪ assignment, ACTIVE) × auth-service answer → counts")
    void enrolmentSummary_countsTheRoster() throws Exception {
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/internal/auth/second-factor/enrolment-status"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"enrolledAccountIds\":[]}")));

        // tenant-x roster: ADMIN_X (no account link) + MEMBER_X (linked, not enrolled).
        mockMvc.perform(get("/api/admin/tenants/" + TENANT_X + "/entry-policy/enrolment-summary")
                        .header("Authorization", adminX()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operators").value(2))
                .andExpect(jsonPath("$.enrolled").value(0))
                .andExpect(jsonPath("$.notEnrolled").value(1))
                .andExpect(jsonPath("$.unlinked").value(1));
        wireMock.verify(WireMock.postRequestedFor(urlPathEqualTo("/internal/auth/second-factor/enrolment-status"))
                .withRequestBody(WireMock.equalToJson("{\"accountIds\":[\"" + MEMBER_X_OIDC + "\"]}")));
    }

    @Test
    @DisplayName("enrolment-summary: auth-service 500 → 503, not a number")
    void enrolmentSummary_authDown_503() throws Exception {
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/internal/auth/second-factor/enrolment-status"))
                .willReturn(aResponse().withStatus(500)));

        mockMvc.perform(get("/api/admin/tenants/" + TENANT_X + "/entry-policy/enrolment-summary")
                        .header("Authorization", adminX()))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("🔴 AC-4: PUT ON → member exchange amr=[pwd] 403 MFA_REQUIRED · [pwd,otp,mfa] 200 · PUT OFF → [pwd] 200")
    void ac4_transitionThroughTheRealWritePath() throws Exception {
        exchangeWith("pwd").andExpect(status().isOk()); // control: nothing on yet

        putPolicy(TENANT_X, "{\"requireMfa\":true}").andExpect(status().isOk());
        exchangeWith("pwd")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MFA_REQUIRED"));
        exchangeWith("pwd", "otp", "mfa")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        putPolicy(TENANT_X, "{\"requireMfa\":false}").andExpect(status().isOk());
        exchangeWith("pwd").andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT require_mfa FROM tenant_entry_policy WHERE tenant_id = ?", Boolean.class, TENANT_X)).isFalse();
    }
}
