package com.example.admin.integration;

import com.example.admin.support.OperatorJwtTestFixture;
import com.example.testsupport.integration.AbstractIntegrationTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-474 / ADR-MONO-044 — end-to-end Testcontainers IT for self-service tenant
 * onboarding ({@code POST /api/admin/onboarding/organizations}).
 *
 * <p>Boots admin-service against real MySQL + Kafka (AbstractIntegrationTest) + Redis,
 * with WireMock serving the auth-service JWKS (for the caller's user OIDC token) and
 * stubbing account-service (tenant create, account detail, identity resolve-or-create,
 * GAP client-credentials token). It mints a {@code platform-console-web} user OIDC token
 * — NOT an operator token — and proves the whole security-critical wiring end-to-end:
 * <ul>
 *   <li>valid user token → 201; the new tenant's first admin is minted with BOTH
 *       {@code TENANT_ADMIN} + {@code TENANT_BILLING_ADMIN} (D6);</li>
 *   <li><b>D2 confinement at the persistence layer</b>: every {@code admin_operator_roles}
 *       row for the new operator carries {@code tenant_id = <new tenant>} — never {@code '*'},
 *       and the whole-tenant assignment targets the new tenant;</li>
 *   <li>an operator token presented as the subject token → 401 (rejects operator tokens —
 *       only an ordinary user OIDC token may self-onboard).</li>
 * </ul>
 *
 * <p>Skipped automatically when Docker is unavailable (AbstractIntegrationTest
 * DockerAvailableCondition) — expected on the local Windows host; CI Linux is authoritative.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class SelfServiceOnboardingIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static final String OIDC_ISSUER = "http://localhost:8081";
    private static final String CONSOLE_CLIENT = "platform-console-web";
    private static final String JWKS_KID = "auth-test-kid";

    private static final String OWNER_ACCOUNT_ID = "acc-owner-0001";
    private static final String OWNER_EMAIL = "owner@newco.com";
    private static final String NEW_TENANT = "newco-inc";

    /** TASK-MONO-773 S5 — a pool account with no operator row (the «측면 없음» row of auth-api.md's console table). */
    private static final String SHELL_ACCOUNT_ID = "0199de70-0000-7000-8000-000000a10773";
    private static final String SHELL_EMAIL = "shell-owner-773@example.com";
    private static final String SHELL_TENANT = "shellco-773";

    static WireMockServer wireMock;
    static KeyPair authKeyPair;
    static OperatorJwtTestFixture jwt;
    static String adminSigningKeyPem;

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

        // auth-service JWKS for the caller's user OIDC token
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/auth/jwks"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jwksJson((RSAPublicKey) authKeyPair.getPublic()))));

        // GAP client-credentials token endpoint (the account clients fetch a Bearer)
        wireMock.stubFor(WireMock.post(urlEqualTo("/oauth2/token"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"stub-cc-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));

        // account-service: caller's authoritative account (email + display name resolved from here)
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/accounts/" + OWNER_ACCOUNT_ID))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"" + OWNER_ACCOUNT_ID + "\",\"email\":\"" + OWNER_EMAIL
                                + "\",\"status\":\"ACTIVE\",\"createdAt\":\"2026-07-04T00:00:00Z\","
                                + "\"profile\":{\"displayName\":\"NewCo Owner\",\"phoneMasked\":null}}")));

        // TASK-MONO-773 S3 (onboarding-api.md «트러스트 게이트») — the owner's email is verified: account-service's
        // verified-email:match answers 200 + emailVerifiedAt. Without it the gate refuses (an unmatched WireMock
        // request is a 404 = NOT_ELIGIBLE) — the refusals themselves are SelfServiceOnboardingTrustGateIntegrationTest.
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/internal/accounts/" + OWNER_ACCOUNT_ID + "/verified-email:match"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accountId\":\"" + OWNER_ACCOUNT_ID
                                + "\",\"emailVerifiedAt\":\"2026-10-15T00:00:00Z\"}")));

        // account-service: tenant create → 201 (TenantResponse shape)
        wireMock.stubFor(WireMock.post(urlEqualTo("/internal/tenants"))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"tenantId\":\"" + NEW_TENANT + "\",\"displayName\":\"NewCo Inc\","
                                + "\"tenantType\":\"B2B_ENTERPRISE\",\"status\":\"ACTIVE\","
                                + "\"createdAt\":\"2026-07-04T00:00:00Z\",\"updatedAt\":\"2026-07-04T00:00:00Z\"}")));

        // ── TASK-MONO-773 S5 (AC-1 control) — a pool account with NO operator row, holding the console shell token ──
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/accounts/" + SHELL_ACCOUNT_ID))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"" + SHELL_ACCOUNT_ID + "\",\"email\":\"" + SHELL_EMAIL
                                + "\",\"status\":\"ACTIVE\",\"createdAt\":\"2026-10-15T00:00:00Z\","
                                + "\"profile\":{\"displayName\":\"Shell Owner\",\"phoneMasked\":null}}")));
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/internal/accounts/" + SHELL_ACCOUNT_ID + "/verified-email:match"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accountId\":\"" + SHELL_ACCOUNT_ID
                                + "\",\"emailVerifiedAt\":\"2026-10-15T00:00:00Z\"}")));
        wireMock.stubFor(WireMock.post(urlEqualTo("/internal/tenants"))
                .atPriority(1)
                .withRequestBody(WireMock.containing(SHELL_TENANT))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"tenantId\":\"" + SHELL_TENANT + "\",\"displayName\":\"Shell Co\","
                                + "\"tenantType\":\"B2B_ENTERPRISE\",\"status\":\"ACTIVE\","
                                + "\"createdAt\":\"2026-10-15T00:00:00Z\",\"updatedAt\":\"2026-10-15T00:00:00Z\"}")));

        // account-service: born-unified identity resolve-or-create
        wireMock.stubFor(WireMock.post(urlPathMatching("/internal/tenants/[^/]+/identities:resolveOrCreate"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"identityId\":\"identity-newco-1\",\"outcome\":\"CREATED\"}")));
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
        // user OIDC subject-token validation → WireMock JWKS
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

    private String userOidcToken(String sub) {
        Instant now = Instant.now();
        JwtBuilder b = Jwts.builder()
                .header().keyId(JWKS_KID).and()
                .subject(sub)
                .issuer(OIDC_ISSUER)
                .audience().add(CONSOLE_CLIENT).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(15, ChronoUnit.MINUTES)));
        return b.signWith(authKeyPair.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private String onboardBody(String subjectToken, String tenantId) {
        return "{\"subjectToken\":\"" + subjectToken + "\",\"tenantId\":\"" + tenantId
                + "\",\"organizationName\":\"NewCo Inc\"}";
    }

    @Test
    @DisplayName("valid user OIDC token → 201; first admin minted with TENANT_ADMIN + TENANT_BILLING_ADMIN, "
            + "and every role row + assignment is confined to the NEW tenant (D2/D6, persistence-level)")
    void onboards_confinedToNewTenant() throws Exception {
        String token = userOidcToken(OWNER_ACCOUNT_ID);

        mockMvc.perform(post("/api/admin/onboarding/organizations")
                        .contentType("application/json")
                        .content(onboardBody(token, NEW_TENANT)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(NEW_TENANT))
                .andExpect(jsonPath("$.operatorId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.roles.length()").value(2));

        // The minted operator's home tenant = the new tenant.
        Long operatorInternalId = jdbcTemplate.queryForObject(
                "SELECT id FROM admin_operators WHERE tenant_id = ? AND email = ?",
                Long.class, NEW_TENANT, OWNER_EMAIL);
        assertThat(operatorInternalId).isNotNull();

        // D2 CONFINEMENT (persistence): BOTH role rows carry tenant_id = the new tenant, never '*'.
        List<Map<String, Object>> roleRows = jdbcTemplate.queryForList(
                "SELECT ar.name AS role_name, r.tenant_id AS tid "
                        + "FROM admin_operator_roles r JOIN admin_roles ar ON ar.id = r.role_id "
                        + "WHERE r.operator_id = ?", operatorInternalId);
        assertThat(roleRows).hasSize(2);
        assertThat(roleRows).allSatisfy(row -> {
            assertThat(row.get("tid")).isEqualTo(NEW_TENANT);
            assertThat(row.get("tid")).isNotEqualTo("*");
        });
        assertThat(roleRows).extracting(row -> row.get("role_name"))
                .containsExactlyInAnyOrder("TENANT_ADMIN", "TENANT_BILLING_ADMIN");

        // Whole-tenant assignment targets the new tenant.
        String assignmentTenant = jdbcTemplate.queryForObject(
                "SELECT tenant_id FROM operator_tenant_assignment WHERE operator_id = ?",
                String.class, operatorInternalId);
        assertThat(assignmentTenant).isEqualTo(NEW_TENANT);

        // fix-001: the operator's oidc_subject = the caller's account_id (the OIDC sub) —
        // what makes the owner reachable via token-exchange (the gap live verification caught).
        String oidcSubject = jdbcTemplate.queryForObject(
                "SELECT oidc_subject FROM admin_operators WHERE id = ?", String.class, operatorInternalId);
        assertThat(oidcSubject).isEqualTo(OWNER_ACCOUNT_ID);

        // fix-001 END-TO-END: the owner can now token-exchange their SAME user OIDC token into an
        // operator token for the new tenant — i.e. actually log into the console as this operator.
        String exchangeBody = "{\"grant_type\":\"urn:ietf:params:oauth:grant-type:token-exchange\","
                + "\"subject_token\":\"" + token + "\","
                + "\"subject_token_type\":\"urn:ietf:params:oauth:token-type:access_token\"}";
        mockMvc.perform(post("/api/admin/auth/token-exchange")
                        .contentType("application/json")
                        .content(exchangeBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("admin"));

        // D6: the tenant is born entitlement-empty — no domain subscriptions were created here
        // (the grant is a capability to subscribe, not a subscription). Nothing to assert in
        // admin-service DB (subscriptions live in account-service); the absence of any
        // subscription call is the contract.
    }

    @Test
    @DisplayName("an operator token presented as the subject token → 401 (only an ordinary user OIDC token may self-onboard)")
    void operatorTokenRejected() throws Exception {
        // An admin-minted operator token carries token_type=admin; the OIDC validator
        // rejects any token with a token_type claim (fail-closed).
        String operatorToken = jwt.operatorToken("00000000-0000-7000-8000-0000000be474");

        mockMvc.perform(post("/api/admin/onboarding/organizations")
                        .contentType("application/json")
                        .content(onboardBody(operatorToken, "other-tenant")))
                .andExpect(status().isUnauthorized());
    }

    /**
     * TASK-MONO-773 S5 — the console «non-operator shell» token, byte-for-byte the claim set auth-service now mints
     * for a pool principal with no operator facet (auth-api.md § 풀 계정의 콘솔 토큰, «측면 없음» row; the auth half —
     * that the real issuer mints exactly these claims — is auth-service's
     * {@code ConsumerPoolSsoIntegrationTest#poolSession_onConsole_shellToken}): {@code iss} = IAM ·
     * {@code aud} = the console client · {@code sub} = the pool account · {@code tenant_id=iam} ·
     * {@code tenant_type=B2B_ENTERPRISE} · no {@code roles} · no {@code token_type}.
     *
     * <p>🔴 AC-1 control (auth-api.md «이 토큰은 운영자 토큰이 아니다»): ① as a Bearer on an admin API → 401
     * TOKEN_INVALID ② as the token-exchange subject while no operator row exists → 401 TOKEN_INVALID ③ on the
     * onboarding endpoints as {@code subjectToken} → admitted, the endpoint's own judgement decides
     * (received-invitations 200 VERIFIED · organizations 201). ④ Control for ②: after ③ created the operator row, the
     * SAME token exchanges (200) — so ②'s 401 was «not an operator», not «a bad token».
     */
    @Test
    @DisplayName("🔴 773 S5 AC-1 대조군: 콘솔 셸 토큰 → 관리 API Bearer 401 · 토큰 교환 401 · 온보딩 두 엔드포인트는 통과(200 · 201) · 생성 뒤 같은 토큰 교환 200")
    void ac1_consoleShellToken_isNotAnOperatorToken() throws Exception {
        Instant now = Instant.now();
        String shell = Jwts.builder()
                .header().keyId(JWKS_KID).and()
                .subject(SHELL_ACCOUNT_ID)
                .issuer(OIDC_ISSUER)
                .audience().add(CONSOLE_CLIENT).and()
                .claim("tenant_id", "iam")
                .claim("tenant_type", "B2B_ENTERPRISE")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(15, ChronoUnit.MINUTES)))
                .signWith(authKeyPair.getPrivate(), Jwts.SIG.RS256).compact();

        // ① an admin API with the shell token as Bearer.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/admin/operators")
                        .header("Authorization", "Bearer " + shell))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));

        // ② the operator token exchange — no operator row for this sub.
        String exchangeBody = "{\"grant_type\":\"urn:ietf:params:oauth:grant-type:token-exchange\","
                + "\"subject_token\":\"" + shell + "\","
                + "\"subject_token_type\":\"urn:ietf:params:oauth:token-type:access_token\"}";
        mockMvc.perform(post("/api/admin/auth/token-exchange")
                        .contentType("application/json")
                        .content(exchangeBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));

        // ③ the onboarding endpoints take it as subjectToken; their own judgement decides.
        mockMvc.perform(post("/api/admin/onboarding/received-invitations")
                        .contentType("application/json")
                        .content("{\"subjectToken\":\"" + shell + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountState").value("VERIFIED"));
        mockMvc.perform(post("/api/admin/onboarding/organizations")
                        .contentType("application/json")
                        .content("{\"subjectToken\":\"" + shell + "\",\"tenantId\":\"" + SHELL_TENANT
                                + "\",\"organizationName\":\"Shell Co\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(SHELL_TENANT));

        // ④ control for ②: the same token now exchanges — the operator row exists.
        mockMvc.perform(post("/api/admin/auth/token-exchange")
                        .contentType("application/json")
                        .content(exchangeBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("admin"));
    }
}
