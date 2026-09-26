package com.example.auth.integration;

import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.credentials.CredentialHash;
import com.example.auth.domain.repository.RefreshTokenRepository;
import com.example.auth.domain.session.PrincipalDetailKeys;
import com.example.auth.domain.token.RefreshToken;
import com.example.auth.infrastructure.persistence.CredentialJpaEntity;
import com.example.auth.infrastructure.persistence.CredentialJpaRepository;
import com.example.testsupport.integration.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-606 — replay of a refresh token that was ROTATED AWAY, end to end at
 * {@code POST /oauth2/token}.
 *
 * <p>Before BE-606 this shape never reached reuse detection: SAS keeps only the authorization's
 * current refresh token, so {@code findByToken(A)} was null and the provider answered
 * {@code invalid_grant} with nothing revoked and no event ({@code OAuth2RefreshTokenIntegrationTest}
 * {@code @Order(4)} observed that 400 and read it as reuse detection).
 *
 * <p>Owner decision 2026-09-26 (option B, N = 30 s): within 30 s of the rotation, while the child
 * is the only child and still the chain head, the replay is a client race → 400, nothing revoked.
 * Otherwise it is reuse → the account's family is revoked (mirror rows + SAS authorizations) and
 * {@code auth.token.reuse.detected} is written to the outbox.
 *
 * <p><b>Clock control.</b> The 30 s is not slept through: the child row's {@code issued_at} is
 * moved back with one UPDATE — the policy measures the window from exactly that column.
 *
 * <p>Event evidence = the {@code auth_outbox} row (the transactional write the relay publishes
 * from), counted per account — the relay marks rows published, it does not delete them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SasRefreshTokenReplayIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    static WireMockServer wireMock;

    private static final String CLIENT_ID = "demo-spa-client";
    private static final String REDIRECT_URI = "http://localhost:3000/callback";
    private static final String REUSE_TOPIC = "auth.token.reuse.detected";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        registry.add("auth.account-service.base-url", wireMock::baseUrl);
    }

    @AfterAll
    static void teardown() {
        if (wireMock != null && wireMock.isRunning()) {
            wireMock.stop();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private CredentialJpaRepository credentialJpaRepository;
    @Autowired private OAuth2AuthorizationService oAuth2AuthorizationService;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    // Same hermetic replacement as OAuth2RefreshTokenIntegrationTest (TASK-BE-318c).
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.example.security.oauth2.client.IamClientCredentialsTokenProvider gapTokenProvider;

    @BeforeEach
    void stubIamClientCredentialsToken() {
        org.mockito.Mockito.when(gapTokenProvider.currentBearer()).thenReturn("test-jwt");
    }

    // -----------------------------------------------------------------------
    // AC-1 — replay of A after the grace window = reuse
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("BE-606 AC-1: 로그인 → refresh(A→B) → 30s 경과 후 A 재제출 → 400 · 계정의 다른 SAS 세션 refresh 거부 "
            + "· SAS 인가 무효화 · auth.token.reuse.detected 정확히 1건 (재차 재제출해도 1건)")
    void replayOfRotatedToken_afterGrace_revokesFamilyAndEmitsOnce() throws Exception {
        String accountId = UUID.randomUUID().toString();
        String email = "replay-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        // A credential makes the SAS revocation port able to find the account's authorizations
        // (principal name = the login email) — the leg the mirror-row revoke cannot reach.
        credentialJpaRepository.save(CredentialJpaEntity.fromDomain(Credential.create(
                accountId, "fan-platform", email, CredentialHash.argon2id("unused"), Instant.now())));

        String tokenA = signIn(email, accountId);
        String otherSession = signIn(email, accountId);
        String tokenB = refreshOk(tokenA);
        backdateIssuedAt(tokenB, 60);
        assertThat(reuseEvents(accountId)).as("control: no reuse event before the replay").isZero();

        refresh(tokenA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"))
                .andExpect(jsonPath("$.error_description").value(
                        org.hamcrest.Matchers.containsString("reuse detected")));

        assertThat(reuseEvents(accountId))
                .as("exactly one auth.token.reuse.detected for the account")
                .isEqualTo(1);
        // The family and the account's OTHER session are closed in BOTH stores.
        assertThat(refreshTokenRepository.findByJti(tokenB).orElseThrow().isRevoked()).isTrue();
        assertThat(refreshTokenRepository.findByJti(otherSession).orElseThrow().isRevoked()).isTrue();
        OAuth2Authorization other = oAuth2AuthorizationService.findByToken(
                otherSession, OAuth2TokenType.REFRESH_TOKEN);
        assertThat(other).isNotNull();
        assertThat(other.getRefreshToken().isInvalidated())
                .as("the SAS authorization itself was invalidated (OAuthAuthorizationRevocationPort) — "
                        + "not only its mirror row")
                .isTrue();
        refresh(otherSession)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
        refresh(tokenB)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));

        // A second replay of the same dead family revokes nothing → not announced again, so one
        // stolen token replayed twice cannot by itself count as two reuse events.
        refresh(tokenA).andExpect(status().isBadRequest());
        assertThat(reuseEvents(accountId)).isEqualTo(1);
    }

    // -----------------------------------------------------------------------
    // AC-2 — the race the grace window exists for, and the forked-chain state
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("BE-606 AC-2: 회전 직후(유예 30s 안) A 재제출 → 400 invalid_grant · 폐기 없음 · 이벤트 없음 · B 는 계속 refresh(200)")
    void replayOfRotatedToken_withinGrace_refusedButWinnerSurvives() throws Exception {
        String accountId = UUID.randomUUID().toString();
        String email = "grace-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";

        String tokenA = signIn(email, accountId);
        String tokenB = refreshOk(tokenA);

        MvcResult replay = refresh(tokenA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"))
                .andReturn();
        assertThat(replay.getResponse().getContentAsString()).doesNotContain("reuse detected");

        assertThat(reuseEvents(accountId)).isZero();
        assertThat(refreshTokenRepository.findByJti(tokenB).orElseThrow().isRevoked())
                .as("the winner's token was not revoked")
                .isFalse();
        String tokenC = refreshOk(tokenB);
        assertThat(tokenC).isNotBlank().isNotEqualTo(tokenB);
    }

    @Test
    @DisplayName("BE-606 AC-2: 자식 둘(동시 refresh 가 둘 다 통과한 상태) → 500 아닌 400 · 유예 안이어도 재사용 · 이벤트 1건")
    void replayOfTokenWithTwoChildren_isReuseNotServerError() throws Exception {
        String accountId = UUID.randomUUID().toString();
        String email = "fork-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";

        String tokenA = signIn(email, accountId);
        refreshOk(tokenA);
        // The second child a concurrent refresh that also read before the first committed would
        // have written (rotated_from is not unique — V0001).
        Instant now = Instant.now();
        transactionTemplate.executeWithoutResult(s -> refreshTokenRepository.save(RefreshToken.create(
                "fork-" + UUID.randomUUID(), accountId, "fan-platform",
                now, now.plusSeconds(3600), tokenA, null, null)));

        refresh(tokenA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"))
                .andExpect(jsonPath("$.error_description").value(
                        org.hamcrest.Matchers.containsString("reuse detected")));
        assertThat(reuseEvents(accountId)).isEqualTo(1);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Moves the row's {@code issued_at} back RELATIVE to its own value. Binding an absolute
     * {@code Timestamp} here would go through the JVM default zone while Hibernate reads the
     * column as UTC — on a KST host that lands the row 9 h in the FUTURE (inside the window).
     */
    private void backdateIssuedAt(String jti, long seconds) {
        int updated = jdbcTemplate.update(
                "UPDATE refresh_tokens SET issued_at = issued_at - INTERVAL ? SECOND WHERE jti = ?",
                seconds, jti);
        assertThat(updated).as("the child row to backdate exists").isEqualTo(1);
    }

    private int reuseEvents(String accountId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_outbox WHERE event_type = ? AND aggregate_id = ?",
                Integer.class, REUSE_TOPIC, accountId);
        return n == null ? 0 : n;
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "refresh_token")
                .param("refresh_token", refreshToken)
                .param("client_id", CLIENT_ID));
    }

    private String refreshOk(String refreshToken) throws Exception {
        MvcResult result = refresh(refreshToken).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("refresh_token").asText();
    }

    /** authorization_code + PKCE with the principal shape the form/social login paths build. */
    private String signIn(String email, String accountId) throws Exception {
        Map<String, Object> details = new HashMap<>();
        details.put(PrincipalDetailKeys.TENANT_ID, "fan-platform");
        details.put(PrincipalDetailKeys.TENANT_TYPE, "B2C");
        details.put(PrincipalDetailKeys.ACCOUNT_ID, accountId);
        details.put(PrincipalDetailKeys.EMAIL, email);
        UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        principal.setDetails(details);

        String codeVerifier = Base64.getUrlEncoder().withoutPadding().encodeToString(
                UUID.randomUUID().toString().replace("-", "").getBytes(StandardCharsets.UTF_8));
        String codeChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(codeVerifier.getBytes(StandardCharsets.US_ASCII)));

        MvcResult authorize = mockMvc.perform(get("/oauth2/authorize")
                        .with(authentication(principal))
                        .queryParam("response_type", "code")
                        .queryParam("client_id", CLIENT_ID)
                        .queryParam("redirect_uri", REDIRECT_URI)
                        .queryParam("scope", "openid profile email")
                        .queryParam("code_challenge", codeChallenge)
                        .queryParam("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String code = extractParam(authorize.getResponse().getHeader("Location"), "code");
        assertThat(code).isNotNull();

        MvcResult token = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("client_id", CLIENT_ID)
                        .param("code_verifier", codeVerifier))
                .andExpect(status().isOk())
                .andReturn();
        String refreshToken = objectMapper.readTree(token.getResponse().getContentAsString())
                .get("refresh_token").asText();
        // The initial-issuance mirror INSERT swallows its failures — make sure it landed, so the
        // assertions on "the other session's row" above test what they claim.
        assertThat(refreshTokenRepository.findByJti(refreshToken)).isPresent();
        return refreshToken;
    }

    private static String extractParam(String url, String paramName) {
        String query = url.contains("?") ? url.substring(url.indexOf("?") + 1) : url;
        for (String param : query.split("&")) {
            if (param.startsWith(paramName + "=")) {
                return param.substring(paramName.length() + 1);
            }
        }
        return null;
    }
}
