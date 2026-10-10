package com.example.auth.integration;

import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.credentials.CredentialHash;
import com.example.auth.domain.repository.RefreshTokenRepository;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-615 (ADR-MONO-078 A; multi-tenancy.md § 소비자 계정 풀 § 4) — a consumer-POOL account
 * driven through the real browser path: form login (pool credential first) → authorize → code →
 * token, then single sign-on to the other consumer site, refresh, the console, and logout.
 *
 * <p>The pool account's credential row is {@code tenant_id = 'consumer-pool'}. account-service is
 * WireMock: status ACTIVE, and the new {@code /internal/tenants/{site}/consumer-members/{id}} read
 * (auth-to-account.md) answers per site. Two pool accounts:
 * <ul>
 *   <li>{@link #BOTH} — ACTIVE member of {@code ecommerce} AND {@code fan-platform}.</li>
 *   <li>{@link #STORE_ONLY} — member of {@code ecommerce} only (signed up at the store; the fan
 *       site's first-visit consent is {@code TASK-BE-616}).</li>
 * </ul>
 * The per-site controls (AC-2, AC-6) are {@code SsoTenantGateIntegrationTest} /
 * {@code CrossTenantLoginRefreshIntegrationTest}, unchanged.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConsumerPoolSsoIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    static WireMockServer accountService;
    /** TASK-MONO-772 S4 — admin-service's console-eligibility read (auth-to-admin.md). */
    static WireMockServer adminService;

    private static final String PASSWORD = "PoolSsoPassw0rd!";
    private static final String BOTH = "0199de70-0000-7000-8000-0000000b0615";
    private static final String BOTH_EMAIL = "pool-both-615@example.com";
    private static final String STORE_ONLY = "0199de70-0000-7000-8000-0000000c0615";
    private static final String STORE_ONLY_EMAIL = "pool-store-615@example.com";
    /** TASK-BE-616 — signs up at the store in the test, then consents to the fan site. */
    private static final String NEW_SHOPPER = "0199de70-0000-7000-8000-0000000e0616";
    private static final String NEW_SHOPPER_EMAIL = "pool-new-616@example.com";
    /** TASK-BE-616 — a store member who declines the fan site. */
    private static final String DECLINER = "0199de70-0000-7000-8000-0000000f0616";
    private static final String DECLINER_EMAIL = "pool-decline-616@example.com";
    private static final String FAN_CONSENT = "fan-consent-616";
    /** TASK-MONO-772 S4 — a pool account with a live operator facet (ACTIVE operator, oidc_subject = this id). */
    private static final String OPERATOR = "0199de70-0000-7000-8000-000000a10772";
    private static final String OPERATOR_EMAIL = "pool-operator-772@example.com";
    private static final String FACET = "facet-772";
    /** TASK-MONO-772 S4 — a pool account whose eligibility admin-service cannot answer (503). */
    private static final String UNANSWERED = "0199de70-0000-7000-8000-000000b10772";
    private static final String UNANSWERED_EMAIL = "pool-unanswered-772@example.com";
    /** TASK-BE-610 dual credential: a pool credential AND an `iam` credential under one email. */
    private static final String DUAL_POOL = "0199de70-0000-7000-8000-000000c10772";
    private static final String DUAL_IAM = "0199de70-0000-7000-8000-000000d10772";
    private static final String DUAL_EMAIL = "pool-dual-772@example.com";

    private static final String STORE_CLIENT_ID = "ecommerce-web-store-client";
    private static final String STORE_CLIENT_SECRET = "ecommerce-dev";
    private static final String STORE_REDIRECT_URI = "http://localhost:3000/api/auth/callback/iam";
    private static final String FAN_CLIENT_ID = "demo-spa-client";
    private static final String FAN_REDIRECT_URI = "http://localhost:3000/callback";
    private static final String CONSOLE_CLIENT_ID = "platform-console-web";
    private static final String CONSOLE_REDIRECT_URI = "http://localhost:3000/api/auth/callback";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

        accountService = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        accountService.start();
        registry.add("auth.account-service.base-url", accountService::baseUrl);
        for (String accountId : List.of(BOTH, STORE_ONLY, NEW_SHOPPER, DECLINER, OPERATOR, UNANSWERED, DUAL_POOL)) {
            accountService.stubFor(WireMock.get(WireMock.urlPathEqualTo("/internal/accounts/" + accountId + "/status"))
                    .willReturn(json("""
                            { "accountId": "%s", "status": "ACTIVE", "statusChangedAt": "2026-10-01T00:00:00Z" }
                            """.formatted(accountId))));
        }
        membership("ecommerce", BOTH, "\"ACTIVE\"");
        membership("fan-platform", BOTH, "\"ACTIVE\"");
        membership("ecommerce", STORE_ONLY, "\"ACTIVE\"");
        membership("fan-platform", STORE_ONLY, "null");
        membership("ecommerce", OPERATOR, "\"ACTIVE\"");
        membership("ecommerce", UNANSWERED, "\"ACTIVE\"");
        membership("ecommerce", DUAL_POOL, "\"ACTIVE\"");

        // ── TASK-MONO-772 S4 — admin-service console-eligibility ──
        adminService = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        adminService.start();
        registry.add("auth.admin-service.base-url", adminService::baseUrl);
        eligibility(BOTH, json("{ \"eligible\": false }"));
        eligibility(DUAL_POOL, json("{ \"eligible\": false }"));
        eligibility(UNANSWERED, WireMock.aResponse().withStatus(503));
        // OPERATOR: eligible until the operator row is suspended (scenario state REVOKED) — what admin-service's
        // predicate (oidc_subject = id ∧ status = ACTIVE) answers after PATCH …/status.
        adminService.stubFor(eligibilityRequest(OPERATOR).inScenario(FACET)
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(json("{ \"eligible\": true }")));
        adminService.stubFor(eligibilityRequest(OPERATOR).inScenario(FACET).whenScenarioStateIs("REVOKED")
                .willReturn(json("{ \"eligible\": false }")));

        // ── TASK-BE-616 ──
        // NEW_SHOPPER: signs up at the store (pool account + store membership), then visits fan for the
        // first time. The fan membership is a WireMock scenario: no membership until the consent PUT,
        // ACTIVE after it — what account-service's ConsentToConsumerSiteUseCase does.
        membership("ecommerce", NEW_SHOPPER, "\"ACTIVE\"");
        accountService.stubFor(WireMock.get(WireMock.urlPathEqualTo(
                        "/internal/tenants/fan-platform/consumer-members/" + NEW_SHOPPER))
                .inScenario(FAN_CONSENT).whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(membershipBody("fan-platform", NEW_SHOPPER, "null")));
        accountService.stubFor(WireMock.put(WireMock.urlPathEqualTo(
                        "/internal/tenants/fan-platform/consumer-members/" + NEW_SHOPPER))
                .inScenario(FAN_CONSENT).willSetStateTo("CONSENTED")
                .willReturn(membershipBody("fan-platform", NEW_SHOPPER, "\"ACTIVE\"")));
        accountService.stubFor(WireMock.get(WireMock.urlPathEqualTo(
                        "/internal/tenants/fan-platform/consumer-members/" + NEW_SHOPPER))
                .inScenario(FAN_CONSENT).whenScenarioStateIs("CONSENTED")
                .willReturn(membershipBody("fan-platform", NEW_SHOPPER, "\"ACTIVE\"")));
        // DECLINER: a store member who says «no» to the fan site. No PUT stub on purpose — a write would 404.
        membership("ecommerce", DECLINER, "\"ACTIVE\"");
        membership("fan-platform", DECLINER, "null");
        // The store signup page asks whether the store tenant may take signups (TASK-BE-581) and then
        // proxies the signup itself (account-service decides it is a pool signup — TASK-BE-614).
        accountService.stubFor(WireMock.get(WireMock.urlPathEqualTo("/internal/tenants/ecommerce"))
                .willReturn(json("""
                        { "tenantId": "ecommerce", "displayName": "E-Commerce Platform",
                          "tenantType": "B2C_CONSUMER", "status": "ACTIVE" }
                        """)));
        accountService.stubFor(WireMock.post(WireMock.urlPathEqualTo("/api/accounts/signup"))
                .willReturn(WireMock.aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("""
                                { "accountId": "%s", "email": "%s", "status": "ACTIVE" }
                                """.formatted(NEW_SHOPPER, NEW_SHOPPER_EMAIL))));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder membershipBody(
            String site, String accountId, String statusJson) {
        return json("""
                { "accountId": "%s", "siteTenantId": "%s", "consumerSite": true,
                  "siteTenantType": "B2C_CONSUMER", "membershipStatus": %s, "siteRoles": [] }
                """.formatted(accountId, site, statusJson));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String body) {
        return WireMock.aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }

    private static com.github.tomakehurst.wiremock.client.MappingBuilder eligibilityRequest(String accountId) {
        return WireMock.get(WireMock.urlPathEqualTo("/internal/operators/console-eligibility"))
                .withQueryParam("accountId", WireMock.equalTo(accountId));
    }

    private static void eligibility(String accountId,
                                    com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder answer) {
        adminService.stubFor(eligibilityRequest(accountId).willReturn(answer));
    }

    private static void membership(String site, String accountId, String statusJson) {
        accountService.stubFor(WireMock.get(WireMock.urlPathEqualTo(
                        "/internal/tenants/" + site + "/consumer-members/" + accountId))
                .willReturn(json("""
                        { "accountId": "%s", "siteTenantId": "%s", "consumerSite": true,
                          "siteTenantType": "B2C_CONSUMER", "membershipStatus": %s, "siteRoles": [] }
                        """.formatted(accountId, site, statusJson))));
    }

    @AfterAll
    static void stopAccountService() {
        if (accountService != null && accountService.isRunning()) {
            accountService.stop();
        }
        if (adminService != null && adminService.isRunning()) {
            adminService.stop();
        }
    }

    @MockitoBean
    IamClientCredentialsTokenProvider gapTokenProvider;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CredentialJpaRepository credentialJpaRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedPoolCredentials() {
        Mockito.when(gapTokenProvider.currentBearer()).thenReturn("test-jwt");
        accountService.resetRequests();
        accountService.resetScenarios();
        adminService.resetRequests();
        adminService.resetScenarios();
        credentialJpaRepository.deleteAll();
        String hash = new Argon2idPasswordHasher().hash(PASSWORD);
        credentialJpaRepository.save(CredentialJpaEntity.fromDomain(Credential.create(
                BOTH, "consumer-pool", BOTH_EMAIL, CredentialHash.argon2id(hash), Instant.now())));
        credentialJpaRepository.save(CredentialJpaEntity.fromDomain(Credential.create(
                STORE_ONLY, "consumer-pool", STORE_ONLY_EMAIL, CredentialHash.argon2id(hash), Instant.now())));
        credentialJpaRepository.save(CredentialJpaEntity.fromDomain(Credential.create(
                DECLINER, "consumer-pool", DECLINER_EMAIL, CredentialHash.argon2id(hash), Instant.now())));
        credentialJpaRepository.save(CredentialJpaEntity.fromDomain(Credential.create(
                OPERATOR, "consumer-pool", OPERATOR_EMAIL, CredentialHash.argon2id(hash), Instant.now())));
        credentialJpaRepository.save(CredentialJpaEntity.fromDomain(Credential.create(
                UNANSWERED, "consumer-pool", UNANSWERED_EMAIL, CredentialHash.argon2id(hash), Instant.now())));
        credentialJpaRepository.save(CredentialJpaEntity.fromDomain(Credential.create(
                DUAL_POOL, "consumer-pool", DUAL_EMAIL, CredentialHash.argon2id(hash), Instant.now())));
        credentialJpaRepository.save(CredentialJpaEntity.fromDomain(Credential.create(
                DUAL_IAM, "iam", DUAL_EMAIL, CredentialHash.argon2id(hash), Instant.now())));
    }

    // ── AC-1 ──────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-1: 풀 계정 팬 로그인 → 스토어 authorize 는 폼 없이 코드 → sub 동일 · tenant_id=ecommerce · roles 에 FAN 없음")
    void poolLoginOnFan_thenStoreWithoutForm_sameSub_siteTenant_noFlattening() throws Exception {
        Pkce fanPkce = Pkce.create();
        MockHttpSession session = loginThrough(BOTH_EMAIL, FAN_CLIENT_ID, FAN_REDIRECT_URI, fanPkce);
        JsonNode fanTokens = exchange(authorizeExpectingCode(session, FAN_CLIENT_ID, FAN_REDIRECT_URI, fanPkce),
                FAN_CLIENT_ID, FAN_REDIRECT_URI, fanPkce, 200);
        String fanAccess = fanTokens.get("access_token").asText();
        assertThat(claim(fanAccess, "tenant_id")).isEqualTo("fan-platform");
        assertThat(claim(fanAccess, "sub")).isEqualTo(BOTH);
        assertThat(roles(fanAccess)).containsExactly("FAN");

        // Single sign-on: the store authorize yields a code at once — no /login.
        Pkce storePkce = Pkce.create();
        String storeCode = authorizeExpectingCode(session, STORE_CLIENT_ID, STORE_REDIRECT_URI, storePkce);
        JsonNode storeTokens = exchange(storeCode, STORE_CLIENT_ID, STORE_REDIRECT_URI, storePkce, 200);
        String storeAccess = storeTokens.get("access_token").asText();
        assertThat(claim(storeAccess, "sub")).as("one account on both sites").isEqualTo(BOTH);
        assertThat(claim(storeAccess, "tenant_id")).isEqualTo("ecommerce").isNotEqualTo("consumer-pool");
        assertThat(roles(storeAccess)).containsExactly("CUSTOMER").doesNotContain("FAN");
        assertThat(claim(storeTokens.get("id_token").asText(), "tenant_id")).isEqualTo("ecommerce");

        // The mirror rows carry the SITE (the claim), never the pool value.
        assertThat(refreshTokenRepository.findByJti(fanTokens.get("refresh_token").asText())
                .orElseThrow().getTenantId()).isEqualTo("fan-platform");
        assertThat(refreshTokenRepository.findByJti(storeTokens.get("refresh_token").asText())
                .orElseThrow().getTenantId()).isEqualTo("ecommerce");
    }

    // ── no membership → no token, no loop ───────────────────────────────────────────────────────

    /**
     * TASK-BE-616 — CHANGED EXPECTATION. Under TASK-BE-615 this cell was
     * {@code storeOnlyPoolAccount_onFan_noTokenAndNoLoop}: the fan authorize issued a code and the token
     * endpoint refused it ({@code invalid_grant}). The first-visit consent screen now takes that place:
     * no code at all until the person answers. Still never a {@code /login} bounce (no loop).
     */
    @Test
    @DisplayName("TASK-BE-616: 멤버십 없는 사이트(스토어 전용 풀 계정 → 팬) → 코드 대신 /consent · /login 아님 · 두 번째 시도도 같다")
    void storeOnlyPoolAccount_onFan_consentScreen_noCode_noLoop() throws Exception {
        MockHttpSession session = loginThrough(STORE_ONLY_EMAIL, STORE_CLIENT_ID, STORE_REDIRECT_URI, Pkce.create());

        assertThat(authorizeExpectingConsent(session, FAN_CLIENT_ID, FAN_REDIRECT_URI, Pkce.create()))
                .doesNotContain("code=");
        assertThat(authorizeExpectingConsent(session, FAN_CLIENT_ID, FAN_REDIRECT_URI, Pkce.create()))
                .as("a second attempt is the same screen — never /login").endsWith("/consent");
    }

    // ── TASK-BE-616 — first-visit consent ───────────────────────────────────────────────────────

    /**
     * AC-6 — «스토어 풀 가입 → 팬 첫 방문 → 동의 → 팬 토큰» through the browser path of THIS service.
     * account-service is WireMock: the signup proxy is answered 201 (account-service would make it a
     * pool account — that half is account-service's {@code ConsumerSiteConsentIntegrationTest}), and
     * the pool credential account-service would write through {@code POST /internal/auth/credentials}
     * is written directly here. The fan membership turns ACTIVE only when the consent PUT arrives.
     */
    @Test
    @DisplayName("AC-6/AC-1: 스토어 풀 가입 → 스토어 토큰(CUSTOMER) → 팬 첫 방문 /consent → 동의 → 팬 토큰: sub 동일 · tenant_id=fan-platform · roles 에 FAN, CUSTOMER 없음")
    void storeSignup_fanFirstVisit_consent_fanToken() throws Exception {
        // 1. Store authorize → /login (a saved authorize request now carries the store client).
        Pkce storePkce = Pkce.create();
        MvcResult start = mockMvc.perform(authorize(null, STORE_CLIENT_ID, STORE_REDIRECT_URI, storePkce))
                .andExpect(status().is3xxRedirection()).andReturn();
        MockHttpSession session = (MockHttpSession) start.getRequest().getSession(false);

        // 2. Signup on the store's signup page — proxied to account-service with the store tenant.
        mockMvc.perform(post("/signup").session(session).with(csrf())
                        .param("email", NEW_SHOPPER_EMAIL)
                        .param("password", PASSWORD)
                        .param("confirmPassword", PASSWORD))
                .andExpect(status().is3xxRedirection());
        accountService.verify(WireMock.postRequestedFor(WireMock.urlPathEqualTo("/api/accounts/signup"))
                .withHeader("X-Tenant-Id", WireMock.equalTo("ecommerce")));
        // What account-service's pool signup writes here (POST /internal/auth/credentials, tenant consumer-pool).
        credentialJpaRepository.save(CredentialJpaEntity.fromDomain(Credential.create(
                NEW_SHOPPER, "consumer-pool", NEW_SHOPPER_EMAIL,
                CredentialHash.argon2id(new Argon2idPasswordHasher().hash(PASSWORD)), Instant.now())));

        // 3. Log in — the pool credential — and take the store token.
        MvcResult login = mockMvc.perform(post("/login").session(session).with(csrf())
                        .param("username", NEW_SHOPPER_EMAIL).param("password", PASSWORD))
                .andExpect(status().is3xxRedirection()).andReturn();
        session = (MockHttpSession) login.getRequest().getSession(false);
        String storeAccess = exchange(authorizeExpectingCode(session, STORE_CLIENT_ID, STORE_REDIRECT_URI, storePkce),
                STORE_CLIENT_ID, STORE_REDIRECT_URI, storePkce, 200).get("access_token").asText();
        assertThat(claim(storeAccess, "tenant_id")).isEqualTo("ecommerce");
        assertThat(roles(storeAccess)).containsExactly("CUSTOMER");

        // 4. First visit to the fan site: the consent screen, not a code, not /login.
        Pkce fanPkce = Pkce.create();
        authorizeExpectingConsent(session, FAN_CLIENT_ID, FAN_REDIRECT_URI, fanPkce);
        MvcResult page = mockMvc.perform(get("/consent").session(session)).andExpect(status().isOk()).andReturn();
        assertThat(page.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("이 사이트 이용 동의").contains("value=\"accept\"").contains("value=\"decline\"");
        accountService.verify(0, WireMock.putRequestedFor(WireMock.urlPathMatching("/internal/tenants/.*")));

        // 5. Accept → account-service writes the membership → the parked authorize resumes.
        MvcResult accepted = mockMvc.perform(post("/consent").session(session).with(csrf())
                        .param("decision", "accept"))
                .andExpect(status().is3xxRedirection()).andReturn();
        String resume = accepted.getResponse().getRedirectedUrl();
        assertThat(resume).contains("/oauth2/authorize").contains("client_id=" + FAN_CLIENT_ID);
        accountService.verify(1, WireMock.putRequestedFor(WireMock.urlPathEqualTo(
                "/internal/tenants/fan-platform/consumer-members/" + NEW_SHOPPER)));

        MvcResult resumed = mockMvc.perform(get(URI.create(resume)).session(session))
                .andExpect(status().is3xxRedirection()).andReturn();
        String location = resumed.getResponse().getHeader("Location");
        assertThat(location).as("the resumed authorize yields a code").startsWith(FAN_REDIRECT_URI).contains("code=");

        // 6. The fan token: one account, the fan site, the fan seed role only.
        JsonNode fanTokens = exchange(queryParam(location, "code"), FAN_CLIENT_ID, FAN_REDIRECT_URI, fanPkce, 200);
        String fanAccess = fanTokens.get("access_token").asText();
        assertThat(claim(fanAccess, "sub")).isEqualTo(claim(storeAccess, "sub")).isEqualTo(NEW_SHOPPER);
        assertThat(claim(fanAccess, "tenant_id")).isEqualTo("fan-platform").isNotEqualTo("consumer-pool");
        assertThat(roles(fanAccess)).contains("FAN").doesNotContain("CUSTOMER");

        // AC-2: the next fan visit is a plain SSO code — no consent screen again.
        authorizeExpectingCode(session, FAN_CLIENT_ID, FAN_REDIRECT_URI, Pkce.create());
        accountService.verify(1, WireMock.putRequestedFor(WireMock.urlPathMatching("/internal/tenants/.*")));
    }

    @Test
    @DisplayName("AC-1: 동의 거절 → 팬 client 로 error=access_denied + state · 코드·토큰 없음 · 멤버십 쓰기 없음 · IAM 세션은 그대로(스토어 SSO 유지)")
    void decline_returnsAccessDenied_noWrite_sessionKept() throws Exception {
        MockHttpSession session = loginThrough(DECLINER_EMAIL, STORE_CLIENT_ID, STORE_REDIRECT_URI, Pkce.create());
        authorizeExpectingConsent(session, FAN_CLIENT_ID, FAN_REDIRECT_URI, Pkce.create());

        MvcResult declined = mockMvc.perform(post("/consent").session(session).with(csrf())
                        .param("decision", "decline"))
                .andExpect(status().is3xxRedirection()).andReturn();

        assertThat(declined.getResponse().getRedirectedUrl())
                .startsWith(FAN_REDIRECT_URI + "?error=access_denied")
                .contains("state=be-615")
                .doesNotContain("code=");
        accountService.verify(0, WireMock.putRequestedFor(WireMock.urlPathMatching("/internal/tenants/.*")));
        // The IAM session survives a decline: the store still signs in without a form.
        authorizeExpectingCode(session, STORE_CLIENT_ID, STORE_REDIRECT_URI, Pkce.create());
    }

    @Test
    @DisplayName("AC-3: 콘솔 client 에는 동의 화면이 없다 — 풀 세션도 /consent 로 가지 않는다(BE-610 그대로, 토큰은 발급자가 거절)")
    void console_neverShowsConsent() throws Exception {
        MockHttpSession session = loginThrough(STORE_ONLY_EMAIL, STORE_CLIENT_ID, STORE_REDIRECT_URI, Pkce.create());

        MvcResult console = mockMvc.perform(authorize(session, CONSOLE_CLIENT_ID, CONSOLE_REDIRECT_URI, Pkce.create()))
                .andExpect(status().is3xxRedirection()).andReturn();

        assertThat(console.getResponse().getHeader("Location")).doesNotEndWith("/consent");
        accountService.verify(0, WireMock.putRequestedFor(WireMock.urlPathMatching("/internal/tenants/.*")));
    }

    // ── AC-5 ──────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-5: 스토어 refresh 로 팬 토큰 못 받음(400) · 스토어 refresh 는 200(ecommerce) · 미러 행을 fan-platform 으로 바꾸면 TOKEN_TENANT_MISMATCH")
    void refresh_storeTokenNeverYieldsFanToken() throws Exception {
        Pkce storePkce = Pkce.create();
        MockHttpSession session = loginThrough(BOTH_EMAIL, STORE_CLIENT_ID, STORE_REDIRECT_URI, storePkce);
        JsonNode storeTokens = exchange(authorizeExpectingCode(session, STORE_CLIENT_ID, STORE_REDIRECT_URI, storePkce),
                STORE_CLIENT_ID, STORE_REDIRECT_URI, storePkce, 200);
        String storeRefresh = storeTokens.get("refresh_token").asText();

        // The fan client presenting the store's refresh token: no fan token.
        MvcResult asFan = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", storeRefresh)
                        .param("client_id", FAN_CLIENT_ID))
                .andReturn();
        assertThat(asFan.getResponse().getStatus()).isEqualTo(400);

        // The store refreshing its own token: 200, still ecommerce.
        JsonNode refreshed = refreshStore(storeRefresh, 200);
        assertThat(claim(refreshed.get("access_token").asText(), "tenant_id")).isEqualTo("ecommerce");
        String rotated = refreshed.get("refresh_token").asText();
        assertThat(refreshTokenRepository.findByJti(rotated).orElseThrow().getTenantId()).isEqualTo("ecommerce");

        // The comparison still bites: a row of another site is refused.
        jdbcTemplate.update("UPDATE refresh_tokens SET tenant_id = 'fan-platform' WHERE jti = ?", rotated);
        JsonNode mismatch = refreshStore(rotated, 400);
        assertThat(mismatch.get("error").asText()).isEqualTo("invalid_grant");
        assertThat(mismatch.get("error_description").asText()).isEqualTo("TOKEN_TENANT_MISMATCH");
    }

    // ── AC-3 ──────────────────────────────────────────────────────────────────────────────────

    /**
     * TASK-MONO-772 S4 — 🔴 AC-2 (the closed path, asserted at the token endpoint). Strengthened from the
     * TASK-BE-615 cell of the same name: the refusal text is asserted byte-for-byte (the console's
     * {@code sso_wrong_account} discriminator reads it) and admin-service was actually ASKED — so the 400 is the
     * «no facet» answer, not an outage that happens to look the same.
     */
    @Test
    @DisplayName("🔴 AC-2: 운영자 측면 없는 풀 계정 → 콘솔: 재로그인 없이 코드, 토큰은 400 invalid_grant · BE-614 문구 바이트 불변 · admin 에 물었다")
    void poolSession_onConsole_noConsoleToken() throws Exception {
        MockHttpSession session = loginThrough(BOTH_EMAIL, STORE_CLIENT_ID, STORE_REDIRECT_URI, Pkce.create());

        Pkce consolePkce = Pkce.create();
        String code = authorizeExpectingCode(session, CONSOLE_CLIENT_ID, CONSOLE_REDIRECT_URI, consolePkce);
        JsonNode error = exchange(code, CONSOLE_CLIENT_ID, CONSOLE_REDIRECT_URI, consolePkce, 400);
        assertThat(error.get("error").asText()).isEqualTo("invalid_grant");
        assertThat(error.get("error_description").asText()).isEqualTo(NO_FACET_TEXT);
        assertThat(error.has("access_token")).isFalse();
        adminService.verify(WireMock.getRequestedFor(WireMock.urlPathEqualTo("/internal/operators/console-eligibility"))
                .withQueryParam("accountId", WireMock.equalTo(BOTH)));
    }

    @Test
    @DisplayName("AC-3: 콘솔 폼 로그인의 교차 조회 — 이메일이 풀 한 행뿐이면 그 자격으로 로그인된다(LOGIN_TENANT_AMBIGUOUS 아님), 토큰은 400")
    void consoleFormLogin_poolOnlyEmail_resolvesButNoToken() throws Exception {
        Pkce consolePkce = Pkce.create();
        MockHttpSession session = loginThrough(BOTH_EMAIL, CONSOLE_CLIENT_ID, CONSOLE_REDIRECT_URI, consolePkce);

        String code = authorizeExpectingCode(session, CONSOLE_CLIENT_ID, CONSOLE_REDIRECT_URI, consolePkce);
        JsonNode error = exchange(code, CONSOLE_CLIENT_ID, CONSOLE_REDIRECT_URI, consolePkce, 400);
        assertThat(error.get("error").asText()).isEqualTo("invalid_grant");
        assertThat(error.get("error_description").asText()).isEqualTo(NO_FACET_TEXT);
    }

    // ── TASK-MONO-772 S4 — a console token only with a live operator facet ─────────────────────────

    /** The TASK-BE-614 refusal text — byte-for-byte (auth-api.md § 풀 계정의 콘솔 토큰, «측면 없음» row). */
    private static final String NO_FACET_TEXT =
            "tenant_id 'consumer-pool' is a reserved storage value and is never issued";

    /**
     * AC-2's control (same test class, same path) and AC-3: a faceted pool operator gets the console token
     * (tenant {@code iam}, its own {@code sub}, no roles) and refreshes it; after the operator row is suspended
     * the NEXT refresh is refused with the «no facet» text, while the same account's store session is untouched.
     */
    @Test
    @DisplayName("772 AC-2 대조군 · AC-3: 측면 있는 풀 운영자 → 콘솔 토큰(iam · sub=풀 계정 · roles 없음) → refresh 200(iam) → 측면 회수 → 다음 refresh 400(BE-614 문구) · 같은 계정 스토어 refresh 는 200")
    void facetedPoolOperator_consoleToken_thenRevoked_refreshRefused_storeUnaffected() throws Exception {
        Pkce storePkce = Pkce.create();
        MockHttpSession session = loginThrough(OPERATOR_EMAIL, STORE_CLIENT_ID, STORE_REDIRECT_URI, storePkce);
        JsonNode storeTokens = exchange(authorizeExpectingCode(session, STORE_CLIENT_ID, STORE_REDIRECT_URI, storePkce),
                STORE_CLIENT_ID, STORE_REDIRECT_URI, storePkce, 200);

        // Console: single sign-on (no /login — no iam credential, BE-610), and a console token.
        Pkce consolePkce = Pkce.create();
        JsonNode console = exchange(authorizeExpectingCode(session, CONSOLE_CLIENT_ID, CONSOLE_REDIRECT_URI, consolePkce),
                CONSOLE_CLIENT_ID, CONSOLE_REDIRECT_URI, consolePkce, 200);
        String consoleAccess = console.get("access_token").asText();
        assertThat(claim(consoleAccess, "tenant_id")).isEqualTo("iam").isNotEqualTo("consumer-pool");
        assertThat(claim(consoleAccess, "tenant_type")).isEqualTo("B2B_ENTERPRISE");
        assertThat(claim(consoleAccess, "sub")).isEqualTo(OPERATOR);
        assertThat(roles(consoleAccess)).isEmpty();
        assertThat(claim(console.get("id_token").asText(), "tenant_id")).isEqualTo("iam");
        String consoleRefresh = console.get("refresh_token").asText();
        assertThat(refreshTokenRepository.findByJti(consoleRefresh).orElseThrow().getTenantId())
                .as("the mirror row carries the claim — iam").isEqualTo("iam");

        // Refresh while the facet is live: 200, still iam (the session-tenant comparison answers iam).
        JsonNode refreshed = refreshConsole(consoleRefresh, 200);
        assertThat(claim(refreshed.get("access_token").asText(), "tenant_id")).isEqualTo("iam");
        String rotated = refreshed.get("refresh_token").asText();

        // The operator leaves (row suspended): the next console refresh gets no token.
        adminService.setScenarioState(FACET, "REVOKED");
        JsonNode refused = refreshConsole(rotated, 400);
        assertThat(refused.get("error").asText()).isEqualTo("invalid_grant");
        assertThat(refused.get("error_description").asText()).isEqualTo(NO_FACET_TEXT);

        // ADR-MONO-080 D5 — the store session of the same pool account is untouched by the revocation.
        JsonNode store = refreshStore(storeTokens.get("refresh_token").asText(), 200);
        assertThat(claim(store.get("access_token").asText(), "tenant_id")).isEqualTo("ecommerce");
        assertThat(claim(store.get("access_token").asText(), "sub")).isEqualTo(OPERATOR);
        // admin-service was asked on every console issuance (code + id token, refresh, refused refresh).
        adminService.verify(WireMock.moreThanOrExactly(3),
                WireMock.getRequestedFor(WireMock.urlPathEqualTo("/internal/operators/console-eligibility"))
                        .withQueryParam("accountId", WireMock.equalTo(OPERATOR)));
    }

    @Test
    @DisplayName("772 F5: 판정을 못 받음(admin 503) → 400 invalid_grant · error_description=operator_eligibility_unavailable(값 전체) · 'consumer-pool' 미포함")
    void eligibilityUnanswered_distinctRefusal() throws Exception {
        MockHttpSession session = loginThrough(UNANSWERED_EMAIL, STORE_CLIENT_ID, STORE_REDIRECT_URI, Pkce.create());

        Pkce consolePkce = Pkce.create();
        String code = authorizeExpectingCode(session, CONSOLE_CLIENT_ID, CONSOLE_REDIRECT_URI, consolePkce);
        JsonNode error = exchange(code, CONSOLE_CLIENT_ID, CONSOLE_REDIRECT_URI, consolePkce, 400);
        assertThat(error.get("error").asText()).isEqualTo("invalid_grant");
        assertThat(error.get("error_description").asText())
                .isEqualTo("operator_eligibility_unavailable").doesNotContain("consumer-pool");
    }

    @Test
    @DisplayName("772 S1-1 · BE-610: 풀 세션인데 같은 이메일에 iam 자격 → 콘솔 authorize 는 재인증(/login) · 코드 없음 · admin 에 묻지 않는다")
    void dualCredential_poolSession_consoleReauthenticates() throws Exception {
        MockHttpSession session = loginThrough(DUAL_EMAIL, STORE_CLIENT_ID, STORE_REDIRECT_URI, Pkce.create());

        MvcResult console = mockMvc.perform(authorize(session, CONSOLE_CLIENT_ID, CONSOLE_REDIRECT_URI, Pkce.create()))
                .andExpect(status().is3xxRedirection()).andReturn();

        assertThat(console.getResponse().getHeader("Location")).endsWith("/login").doesNotContain("code=");
        adminService.verify(0, WireMock.getRequestedFor(
                WireMock.urlPathEqualTo("/internal/operators/console-eligibility")));
    }

    // ── AC-4 (owner decision: logout ends the WHOLE IAM session) ──────────────────────────────

    @Test
    @DisplayName("AC-4: 스토어에서 RP 로그아웃(id_token_hint) → IAM 세션 종료 → 팬 authorize 는 /login · 이미 받은 팬 refresh 는 그 사이트 만료까지 유효")
    void logoutFromStore_endsIamSession_fanAppSessionFollowsItsOwnExpiry() throws Exception {
        Pkce fanPkce = Pkce.create();
        MockHttpSession session = loginThrough(BOTH_EMAIL, FAN_CLIENT_ID, FAN_REDIRECT_URI, fanPkce);
        JsonNode fanTokens = exchange(authorizeExpectingCode(session, FAN_CLIENT_ID, FAN_REDIRECT_URI, fanPkce),
                FAN_CLIENT_ID, FAN_REDIRECT_URI, fanPkce, 200);
        Pkce storePkce = Pkce.create();
        JsonNode storeTokens = exchange(
                authorizeExpectingCode(session, STORE_CLIENT_ID, STORE_REDIRECT_URI, storePkce),
                STORE_CLIENT_ID, STORE_REDIRECT_URI, storePkce, 200);

        mockMvc.perform(get("/connect/logout")
                        .session(session)
                        .queryParam("id_token_hint", storeTokens.get("id_token").asText()))
                .andExpect(status().is3xxRedirection());
        assertThat(session.isInvalid()).as("the IAM browser session is gone").isTrue();

        // The other site's NEXT authorize needs a login again — no silent SSO after a logout.
        MvcResult fanAgain = mockMvc.perform(authorize(new MockHttpSession(), FAN_CLIENT_ID, FAN_REDIRECT_URI,
                        Pkce.create()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(fanAgain.getResponse().getHeader("Location")).endsWith("/login");

        // What logout does NOT do (the documented scope): the fan app's own session — its refresh
        // token — follows that site's expiry / its own logout.
        MvcResult fanRefresh = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", fanTokens.get("refresh_token").asText())
                        .param("client_id", FAN_CLIENT_ID))
                .andReturn();
        assertThat(fanRefresh.getResponse().getStatus()).isEqualTo(200);
    }

    // -----------------------------------------------------------------------
    // Helpers — the browser path (SsoTenantGateIntegrationTest shape)
    // -----------------------------------------------------------------------

    private record Pkce(String verifier, String challenge) {
        static Pkce create() throws Exception {
            String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    UUID.randomUUID().toString().replace("-", "").getBytes(StandardCharsets.UTF_8));
            String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
            return new Pkce(verifier, challenge);
        }
    }

    private MockHttpServletRequestBuilder authorize(MockHttpSession session, String clientId, String redirectUri,
                                                    Pkce pkce) {
        var builder = get("/oauth2/authorize")
                .accept(MediaType.TEXT_HTML)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", "openid profile email")
                .queryParam("code_challenge", pkce.challenge())
                .queryParam("code_challenge_method", "S256")
                .queryParam("state", "be-615");
        return session != null ? builder.session(session) : builder;
    }

    private MockHttpSession loginThrough(String email, String clientId, String redirectUri, Pkce pkce)
            throws Exception {
        MvcResult start = mockMvc.perform(authorize(null, clientId, redirectUri, pkce))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(start.getResponse().getHeader("Location")).endsWith("/login");
        MockHttpSession saved = (MockHttpSession) start.getRequest().getSession(false);

        MvcResult login = mockMvc.perform(post("/login")
                        .session(saved)
                        .with(csrf())
                        .param("username", email)
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(login.getResponse().getRedirectedUrl())
                .as("the pool credential logs in and resumes the saved authorize")
                .contains("/oauth2/authorize")
                .contains("client_id=" + clientId);
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private String authorizeExpectingCode(MockHttpSession session, String clientId, String redirectUri,
                                          Pkce pkce) throws Exception {
        MvcResult result = mockMvc.perform(authorize(session, clientId, redirectUri, pkce))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = result.getResponse().getHeader("Location");
        assertThat(location).as("a code, not a /login bounce").startsWith(redirectUri).contains("code=");
        return queryParam(location, "code");
    }

    /** TASK-BE-616 — the authorize answers with the first-visit consent page, not a code and not /login. */
    private String authorizeExpectingConsent(MockHttpSession session, String clientId, String redirectUri,
                                             Pkce pkce) throws Exception {
        MvcResult result = mockMvc.perform(authorize(session, clientId, redirectUri, pkce))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = result.getResponse().getHeader("Location");
        assertThat(location).as("the consent page — not a code, not a /login bounce").endsWith("/consent");
        return location;
    }

    private JsonNode exchange(String code, String clientId, String redirectUri, Pkce pkce, int expectedStatus)
            throws Exception {
        var request = post("/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code")
                .param("code", code)
                .param("redirect_uri", redirectUri)
                .param("code_verifier", pkce.verifier());
        if (STORE_CLIENT_ID.equals(clientId)) {
            request.header("Authorization", basicStore());
        } else {
            request.param("client_id", clientId);
        }
        MvcResult token = mockMvc.perform(request).andReturn();
        assertThat(token.getResponse().getStatus())
                .as("token status — body: " + token.getResponse().getContentAsString())
                .isEqualTo(expectedStatus);
        return objectMapper.readTree(token.getResponse().getContentAsString());
    }

    private JsonNode refreshStore(String refreshToken, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header("Authorization", basicStore())
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", refreshToken))
                .andReturn();
        assertThat(result.getResponse().getStatus())
                .as("refresh status — body: " + result.getResponse().getContentAsString())
                .isEqualTo(expectedStatus);
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode refreshConsole(String refreshToken, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", refreshToken)
                        .param("client_id", CONSOLE_CLIENT_ID))
                .andReturn();
        assertThat(result.getResponse().getStatus())
                .as("console refresh status — body: " + result.getResponse().getContentAsString())
                .isEqualTo(expectedStatus);
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String basicStore() {
        return "Basic " + Base64.getEncoder().encodeToString(
                (STORE_CLIENT_ID + ":" + STORE_CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode payload(String jwt) throws Exception {
        return objectMapper.readTree(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]));
    }

    private String claim(String jwt, String name) throws Exception {
        JsonNode value = payload(jwt).get(name);
        return value == null ? null : value.asText();
    }

    private List<String> roles(String jwt) throws Exception {
        List<String> roles = new ArrayList<>();
        JsonNode node = payload(jwt).get("roles");
        if (node != null) {
            node.forEach(r -> roles.add(r.asText()));
        }
        return roles;
    }

    private static String queryParam(String url, String name) {
        String query = url.substring(url.indexOf('?') + 1);
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }
}
