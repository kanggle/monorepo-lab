package com.example.admin.integration;

import com.example.admin.support.OperatorJwtTestFixture;
import com.example.testsupport.integration.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterAll;
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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-773 S3 — the self-service path of «테넌트 생성» against real MySQL, with WireMock as account-service
 * (account detail · 🔴 {@code verified-email:match} · tenant create / suspend · tenant reads · entitled domains · the
 * invitation mail) and as auth-service's JWKS (the caller's console OIDC token). Contract:
 * onboarding-api.md (트러스트 게이트 · 신원당 상한 · 회사 생성자의 업무 권한 세트 · received-invitations) ·
 * admin-api.md § 출처와 회수 규칙 · data-model.md § operator_domain_set_grant ({@code ORIGIN} CHECK).
 *
 * <ul>
 *   <li>🔴 <b>트러스트 게이트</b> — an unverified email → {@code 403 EMAIL_NOT_VERIFIED} and NOTHING exists afterwards
 *       (no tenant create call, no operator row, no role · assignment · set row); the same account once verified →
 *       {@code 201} (the control). Owner decision 2026-10-15 UTC (option A): an {@code iam}-credential operator
 *       (account-service has no account row — 404 — AND an operator facet exists) is exempt from the email check
 *       → {@code 201} on the existing-facet path, still bound by the cap; 404 with no facet → {@code 403
 *       ONBOARDING_ACCOUNT_NOT_ELIGIBLE}; a site account (account row, match 404) → {@code 403} with the «개인 계정»
 *       copy. No verdict (5xx) → {@code 503}, nothing created.</li>
 *   <li>🔴 <b>AC-2</b> — a new creator: tenant + {@code TENANT_ADMIN} + {@code TENANT_BILLING_ADMIN} + assignment +
 *       EXACTLY ONE {@code ORIGIN} row ({@code all-domains-full}, the new tenant, {@code granted_by NULL}); no set row in
 *       any other tenant; the admin half of the assume read ({@code domainRoles}) = the {@code all-domains-full}
 *       catalog (auth's {@code ∩ entitled} is P3's, not admin's).</li>
 *   <li>🔴 <b>AC-3</b> — a second company by an existing ACTIVE company operator: rows in the new tenant only; the
 *       existing company's role rows · assignments · SET rows and the operator row byte-unchanged; no copy of the
 *       existing company's set into the new one.</li>
 *   <li>🔴 <b>상한 3 · 상태 무관</b> — owning two (one of them SUSPENDED) → the third is created; owning three → the
 *       fourth is refused before any tenant is created. (The race is {@link SelfServiceOnboardingCapRaceIntegrationTest}.)</li>
 *   <li>🔴 <b>{@code ORIGIN}</b> — the CHECK admits it on a company tenant and refuses it on {@code '*'}; the API never
 *       writes it; the personal revoke and «이 회사에서 내보내기» delete it.</li>
 *   <li>🔴 <b>received-invitations</b> — only live PENDING invitations to the caller's VERIFIED email; unverified /
 *       ineligible → an empty list with the state; no verdict → {@code 503}.</li>
 * </ul>
 *
 * <p>Every tenant / operator / account id carries a per-test suffix — the IT suite shares one MySQL container.
 * Skipped when Docker is unavailable (AbstractIntegrationTest) — CI Linux is authoritative.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
@org.springframework.test.annotation.DirtiesContext(
        classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class SelfServiceOnboardingTrustGateIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static final String OIDC_ISSUER = "http://localhost:8081";
    private static final String CONSOLE_CLIENT = "platform-console-web";
    private static final String JWKS_KID = "auth-test-kid-773s3";
    private static final String MAIL_PATH = "/internal/notifications/operator-invitation";
    private static final String ALL_DOMAINS_FULL = "all-domains-full";
    private static final ObjectMapper JSON = new ObjectMapper();

    static WireMockServer wireMock;
    static OperatorJwtTestFixture jwt;
    static String adminSigningKeyPem;
    static KeyPair authKeyPair;

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
        // 🔴 deliberately NOT setting admin.onboarding.max-owned-tenants-per-operator: the cap test reads the DEFAULT.
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void stubs() {
        wireMock.resetAll();
        wireMock.stubFor(WireMock.post(urlEqualTo("/oauth2/token"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"test-jwt\",\"expires_in\":300,\"token_type\":\"Bearer\"}")));
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/auth/jwks"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(jwksJson((RSAPublicKey) authKeyPair.getPublic()))));
        wireMock.stubFor(WireMock.post(urlPathMatching("/internal/tenants/[^/]+/identities:resolveOrCreate"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"identityId\":\"identity-773s3\",\"outcome\":\"CREATED\"}")));
        // tenant create (any slug) → 201; the compensation PATCH (any slug) → 200
        wireMock.stubFor(WireMock.post(urlEqualTo("/internal/tenants"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody(tenantJson("s3-created", "ACTIVE"))));
        wireMock.stubFor(WireMock.patch(urlPathMatching("/internal/tenants/[^/]+"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(tenantJson("s3-patched", "SUSPENDED"))));
        wireMock.stubFor(WireMock.post(urlPathEqualTo(MAIL_PATH)).willReturn(aResponse().withStatus(204)));
        // every company of this class subscribes to wms only
        wireMock.stubFor(WireMock.get(urlPathMatching("/internal/tenants/[^/]+/entitled-domains"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"tenantId\":\"x\",\"domainKeys\":[\"wms\"]}")));
    }

    // ═══ 트러스트 게이트 ═════════════════════════════════════════════════════════

    @Test
    @DisplayName("🔴 게이트: 미인증 이메일 → 403 EMAIL_NOT_VERIFIED · 테넌트 생성 호출 0 · 운영자 · 역할 · 배정 · 세트 행 0 → 같은 계정 인증 뒤 같은 요청 201 (대조군)")
    void unverifiedEmail_403_nothingCreated_thenVerified_201() throws Exception {
        String sfx = sfx();
        String account = "acc-unv-" + sfx;
        String email = "unverified-" + sfx + "@example.com";
        String slug = "s3-unv-" + sfx;
        stubAccountDetail(account, email);
        String path = matchPath(account);
        wireMock.stubFor(WireMock.post(urlPathEqualTo(path)).inScenario("verify-" + sfx)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(403).withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":\"EMAIL_NOT_VERIFIED\",\"message\":\"x\"}")));
        wireMock.stubFor(WireMock.post(urlPathEqualTo(path)).inScenario("verify-" + sfx)
                .whenScenarioStateIs("verified")
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"accountId\":\"" + account + "\",\"emailVerifiedAt\":\"2026-10-15T00:00:00Z\"}")));

        onboard(account, slug)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        assertThat(tenantCreateCalls(slug)).as("no tenant was created").isZero();
        assertThat(operatorsWithSubject(account)).as("no operator row").isZero();
        assertThat(count("SELECT COUNT(*) FROM admin_operator_roles WHERE tenant_id = ?", slug)).isZero();
        assertThat(count("SELECT COUNT(*) FROM operator_tenant_assignment WHERE tenant_id = ?", slug)).isZero();
        assertThat(count("SELECT COUNT(*) FROM operator_domain_set_grant WHERE tenant_id = ?", slug)).isZero();
        // the gate asked account-service about THIS account with the authoritative email (never the body)
        wireMock.verify(postRequestedFor(urlPathEqualTo(path))
                .withRequestBody(matchingJsonPath("$.expectedEmail", equalTo(email))));

        // control: the same account verifies → the same request now creates
        wireMock.setScenarioState("verify-" + sfx, "verified");
        onboard(account, slug).andExpect(status().isCreated()).andExpect(jsonPath("$.tenantId").value(slug));
        assertThat(tenantCreateCalls(slug)).isEqualTo(1);
        assertThat(operatorsWithSubject(account)).isEqualTo(1);
    }

    @Test
    @DisplayName("🔴 게이트(option A): iam 자격 운영자(계정 읽기 404 ∧ ACTIVE 회사 측면) → 이메일 면제 → 201 · 매치 호출 0 · 새 테넌트에만 역할 둘 + 배정 + ORIGIN 한 행 · 기존 회사 행 · 운영자 행 바이트 불변")
    void iamCredentialOperator_exemptFromEmail_secondCompany_201() throws Exception {
        String sfx = sfx();
        // An `iam` credential has NO account-service account row (seeds: demo@demo.com ad03 · ad04 · ad05 · ad08) —
        // GET /internal/accounts/{sub} answers 404. Before S3 the controller read it FIRST and ended in 503.
        String iamAccount = "acc-iam-" + sfx;
        String iamEmail = "iam-op-" + sfx + "@example.com";
        String home = "s3-ih-" + sfx;
        long id = seedFacet(iamAccount, iamEmail, home, "ACTIVE");
        role(id, "SUPPORT_LOCK", home);
        directSet(id, home, "wms-inventory-read");
        stubAccountNotFound(iamAccount);
        String existingOperator = operatorUuid(id);
        String slug = "s3-iam-" + sfx;
        Map<String, Object> rowBefore = operatorRow(id);
        List<Map<String, Object>> rolesBefore = roleRowsOutside(id, slug);
        List<Map<String, Object>> assignmentsBefore = assignmentRowsOutside(id, slug);
        List<Map<String, Object>> setsBefore = setRowsOutside(id, slug);

        onboard(iamAccount, slug)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.operatorId").value(existingOperator));

        wireMock.verify(0, postRequestedFor(urlPathEqualTo(matchPath(iamAccount))));
        assertThat(tenantCreateCalls(slug)).isEqualTo(1);
        assertThat(operatorsWithSubject(iamAccount)).isEqualTo(1);
        assertThat(jdbc.queryForList("""
                SELECT r.name FROM admin_operator_roles x JOIN admin_roles r ON r.id = x.role_id
                 WHERE x.operator_id = ? AND x.tenant_id = ? ORDER BY r.name""", String.class, id, slug))
                .containsExactly("TENANT_ADMIN", "TENANT_BILLING_ADMIN");
        assertThat(count("SELECT COUNT(*) FROM operator_tenant_assignment WHERE operator_id = ? AND tenant_id = ?",
                id, slug)).isEqualTo(1);
        assertThat(jdbc.queryForList("""
                SELECT CONCAT(g.source_key, '/', s.system_key) FROM operator_domain_set_grant g
                  JOIN domain_permission_set s ON s.id = g.set_id
                 WHERE g.operator_id = ? AND g.tenant_id = ?""", String.class, id, slug))
                .containsExactly("ORIGIN/" + ALL_DOMAINS_FULL);
        assertThat(roleRowsOutside(id, slug)).isEqualTo(rolesBefore);
        assertThat(assignmentRowsOutside(id, slug)).isEqualTo(assignmentsBefore);
        assertThat(setRowsOutside(id, slug)).isEqualTo(setsBefore);
        assertThat(operatorRow(id)).isEqualTo(rowBefore);
    }

    @Test
    @DisplayName("🔴 게이트(option A): iam 자격 운영자도 상한 3 을 받는다 — 소유 3 → 422 · 테넌트 생성 0 · 매치 호출 0")
    void iamCredentialOperator_stillBoundByTheCap() throws Exception {
        String sfx = sfx();
        String iamAccount = "acc-iamc-" + sfx;
        long id = seedFacet(iamAccount, "iamc-" + sfx + "@example.com", "s3-ic1-" + sfx, "ACTIVE");
        ownTenant(id, "s3-ic2-" + sfx);
        ownTenant(id, "s3-ic3-" + sfx);
        stubAccountNotFound(iamAccount);
        String slug = "s3-ic4-" + sfx;

        onboard(iamAccount, slug)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("OPERATOR_OWNED_TENANT_LIMIT_EXCEEDED"));
        assertThat(tenantCreateCalls(slug)).isZero();
        assertThat(ownedTenants(id)).isEqualTo(3);
    }

    @Test
    @DisplayName("🔴 게이트(option A): 계정 읽기 404 ∧ 운영자 측면 없음 → 403 ONBOARDING_ACCOUNT_NOT_ELIGIBLE(fail-closed) · 테넌트 생성 0 · 운영자 행 0")
    void noAccountRecordAndNoFacet_403_notEligible() throws Exception {
        String sfx = sfx();
        String ghost = "acc-ghost-" + sfx;
        stubAccountNotFound(ghost);
        String slug = "s3-gh-" + sfx;

        onboard(ghost, slug)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ONBOARDING_ACCOUNT_NOT_ELIGIBLE"));
        assertThat(tenantCreateCalls(slug)).isZero();
        assertThat(operatorsWithSubject(ghost)).isZero();
    }

    @Test
    @DisplayName("🔴 게이트: 사이트 계정(계정 읽기 200 · 매치 404 · 측면 없음) → 403 ONBOARDING_ACCOUNT_NOT_ELIGIBLE + «개인 계정» 안내 · 이메일 경합도 같은 코드 · 테넌트 생성 0 · 운영자 행 0")
    void siteAccount_403_notEligible_withCopy() throws Exception {
        String sfx = sfx();
        String site = "acc-site-" + sfx;
        stubAccountDetail(site, "site-" + sfx + "@example.com");
        stubMatch(site, 404, "ACCOUNT_NOT_FOUND");
        String slug = "s3-site-" + sfx;

        onboard(site, slug)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ONBOARDING_ACCOUNT_NOT_ELIGIBLE"))
                // the refusal carries the copy «개인 계정으로 로그인해 만드세요» (same meaning — console § 2.6.4)
                .andExpect(jsonPath("$.message").value(containsString("개인 계정")));
        assertThat(tenantCreateCalls(slug)).isZero();
        assertThat(operatorsWithSubject(site)).isZero();

        // the authoritative email changed between the detail read and the match (account-service 403 MISMATCH)
        String raced = "acc-race-" + sfx;
        stubAccountDetail(raced, "raced-" + sfx + "@example.com");
        stubMatch(raced, 403, "ACCOUNT_EMAIL_MISMATCH");
        String slug2 = "s3-mis-" + sfx;
        onboard(raced, slug2)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ONBOARDING_ACCOUNT_NOT_ELIGIBLE"));
        assertThat(tenantCreateCalls(slug2)).isZero();
        assertThat(operatorsWithSubject(raced)).isZero();
    }

    @Test
    @DisplayName("🔴 게이트 fail-closed: 판정을 못 받음(account-service 5xx) → 503 · 테넌트 생성 0 · 운영자 행 0")
    void noVerdict_503_nothingCreated() throws Exception {
        String sfx = sfx();
        String account = "acc-down-" + sfx;
        stubAccountDetail(account, "down-" + sfx + "@example.com");
        wireMock.stubFor(WireMock.post(urlPathEqualTo(matchPath(account)))
                .willReturn(aResponse().withStatus(500).withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":\"INTERNAL_ERROR\",\"message\":\"x\"}")));
        String slug = "s3-down-" + sfx;

        onboard(account, slug).andExpect(status().isServiceUnavailable());
        assertThat(tenantCreateCalls(slug)).isZero();
        assertThat(operatorsWithSubject(account)).isZero();
    }

    // ═══ AC-2 · AC-3 — the creator's ORIGIN set ══════════════════════════════════

    @Test
    @DisplayName("🔴 AC-2: 비운영자 생성 → ACTIVE 테넌트 · TENANT_ADMIN + TENANT_BILLING_ADMIN · 배정 · ORIGIN all-domains-full 정확히 한 행(granted_by NULL) · 다른 테넌트 세트 행 0 · domainRoles = all-domains-full 카탈로그 · /me/domain-access 출처 ORIGIN")
    void ac2_newCreator_originRowInTheNewTenantOnly() throws Exception {
        String sfx = sfx();
        String account = "acc-new-" + sfx;
        String email = "creator-" + sfx + "@example.com";
        String slug = "s3-ac2-" + sfx;
        stubVerified(account, email);

        String operatorId = JSON.readTree(onboard(account, slug)
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status").value("ACTIVE"))
                        .andReturn().getResponse().getContentAsString())
                .get("operatorId").asText();
        long id = operatorInternalId(account);

        assertThat(jdbc.queryForList("""
                SELECT r.name FROM admin_operator_roles x JOIN admin_roles r ON r.id = x.role_id
                 WHERE x.operator_id = ? AND x.tenant_id = ? ORDER BY r.name""", String.class, id, slug))
                .containsExactly("TENANT_ADMIN", "TENANT_BILLING_ADMIN");
        assertThat(jdbc.queryForList("SELECT tenant_id FROM operator_tenant_assignment WHERE operator_id = ?",
                String.class, id)).containsExactly(slug);

        // 🔴 exactly ONE ORIGIN row: (creator, new tenant, all-domains-full, granted_by NULL, no group)
        List<Map<String, Object>> origin = originRows(id);
        assertThat(origin).as("the creator's ORIGIN set row").hasSize(1);
        assertThat(origin.get(0).get("tenant_id")).isEqualTo(slug);
        assertThat(origin.get(0).get("system_key")).isEqualTo(ALL_DOMAINS_FULL);
        assertThat(origin.get(0).get("granted_by")).isNull();
        assertThat(origin.get(0).get("group_origin")).isNull();
        // 🔴 no set row anywhere else (ADR-044 D2), and nothing but that one row at all
        assertThat(count("SELECT COUNT(*) FROM operator_domain_set_grant WHERE operator_id = ? AND tenant_id <> ?",
                id, slug)).isZero();
        assertThat(count("SELECT COUNT(*) FROM operator_domain_set_grant WHERE operator_id = ?", id)).isEqualTo(1);

        // the admin half of the assume read = the whole all-domains-full catalog (auth's ∩ entitled narrows it)
        assertThat(domainRoles(account, slug)).containsExactlyInAnyOrderElementsOf(allDomainsFullCatalog());

        // the source is visible to its holder
        mockMvc.perform(get("/api/admin/me/domain-access")
                        .header("Authorization", "Bearer " + jwt.operatorToken(operatorId))
                        .header("X-Tenant-Id", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sets.length()").value(1))
                .andExpect(jsonPath("$.sets[0].systemKey").value(ALL_DOMAINS_FULL))
                .andExpect(jsonPath("$.sets[0].sources[0].source").value("ORIGIN"))
                .andExpect(jsonPath("$.sets[0].sources[0].grantedBy").value(nullValue()));
    }

    @Test
    @DisplayName("🔴 AC-3: 기존 ACTIVE 회사 운영자의 두 번째 회사 → 새 테넌트에만 역할 둘 + 배정 + ORIGIN 한 행 · 기존 회사 역할 행 · 배정 · 세트 행 · 운영자 행 바이트 불변 · 기존 회사 세트는 새 회사로 옮겨지지 않음")
    void ac3_secondCompany_newTenantOnly_existingCompanyByteUnchanged() throws Exception {
        String sfx = sfx();
        String account = "acc-two-" + sfx;
        String email = "two-co-" + sfx + "@example.com";
        String home = "s3-home-" + sfx;
        long id = seedFacet(account, email, home, "ACTIVE");
        // the existing company also has a work role row and a DIRECT set row (the byte-unchanged control)
        role(id, "SUPPORT_LOCK", home);
        directSet(id, home, "wms-inventory-read");
        stubVerified(account, email);
        String existingOperator = jdbc.queryForObject("SELECT operator_id FROM admin_operators WHERE id = ?",
                String.class, id);
        String slug = "s3-ac3-" + sfx;

        Map<String, Object> rowBefore = operatorRow(id);
        List<Map<String, Object>> rolesBefore = roleRowsOutside(id, slug);
        List<Map<String, Object>> assignmentsBefore = assignmentRowsOutside(id, slug);
        List<Map<String, Object>> setsBefore = setRowsOutside(id, slug);
        assertThat(setsBefore).as("the existing DIRECT set row is there before").hasSize(1);
        List<String> homeRolesBefore = domainRoles(account, home);

        onboard(account, slug)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.operatorId").value(existingOperator));

        assertThat(operatorsWithSubject(account)).as("still ONE operator row").isEqualTo(1);
        assertThat(jdbc.queryForList("""
                SELECT r.name FROM admin_operator_roles x JOIN admin_roles r ON r.id = x.role_id
                 WHERE x.operator_id = ? AND x.tenant_id = ? ORDER BY r.name""", String.class, id, slug))
                .as("the new tenant: the two first-admin roles only — no SUPPORT_LOCK carried over")
                .containsExactly("TENANT_ADMIN", "TENANT_BILLING_ADMIN");
        assertThat(count("SELECT COUNT(*) FROM operator_tenant_assignment WHERE operator_id = ? AND tenant_id = ?",
                id, slug)).isEqualTo(1);
        // 🔴 the new tenant's set rows = exactly the ORIGIN row (the home company's DIRECT set is not copied)
        assertThat(jdbc.queryForList("""
                SELECT CONCAT(g.source_key, '/', s.system_key) FROM operator_domain_set_grant g
                  JOIN domain_permission_set s ON s.id = g.set_id
                 WHERE g.operator_id = ? AND g.tenant_id = ?""", String.class, id, slug))
                .containsExactly("ORIGIN/" + ALL_DOMAINS_FULL);
        // 🔴 the existing company — byte-unchanged
        assertThat(roleRowsOutside(id, slug)).isEqualTo(rolesBefore);
        assertThat(assignmentRowsOutside(id, slug)).isEqualTo(assignmentsBefore);
        assertThat(setRowsOutside(id, slug)).isEqualTo(setsBefore);
        assertThat(operatorRow(id)).isEqualTo(rowBefore);
        assertThat(domainRoles(account, home)).isEqualTo(homeRolesBefore);
    }

    // ═══ 신원당 상한 = 3 · 상태 무관 ══════════════════════════════════════════════

    @Test
    @DisplayName("🔴 상한 3(기본값) · 상태 무관: 소유 2(하나는 SUSPENDED 회사) → 세 번째 201 · 소유 3 → 네 번째 422 · 그 테넌트 생성 호출 0")
    void cap3_countsSuspendedTenant_thirdOk_fourthRefused() throws Exception {
        String sfx = sfx();
        String account = "acc-cap-" + sfx;
        String email = "cap-" + sfx + "@example.com";
        String home = "s3-caph-" + sfx;
        String suspendedCo = "s3-caps-" + sfx;
        long id = seedFacet(account, email, home, "ACTIVE");
        ownTenant(id, suspendedCo);
        // the owned company is SUSPENDED account-side — the count does not read tenant status (OD-3)
        stubTenant(suspendedCo, "SUSPENDED");
        stubVerified(account, email);
        assertThat(ownedTenants(id)).as("owns two before").isEqualTo(2);

        String third = "s3-cap3-" + sfx;
        onboard(account, third).andExpect(status().isCreated());
        assertThat(ownedTenants(id)).isEqualTo(3);

        String fourth = "s3-cap4-" + sfx;
        onboard(account, fourth)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("OPERATOR_OWNED_TENANT_LIMIT_EXCEEDED"));
        assertThat(tenantCreateCalls(fourth)).as("refused BEFORE any tenant is created").isZero();
        assertThat(ownedTenants(id)).isEqualTo(3);
    }

    // ═══ ORIGIN — schema · API · revoke · exit ══════════════════════════════════

    @Test
    @DisplayName("🔴 data-model: CHECK 가 ORIGIN 을 회사 테넌트에 받는다 · '*' 테넌트 ORIGIN 은 거절 · 모르는 출처는 계속 거절(대조군)")
    void check_admitsOriginOnACompanyTenant_refusesItOnStar() {
        String sfx = sfx();
        long id = seedFacet("acc-chk-" + sfx, "chk-" + sfx + "@example.com", "s3-chk-" + sfx, "ACTIVE");
        long allFull = presetInternalId(ALL_DOMAINS_FULL);

        assertThatCode(() -> jdbc.update("""
                INSERT INTO operator_domain_set_grant
                  (operator_id, tenant_id, set_id, source_key, group_origin, granted_by, granted_at)
                VALUES (?, ?, ?, 'ORIGIN', NULL, NULL, NOW(6))""", id, "s3-chk-" + sfx, allFull))
                .as("V0052's CHECK rejects ORIGIN — the S3 migration must admit it")
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO operator_domain_set_grant
                  (operator_id, tenant_id, set_id, source_key, group_origin, granted_by, granted_at)
                VALUES (?, '*', ?, 'ORIGIN', NULL, NULL, NOW(6))""", id, allFull))
                .as("ORIGIN never targets '*'")
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO operator_domain_set_grant
                  (operator_id, tenant_id, set_id, source_key, group_origin, granted_by, granted_at)
                VALUES (?, ?, ?, 'BOGUS', NULL, NULL, NOW(6))""", id, "s3-chk-" + sfx, allFull))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("🔴 ORIGIN 은 API 로 만들 수 없다: all-domains-full 직접 배정 → 422 DOMAIN_NOT_SUBSCRIBED · 본문 source=ORIGIN 은 무시되고 DIRECT 로 기록 · ORIGIN 행은 생성 때의 한 행 그대로")
    void origin_notGrantableThroughTheApi() throws Exception {
        Creator c = createCompany("api");
        assertThat(originRows(c.internalId())).as("precondition — the creator's ORIGIN row").hasSize(1);
        String allFull = presetSetId(ALL_DOMAINS_FULL);
        String wmsFull = presetSetId("wms-full");

        grant(c.operatorId(), c.tenantId(), c.operatorId(), "{\"setId\":\"" + allFull + "\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DOMAIN_NOT_SUBSCRIBED"));
        grant(c.operatorId(), c.tenantId(), c.operatorId(),
                "{\"setId\":\"" + wmsFull + "\",\"source\":\"ORIGIN\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("DIRECT"));

        assertThat(originRows(c.internalId())).hasSize(1);
        assertThat(originRows(c.internalId()).get(0).get("system_key")).isEqualTo(ALL_DOMAINS_FULL);
        assertThat(jdbc.queryForList("""
                SELECT g.source_key FROM operator_domain_set_grant g JOIN domain_permission_set s ON s.id = g.set_id
                 WHERE g.operator_id = ? AND s.system_key = 'wms-full'""", String.class, c.internalId()))
                .containsExactly("DIRECT");
    }

    @Test
    @DisplayName("🔴 개인 회수가 ORIGIN 을 지운다(자기 자신 포함) → 204 · ORIGIN 0 · 감사 removedSources=ORIGIN · 두 번째 회수 404")
    void personalRevoke_deletesOrigin() throws Exception {
        Creator c = createCompany("rvk");
        assertThat(originRows(c.internalId())).as("precondition — the creator's ORIGIN row").hasSize(1);
        String allFull = presetSetId(ALL_DOMAINS_FULL);

        revoke(c.operatorId(), c.tenantId(), c.operatorId(), allFull).andExpect(status().isNoContent());

        assertThat(originRows(c.internalId())).isEmpty();
        assertThat(domainRoles(c.accountId(), c.tenantId())).isEmpty();
        assertThat(jdbc.queryForObject("""
                SELECT downstream_detail FROM admin_actions
                 WHERE actor_id = ? AND action_code = 'OPERATOR_DOMAIN_SET_REVOKE' AND outcome = 'SUCCESS'
                """, String.class, c.operatorId()))
                .isEqualTo("setId=" + allFull + " removedSources=ORIGIN keptGroupSources=0");
        revoke(c.operatorId(), c.tenantId(), c.operatorId(), allFull)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOMAIN_PERMISSION_SET_NOT_FOUND"));
    }

    @Test
    @DisplayName("🔴 «이 회사에서 내보내기» 가 ORIGIN 을 지운다 — 두 번째 회사에서 내보내면 그 회사 ORIGIN 0 · 첫 회사 행 바이트 불변")
    void companyExit_deletesOrigin() throws Exception {
        String sfx = sfx();
        String account = "acc-exit-" + sfx;
        String email = "exit-" + sfx + "@example.com";
        String home = "s3-exh-" + sfx;
        long id = seedFacet(account, email, home, "ACTIVE");
        directSet(id, home, "wms-inventory-read");
        stubVerified(account, email);
        String slug = "s3-exn-" + sfx;
        onboard(account, slug).andExpect(status().isCreated());
        assertThat(originRows(id)).as("precondition — the ORIGIN row of the second company").hasSize(1);
        // a second administrator of the new company (the last-admin guard keeps one)
        String admin2 = UUID.randomUUID().toString();
        operator(admin2, slug, null);
        long admin2Id = jdbc.queryForObject("SELECT id FROM admin_operators WHERE operator_id = ?", Long.class, admin2);
        ownTenant(admin2Id, slug);
        List<Map<String, Object>> homeSetsBefore = setRowsOutside(id, slug);

        mockMvc.perform(delete("/api/admin/operators/" + operatorUuid(id) + "/assignments/" + slug)
                        .header("Authorization", "Bearer " + jwt.operatorToken(admin2))
                        .header("X-Operator-Reason", "773 s3 exit"))
                .andExpect(status().isNoContent());

        assertThat(originRows(id)).isEmpty();
        assertThat(count("SELECT COUNT(*) FROM operator_domain_set_grant WHERE operator_id = ? AND tenant_id = ?",
                id, slug)).isZero();
        assertThat(setRowsOutside(id, slug)).isEqualTo(homeSetsBefore);
        assertThat(jdbc.queryForObject("""
                SELECT downstream_detail FROM admin_actions
                 WHERE actor_id = ? AND action_code = 'OPERATOR_ASSIGNMENT_DELETE' AND outcome = 'SUCCESS'
                """, String.class, admin2)).endsWith(",domainSetGrants=1");
    }

    // ═══ received-invitations ═══════════════════════════════════════════════════

    @Test
    @DisplayName("🔴 OD-7 받은 초대: 인증된 내 이메일로 온 살아 있는 PENDING 만(대소문자 무관) — 남의 주소 · 만료 · 취소는 안 보임 · 토큰 없음 · 아무것도 안 씀")
    void receivedInvitations_onlyLivePendingToTheVerifiedEmail() throws Exception {
        String sfx = sfx();
        String account = "acc-rcv-" + sfx;
        String email = "recv-" + sfx + "@example.com";
        String r1 = "s3-r1-" + sfx;
        String r2 = "s3-r2-" + sfx;
        String r3 = "s3-r3-" + sfx;
        for (String t : List.of(r1, r2, r3)) {
            stubTenant(t, "ACTIVE");
        }
        String admin1 = seedTenantAdmin(r1);
        String admin2 = seedTenantAdmin(r2);
        String admin3 = seedTenantAdmin(r3);

        String[] live = invite(admin1, r1, email);                                  // shown
        String[] otherAddress = invite(admin1, r1, "someone-else-" + sfx + "@example.com"); // hidden
        String[] expired = invite(admin2, r2, email);                               // hidden (expired)
        jdbc.update("UPDATE operator_invitation SET expires_at = NOW(6) - INTERVAL 1 MINUTE WHERE invitation_id = ?",
                expired[0]);
        String[] cancelled = invite(admin3, r3, email);                             // hidden (cancelled)
        mockMvc.perform(post("/api/admin/operator-invitations/" + cancelled[0] + ":cancel")
                        .header("Authorization", "Bearer " + jwt.operatorToken(admin3))
                        .header("X-Operator-Reason", "773 s3"))
                .andExpect(status().isOk());
        int auditRowsBefore = count("SELECT COUNT(*) FROM admin_actions");

        // the authoritative account address differs only in case from the invited one
        stubAccountDetail(account, email.toUpperCase());
        stubMatch(account, 200, null);
        String body = received(userOidcToken(account))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountState").value("VERIFIED"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].invitationId").value(live[0]))
                .andExpect(jsonPath("$.items[0].tenantId").value(r1))
                .andExpect(jsonPath("$.items[0].tenantDisplayName").value("S3 " + r1))
                .andExpect(jsonPath("$.items[0].roles[0]").value("TENANT_ADMIN"))
                .andExpect(jsonPath("$.items[0].firstAdmin").value(false))
                .andExpect(jsonPath("$.items[0].expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.items[0].invitedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        // R4 — no token, no link, no inviter, no other address
        assertThat(body).doesNotContain(live[1]).doesNotContain(otherAddress[1]).doesNotContain(expired[1])
                .doesNotContain("someone-else-").doesNotContain("token").doesNotContain(admin1);
        // a read: nothing written
        assertThat(count("SELECT COUNT(*) FROM admin_actions")).isEqualTo(auditRowsBefore);
        assertThat(jdbc.queryForObject("SELECT status FROM operator_invitation WHERE invitation_id = ?",
                String.class, live[0])).isEqualTo("PENDING");
        assertThat(otherAddress[0]).isNotBlank();
    }

    @Test
    @DisplayName("🔴 OD-7 받은 초대: 미인증 → EMAIL_NOT_VERIFIED + [] · 풀 계정 아님 → NOT_ELIGIBLE + [] · 판정 못 받음 → 503 · subjectToken 없음 → 400 · 운영자 토큰 → 401")
    void receivedInvitations_statesAndRefusals() throws Exception {
        String sfx = sfx();
        String t = "s3-rx-" + sfx;
        stubTenant(t, "ACTIVE");
        String admin = seedTenantAdmin(t);
        String email = "rx-" + sfx + "@example.com";
        invite(admin, t, email);

        String unverified = "acc-rxu-" + sfx;
        stubAccountDetail(unverified, email);
        stubMatch(unverified, 403, "EMAIL_NOT_VERIFIED");
        received(userOidcToken(unverified)).andExpect(status().isOk())
                .andExpect(jsonPath("$.accountState").value("EMAIL_NOT_VERIFIED"))
                .andExpect(jsonPath("$.items.length()").value(0));

        String site = "acc-rxs-" + sfx;
        stubAccountDetail(site, email);
        stubMatch(site, 404, "ACCOUNT_NOT_FOUND");
        received(userOidcToken(site)).andExpect(status().isOk())
                .andExpect(jsonPath("$.accountState").value("NOT_ELIGIBLE"))
                .andExpect(jsonPath("$.items.length()").value(0));

        // an `iam`-credential operator (no account row): the onboarding email exemption does NOT carry over here —
        // there is no verified email to key on → NOT_ELIGIBLE + [] (owner decision option A)
        String iamAccount = "acc-rxi-" + sfx;
        seedFacet(iamAccount, email, "s3-rxh-" + sfx, "ACTIVE");
        stubAccountNotFound(iamAccount);
        received(userOidcToken(iamAccount)).andExpect(status().isOk())
                .andExpect(jsonPath("$.accountState").value("NOT_ELIGIBLE"))
                .andExpect(jsonPath("$.items.length()").value(0));
        wireMock.verify(0, postRequestedFor(urlPathEqualTo(matchPath(iamAccount))));

        String down = "acc-rxd-" + sfx;
        stubAccountDetail(down, email);
        wireMock.stubFor(WireMock.post(urlPathEqualTo(matchPath(down)))
                .willReturn(aResponse().withStatus(500).withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":\"INTERNAL_ERROR\",\"message\":\"x\"}")));
        received(userOidcToken(down)).andExpect(status().isServiceUnavailable());

        mockMvc.perform(post("/api/admin/onboarding/received-invitations")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        received(jwt.operatorToken(admin)).andExpect(status().isUnauthorized());
    }

    // ═══ helpers ═══════════════════════════════════════════════════════════════

    record Creator(String accountId, String operatorId, long internalId, String tenantId) {}

    /** A non-operator verified pool account creates a company through the endpoint — its first admin. */
    private Creator createCompany(String tag) throws Exception {
        String sfx = sfx();
        String account = "acc-" + tag + "-" + sfx;
        String email = tag + "-" + sfx + "@example.com";
        String slug = "s3-" + tag + "-" + sfx;
        stubVerified(account, email);
        String operatorId = JSON.readTree(onboard(account, slug).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("operatorId").asText();
        return new Creator(account, operatorId, operatorInternalId(account), slug);
    }

    private static String sfx() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String jwksJson(RSAPublicKey key) {
        return "{\"keys\":[{\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"RS256\",\"kid\":\"" + JWKS_KID
                + "\",\"n\":\"" + b64Url(key.getModulus().toByteArray()) + "\",\"e\":\""
                + b64Url(key.getPublicExponent().toByteArray()) + "\"}]}";
    }

    private static String b64Url(byte[] bytes) {
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] s = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, s, 0, s.length);
            bytes = s;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String userOidcToken(String sub) {
        Instant now = Instant.now();
        return Jwts.builder().header().keyId(JWKS_KID).and()
                .subject(sub).issuer(OIDC_ISSUER).audience().add(CONSOLE_CLIENT).and()
                .issuedAt(Date.from(now)).expiration(Date.from(now.plus(15, ChronoUnit.MINUTES)))
                .signWith(authKeyPair.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private static String tenantJson(String tenantId, String status) {
        return """
                {"tenantId":"%s","displayName":"S3 %s","tenantType":"B2B_ENTERPRISE","status":"%s",
                 "createdAt":"2026-10-15T00:00:00Z","updatedAt":"2026-10-15T00:00:00Z"}
                """.formatted(tenantId, tenantId, status);
    }

    private void stubTenant(String tenantId, String status) {
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/tenants/" + tenantId))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(tenantJson(tenantId, status))));
    }

    private void stubAccountDetail(String accountId, String email) {
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/accounts/" + accountId))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"" + accountId + "\",\"email\":\"" + email
                                + "\",\"status\":\"ACTIVE\",\"createdAt\":\"2026-10-15T00:00:00Z\","
                                + "\"profile\":{\"displayName\":\"S3 Caller\",\"phoneMasked\":null}}")));
    }

    /** account-service has no account row for this {@code sub} — what an `iam` credential looks like (option A). */
    private void stubAccountNotFound(String accountId) {
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/accounts/" + accountId))
                .willReturn(aResponse().withStatus(404).withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":\"ACCOUNT_NOT_FOUND\",\"message\":\"x\"}")));
    }

    private static String matchPath(String accountId) {
        return "/internal/accounts/" + accountId + "/verified-email:match";
    }

    /** account-service's verdict (the predicate itself is account-service's — 772's VerifiedEmailRequirement). */
    private void stubMatch(String accountId, int status, String code) {
        String body = status == 200
                ? "{\"accountId\":\"" + accountId + "\",\"emailVerifiedAt\":\"2026-10-15T00:00:00Z\"}"
                : "{\"code\":\"" + code + "\",\"message\":\"x\"}";
        wireMock.stubFor(WireMock.post(urlPathEqualTo(matchPath(accountId)))
                .willReturn(aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body)));
    }

    private void stubVerified(String accountId, String email) {
        stubAccountDetail(accountId, email);
        stubMatch(accountId, 200, null);
    }

    private ResultActions onboard(String accountId, String tenantId) throws Exception {
        return mockMvc.perform(post("/api/admin/onboarding/organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subjectToken\":\"" + userOidcToken(accountId) + "\",\"tenantId\":\"" + tenantId
                        + "\",\"organizationName\":\"S3 Org\"}"));
    }

    private ResultActions received(String subjectToken) throws Exception {
        return mockMvc.perform(post("/api/admin/onboarding/received-invitations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subjectToken\":\"" + subjectToken + "\"}"));
    }

    private ResultActions grant(String actor, String tenant, String target, String body) throws Exception {
        return mockMvc.perform(post("/api/admin/operators/" + target + "/domain-permission-sets")
                .header("Authorization", "Bearer " + jwt.operatorToken(actor)).header("X-Tenant-Id", tenant)
                .header("X-Operator-Reason", "773 s3 grant").contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions revoke(String actor, String tenant, String target, String setId) throws Exception {
        return mockMvc.perform(delete("/api/admin/operators/" + target + "/domain-permission-sets/" + setId)
                .header("Authorization", "Bearer " + jwt.operatorToken(actor)).header("X-Tenant-Id", tenant)
                .header("X-Operator-Reason", "773 s3 revoke"));
    }

    /** Issues an invitation through the 772 management API and returns {invitationId, raw token from the mail}. */
    private String[] invite(String actor, String tenantId, String email) throws Exception {
        String json = mockMvc.perform(post("/api/admin/operator-invitations")
                        .header("Authorization", "Bearer " + jwt.operatorToken(actor))
                        .header("X-Operator-Reason", "773 s3")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","displayName":"S3 피초대자","roles":["TENANT_ADMIN"],"tenantId":"%s"}
                                """.formatted(email, tenantId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String invitationId = JSON.readTree(json).get("invitationId").asText();
        List<LoggedRequest> mails = wireMock.findAll(postRequestedFor(urlPathEqualTo(MAIL_PATH)));
        String token = JSON.readTree(mails.get(mails.size() - 1).getBodyAsString()).get("token").asText();
        return new String[]{invitationId, token};
    }

    private int tenantCreateCalls(String slug) {
        return wireMock.findAll(postRequestedFor(urlEqualTo("/internal/tenants"))
                .withRequestBody(matchingJsonPath("$.tenantId", equalTo(slug)))).size();
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private int operatorsWithSubject(String accountId) {
        return count("SELECT COUNT(*) FROM admin_operators WHERE oidc_subject = ?", accountId);
    }

    private long operatorInternalId(String accountId) {
        Long id = jdbc.queryForObject("SELECT id FROM admin_operators WHERE oidc_subject = ?", Long.class, accountId);
        return id == null ? -1L : id;
    }

    private String operatorUuid(long id) {
        return jdbc.queryForObject("SELECT operator_id FROM admin_operators WHERE id = ?", String.class, id);
    }

    private int ownedTenants(long id) {
        return count("""
                SELECT COUNT(DISTINCT x.tenant_id) FROM admin_operator_roles x JOIN admin_roles r ON r.id = x.role_id
                 WHERE x.operator_id = ? AND r.name = 'TENANT_ADMIN' AND x.tenant_id <> '*'""", id);
    }

    private List<Map<String, Object>> originRows(long id) {
        return jdbc.queryForList("""
                SELECT g.tenant_id, s.system_key, g.granted_by, g.group_origin
                  FROM operator_domain_set_grant g JOIN domain_permission_set s ON s.id = g.set_id
                 WHERE g.operator_id = ? AND g.source_key = 'ORIGIN'""", id);
    }

    private List<String> allDomainsFullCatalog() {
        return jdbc.queryForList("""
                SELECT DISTINCT r.role_name FROM domain_permission_set_role r
                  JOIN domain_permission_set s ON s.id = r.set_id WHERE s.system_key = ?""", String.class,
                ALL_DOMAINS_FULL);
    }

    private String presetSetId(String systemKey) {
        return jdbc.queryForObject("SELECT set_id FROM domain_permission_set WHERE system_key = ?", String.class,
                systemKey);
    }

    private long presetInternalId(String systemKey) {
        Long id = jdbc.queryForObject("SELECT id FROM domain_permission_set WHERE system_key = ?", Long.class,
                systemKey);
        return id == null ? -1L : id;
    }

    private List<String> domainRoles(String oidcSubject, String tenant) throws Exception {
        String body = mockMvc.perform(get("/internal/operator-assignments/check")
                        .param("oidcSubject", oidcSubject).param("tenantId", tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigned").value(true))
                .andReturn().getResponse().getContentAsString();
        JsonNode roles = JSON.readTree(body).get("domainRoles");
        List<String> out = new ArrayList<>();
        if (roles != null) {
            roles.forEach(n -> out.add(n.asText()));
        }
        return out;
    }

    /** Every column a membership addition must not write (the home included). */
    private Map<String, Object> operatorRow(long id) {
        return jdbc.queryForMap("""
                SELECT operator_id, tenant_id, email, display_name, status, oidc_subject, identity_id,
                       password_hash, updated_at, version
                  FROM admin_operators WHERE id = ?""", id);
    }

    private List<Map<String, Object>> allRoleRows(long id) {
        return jdbc.queryForList("SELECT * FROM admin_operator_roles WHERE operator_id = ? ORDER BY tenant_id, role_id",
                id);
    }

    private List<Map<String, Object>> allAssignmentRows(long id) {
        return jdbc.queryForList("SELECT * FROM operator_tenant_assignment WHERE operator_id = ? ORDER BY tenant_id",
                id);
    }

    private List<Map<String, Object>> roleRowsOutside(long id, String tenantId) {
        return jdbc.queryForList("""
                SELECT * FROM admin_operator_roles WHERE operator_id = ? AND tenant_id <> ?
                 ORDER BY tenant_id, role_id""", id, tenantId);
    }

    private List<Map<String, Object>> assignmentRowsOutside(long id, String tenantId) {
        return jdbc.queryForList("""
                SELECT * FROM operator_tenant_assignment WHERE operator_id = ? AND tenant_id <> ?
                 ORDER BY tenant_id""", id, tenantId);
    }

    private List<Map<String, Object>> setRowsOutside(long id, String tenantId) {
        return jdbc.queryForList("""
                SELECT * FROM operator_domain_set_grant WHERE operator_id = ? AND tenant_id <> ?
                 ORDER BY tenant_id, set_id, source_key""", id, tenantId);
    }

    private void operator(String uuid, String home, String oidcSubject) {
        jdbc.update("""
                INSERT INTO admin_operators
                  (operator_id, tenant_id, email, password_hash, display_name, status, oidc_subject,
                   created_at, updated_at, version)
                VALUES (?, ?, ?, NULL, 'S3 op', 'ACTIVE', ?, NOW(6), NOW(6), 0)
                """, uuid, home, uuid + "@example.com", oidcSubject);
    }

    /** An operator row linked to {@code accountId}: TENANT_ADMIN in its home + the home membership. */
    private long seedFacet(String accountId, String email, String home, String status) {
        String operatorId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO admin_operators
                  (operator_id, tenant_id, email, password_hash, display_name,
                   status, oidc_subject, created_at, updated_at, version)
                VALUES (?, ?, ?, NULL, 'S3 facet', ?, ?, NOW(6), NOW(6), 0)
                """, operatorId, home, email, status, accountId);
        long id = operatorInternalId(accountId);
        ownTenant(id, home);
        return id;
    }

    /** TENANT_ADMIN role row + membership in {@code tenantId} (one more «owned» tenant). */
    private void ownTenant(long id, String tenantId) {
        role(id, "TENANT_ADMIN", tenantId);
        jdbc.update("""
                INSERT IGNORE INTO operator_tenant_assignment (operator_id, tenant_id, granted_at, granted_by)
                VALUES (?, ?, NOW(6), NULL)""", id, tenantId);
    }

    private void role(long id, String roleName, String tenantId) {
        jdbc.update("""
                INSERT INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
                SELECT ?, r.id, ?, NOW(6), NULL FROM admin_roles r WHERE r.name = ?
                """, id, tenantId, roleName);
    }

    private void directSet(long id, String tenantId, String presetKey) {
        jdbc.update("""
                INSERT INTO operator_domain_set_grant
                  (operator_id, tenant_id, set_id, source_key, group_origin, granted_by, granted_at)
                SELECT ?, ?, s.id, 'DIRECT', NULL, NULL, NOW(6) FROM domain_permission_set s WHERE s.system_key = ?
                """, id, tenantId, presetKey);
    }

    /** A TENANT_ADMIN of {@code tenantId} who can issue invitations there; returns its operator UUID. */
    private String seedTenantAdmin(String tenantId) {
        String operatorId = UUID.randomUUID().toString();
        operator(operatorId, tenantId, null);
        long id = jdbc.queryForObject("SELECT id FROM admin_operators WHERE operator_id = ?", Long.class, operatorId);
        ownTenant(id, tenantId);
        return operatorId;
    }
}
