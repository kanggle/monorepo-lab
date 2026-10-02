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
 * TASK-BE-618 (ADR-MONO-078 A; auth-internal.md § POST /internal/auth/consumer-pool/moves) — the auth_db half of
 * moving a single-site account into the consumer pool, against a real MySQL and the real browser path.
 *
 * <ul>
 *   <li>The move rewrites {@code credentials.tenant_id} ONLY — the account's {@code refresh_tokens} mirror rows
 *       keep the site (the session tenant; task 정정 ①) and social identities are never touched.</li>
 *   <li>AC-3: after the move the same email + password on the SAME site client logs in as a pool principal and
 *       the token has the same {@code sub} and {@code tenant_id} = the site; a refresh token issued BEFORE the
 *       move still refreshes (no {@code TOKEN_TENANT_MISMATCH}).</li>
 *   <li>Refusals write nothing: social identities (409), operator facet (409), admin-service unable to answer
 *       (503, fail-closed). Re-running a done move is 200 {@code alreadyInPool} (idempotent).</li>
 * </ul>
 *
 * <p>One WireMock plays both account-service (status / roles / consumer-members — what account-service
 * answers after its half of the move: an ACTIVE fan membership with {@code [ARTIST, FAN]}, and the widened
 * roles read with the same set) and admin-service ({@code GET /internal/operators/facet}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConsumerPoolLegacyMoveIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    static WireMockServer downstream;

    private static final String PASSWORD = "LegacyMovePassw0rd!";
    private static final String MOVER = "0199de70-0000-7000-8000-0000000a0618";
    private static final String MOVER_EMAIL = "legacy-fan-618@example.com";
    private static final String MOVER_IDENTITY = "0199de71-0000-7000-8000-0000000a0618";
    private static final String SOCIAL = "0199de70-0000-7000-8000-0000000b0618";
    private static final String OPERATOR = "0199de70-0000-7000-8000-0000000c0618";
    private static final String ADMIN_DOWN = "0199de70-0000-7000-8000-0000000d0618";
    private static final String NO_CREDENTIAL = "0199de70-0000-7000-8000-0000000e0618";
    private static final String FAN = "fan-platform";

    private static final String FAN_CLIENT_ID = "demo-spa-client";
    private static final String FAN_REDIRECT_URI = "http://localhost:3000/callback";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

        downstream = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        downstream.start();
        registry.add("auth.account-service.base-url", downstream::baseUrl);
        registry.add("auth.admin-service.base-url", downstream::baseUrl);

        downstream.stubFor(WireMock.get(WireMock.urlPathEqualTo("/internal/accounts/" + MOVER + "/status"))
                .willReturn(json("""
                        { "accountId": "%s", "status": "ACTIVE", "statusChangedAt": "2026-10-01T00:00:00Z" }
                        """.formatted(MOVER))));
        // Site principal (before the move, and a pre-move session after it — the widened roles read, 정정 ⑤).
        downstream.stubFor(WireMock.get(WireMock.urlPathEqualTo(
                        "/internal/tenants/fan-platform/accounts/" + MOVER + "/roles"))
                .willReturn(json("""
                        { "accountId": "%s", "tenantId": "fan-platform", "roles": ["ARTIST", "FAN"] }
                        """.formatted(MOVER))));
        // Pool principal (after the move): account-service has the ACTIVE fan membership + the moved roles.
        downstream.stubFor(WireMock.get(WireMock.urlPathEqualTo(
                        "/internal/tenants/fan-platform/consumer-members/" + MOVER))
                .willReturn(json("""
                        { "accountId": "%s", "siteTenantId": "fan-platform", "consumerSite": true,
                          "siteTenantType": "B2C_CONSUMER", "membershipStatus": "ACTIVE",
                          "siteRoles": ["ARTIST", "FAN"] }
                        """.formatted(MOVER))));
        // admin-service operator facet.
        facet(MOVER, "false");
        facet(SOCIAL, "false");
        facet(OPERATOR, "true");
        downstream.stubFor(WireMock.get(WireMock.urlPathEqualTo("/internal/operators/facet"))
                .withQueryParam("accountId", WireMock.equalTo(ADMIN_DOWN))
                .willReturn(WireMock.aResponse().withStatus(500)));
    }

    private static void facet(String accountId, String faceted) {
        downstream.stubFor(WireMock.get(WireMock.urlPathEqualTo("/internal/operators/facet"))
                .withQueryParam("accountId", WireMock.equalTo(accountId))
                .willReturn(json("{ \"operatorFaceted\": " + faceted + " }")));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String body) {
        return WireMock.aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }

    @AfterAll
    static void stopDownstream() {
        if (downstream != null && downstream.isRunning()) {
            downstream.stop();
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
    void seedSiteCredentials() {
        Mockito.when(gapTokenProvider.currentBearer()).thenReturn("test-jwt");
        downstream.resetRequests();
        credentialJpaRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM social_identities WHERE account_id IN (?, ?, ?, ?)",
                MOVER, SOCIAL, OPERATOR, ADMIN_DOWN);
        String hash = new Argon2idPasswordHasher().hash(PASSWORD);
        for (String[] row : List.of(
                new String[]{MOVER, MOVER_EMAIL},
                new String[]{SOCIAL, "legacy-social-618@example.com"},
                new String[]{OPERATOR, "legacy-operator-618@example.com"},
                new String[]{ADMIN_DOWN, "legacy-admin-down-618@example.com"})) {
            // The pre-ADR-MONO-078 shape: a credential in the SITE tenant.
            credentialJpaRepository.save(CredentialJpaEntity.fromDomain(Credential.create(
                    row[0], FAN, row[1], CredentialHash.argon2id(hash), Instant.now())));
        }
        jdbcTemplate.update("UPDATE credentials SET identity_id = ? WHERE account_id = ?", MOVER_IDENTITY, MOVER);
    }

    // ── the move endpoint ────────────────────────────────────────────────────────────────────────

    private MvcResult move(String accountId) throws Exception {
        return mockMvc.perform(post("/internal/auth/consumer-pool/moves")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"accountId\": \"" + accountId + "\", \"siteTenantId\": \"fan-platform\" }"))
                .andReturn();
    }

    private String credentialTenant(String accountId) {
        return jdbcTemplate.queryForObject("SELECT tenant_id FROM credentials WHERE account_id = ?",
                String.class, accountId);
    }

    @Test
    @DisplayName("AC-3: 이동 전 로그인(사이트 principal) → 이동(자격만) → refresh 행은 그대로 · 이동 전 refresh 가 계속 된다 · 같은 비밀번호로 다시 로그인하면 같은 sub · tenant=사이트 · 재이동은 alreadyInPool")
    void move_credentialOnly_preMoveRefreshWorks_sameSubAfterLogin() throws Exception {
        // 1. Before the move: an ordinary fan site login.
        Pkce before = Pkce.create();
        MockHttpSession oldSession = loginThrough(MOVER_EMAIL, before);
        JsonNode oldTokens = exchange(authorizeExpectingCode(oldSession, before), before, 200);
        String oldAccess = oldTokens.get("access_token").asText();
        assertThat(claim(oldAccess, "sub")).isEqualTo(MOVER);
        assertThat(claim(oldAccess, "tenant_id")).isEqualTo(FAN);
        assertThat(roles(oldAccess)).containsExactlyInAnyOrder("ARTIST", "FAN");
        String oldRefresh = oldTokens.get("refresh_token").asText();
        assertThat(refreshTokenRepository.findByJti(oldRefresh).orElseThrow().getTenantId()).isEqualTo(FAN);

        // 2. The move: credentials only.
        MvcResult moved = move(MOVER);
        assertThat(moved.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(moved.getResponse().getContentAsString());
        assertThat(body.get("moved").asBoolean()).isTrue();
        assertThat(body.get("alreadyInPool").asBoolean()).isFalse();
        assertThat(credentialTenant(MOVER)).isEqualTo("consumer-pool");
        assertThat(jdbcTemplate.queryForObject("SELECT identity_id FROM credentials WHERE account_id = ?",
                String.class, MOVER)).as("same identity link").isEqualTo(MOVER_IDENTITY);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE account_id = ? AND tenant_id <> ?",
                Integer.class, MOVER, FAN))
                .as("정정 ①: refresh mirror rows keep the session (site) tenant — never moved").isZero();
        downstream.verify(WireMock.getRequestedFor(WireMock.urlPathEqualTo("/internal/operators/facet"))
                .withQueryParam("accountId", WireMock.equalTo(MOVER))
                .withQueryParam("identityId", WireMock.equalTo(MOVER_IDENTITY)));

        // 3. The session from before the move keeps refreshing — no TOKEN_TENANT_MISMATCH.
        JsonNode refreshed = refreshFan(oldRefresh, 200);
        String refreshedAccess = refreshed.get("access_token").asText();
        assertThat(claim(refreshedAccess, "sub")).isEqualTo(MOVER);
        assertThat(claim(refreshedAccess, "tenant_id")).isEqualTo(FAN);
        assertThat(roles(refreshedAccess)).containsExactlyInAnyOrder("ARTIST", "FAN");

        // 4. A new login with the same password on the same site: the pool credential, same sub, site tenant.
        Pkce after = Pkce.create();
        MockHttpSession newSession = loginThrough(MOVER_EMAIL, after);
        JsonNode newTokens = exchange(authorizeExpectingCode(newSession, after), after, 200);
        String newAccess = newTokens.get("access_token").asText();
        assertThat(claim(newAccess, "sub")).as("AC-3: the same account — fan data stays reachable").isEqualTo(MOVER);
        assertThat(claim(newAccess, "tenant_id")).isEqualTo(FAN).isNotEqualTo("consumer-pool");
        assertThat(roles(newAccess)).contains("FAN", "ARTIST");
        downstream.verify(WireMock.getRequestedFor(WireMock.urlPathEqualTo(
                "/internal/tenants/fan-platform/consumer-members/" + MOVER)));

        // 5. Idempotent re-run (the «auth committed, account rolled back» window closes on the next run).
        MvcResult again = move(MOVER);
        assertThat(again.getResponse().getStatus()).isEqualTo(200);
        JsonNode againBody = objectMapper.readTree(again.getResponse().getContentAsString());
        assertThat(againBody.get("moved").asBoolean()).isFalse();
        assertThat(againBody.get("alreadyInPool").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("소셜 신원 있음 → 409 POOL_MOVE_SOCIAL_LINKED · 자격·소셜 행 무변경")
    void socialLinked_409_nothingChanged() throws Exception {
        jdbcTemplate.update("INSERT INTO social_identities "
                + "(account_id, tenant_id, provider, provider_user_id, provider_email, connected_at, last_used_at) "
                + "VALUES (?, ?, 'google', ?, NULL, NOW(6), NOW(6))", SOCIAL, FAN, "g-" + UUID.randomUUID());

        MvcResult result = move(SOCIAL);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asText())
                .isEqualTo("POOL_MOVE_SOCIAL_LINKED");
        assertThat(credentialTenant(SOCIAL)).isEqualTo(FAN);
        assertThat(jdbcTemplate.queryForObject("SELECT tenant_id FROM social_identities WHERE account_id = ?",
                String.class, SOCIAL)).isEqualTo(FAN);
    }

    @Test
    @DisplayName("운영자 측면(admin facet=true) → 409 POOL_MOVE_OPERATOR_FACETED · 자격 무변경")
    void operatorFaceted_409_nothingChanged() throws Exception {
        MvcResult result = move(OPERATOR);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asText())
                .isEqualTo("POOL_MOVE_OPERATOR_FACETED");
        assertThat(credentialTenant(OPERATOR)).isEqualTo(FAN);
    }

    @Test
    @DisplayName("admin-service 가 답하지 못함(500) → 503 SERVICE_UNAVAILABLE · 자격 무변경 (fail-closed)")
    void adminDown_503_nothingChanged() throws Exception {
        MvcResult result = move(ADMIN_DOWN);

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asText())
                .isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(credentialTenant(ADMIN_DOWN)).isEqualTo(FAN);
    }

    @Test
    @DisplayName("자격 없는 계정 → 200 {moved:false, alreadyInPool:false} · admin 질문 없음")
    void noCredential_200_nothingToMove() throws Exception {
        MvcResult result = move(NO_CREDENTIAL);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("moved").asBoolean()).isFalse();
        assertThat(body.get("alreadyInPool").asBoolean()).isFalse();
        downstream.verify(0, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/internal/operators/facet")));
    }

    // -----------------------------------------------------------------------
    // Helpers — the browser path (ConsumerPoolSsoIntegrationTest shape), fan client only
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

    private MockHttpServletRequestBuilder authorize(MockHttpSession session, Pkce pkce) {
        var builder = get("/oauth2/authorize")
                .accept(MediaType.TEXT_HTML)
                .queryParam("response_type", "code")
                .queryParam("client_id", FAN_CLIENT_ID)
                .queryParam("redirect_uri", FAN_REDIRECT_URI)
                .queryParam("scope", "openid profile email")
                .queryParam("code_challenge", pkce.challenge())
                .queryParam("code_challenge_method", "S256")
                .queryParam("state", "be-618");
        return session != null ? builder.session(session) : builder;
    }

    private MockHttpSession loginThrough(String email, Pkce pkce) throws Exception {
        MvcResult start = mockMvc.perform(authorize(null, pkce))
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
                .as("the credential logs in and resumes the saved authorize")
                .contains("/oauth2/authorize")
                .contains("client_id=" + FAN_CLIENT_ID);
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private String authorizeExpectingCode(MockHttpSession session, Pkce pkce) throws Exception {
        MvcResult result = mockMvc.perform(authorize(session, pkce))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = result.getResponse().getHeader("Location");
        assertThat(location).as("a code, not a /login bounce").startsWith(FAN_REDIRECT_URI).contains("code=");
        return queryParam(location, "code");
    }

    private JsonNode exchange(String code, Pkce pkce, int expectedStatus) throws Exception {
        MvcResult token = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", FAN_REDIRECT_URI)
                        .param("code_verifier", pkce.verifier())
                        .param("client_id", FAN_CLIENT_ID))
                .andReturn();
        assertThat(token.getResponse().getStatus())
                .as("token status — body: " + token.getResponse().getContentAsString())
                .isEqualTo(expectedStatus);
        return objectMapper.readTree(token.getResponse().getContentAsString());
    }

    private JsonNode refreshFan(String refreshToken, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", refreshToken)
                        .param("client_id", FAN_CLIENT_ID))
                .andReturn();
        assertThat(result.getResponse().getStatus())
                .as("refresh status — body: " + result.getResponse().getContentAsString())
                .isEqualTo(expectedStatus);
        return objectMapper.readTree(result.getResponse().getContentAsString());
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
