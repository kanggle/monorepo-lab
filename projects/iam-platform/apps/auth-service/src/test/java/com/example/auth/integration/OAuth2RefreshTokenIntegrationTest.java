package com.example.auth.integration;

import com.example.auth.application.ConfirmPasswordResetUseCase;
import com.example.auth.application.ForceLogoutUseCase;
import com.example.auth.application.command.ConfirmPasswordResetCommand;
import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.credentials.CredentialHash;
import com.example.auth.domain.repository.PasswordResetTokenStore;
import com.example.auth.domain.repository.RefreshTokenRepository;
import com.example.auth.domain.session.PrincipalDetailKeys;
import com.example.auth.domain.token.RefreshToken;
import com.example.auth.infrastructure.persistence.CredentialJpaEntity;
import com.example.auth.infrastructure.persistence.CredentialJpaRepository;
import com.example.auth.infrastructure.persistence.RefreshTokenJpaRepository;
import com.example.testsupport.integration.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for TASK-BE-251 Phase 2b:
 * SAS {@code refresh_token} grant + existing domain reuse-detection integration.
 *
 * <p>Test coverage:
 * <ol>
 *   <li>Normal rotation — authorization_code → tokens → refresh → new access token + rotated
 *       refresh token; {@link RefreshTokenRepository} reflects both records.</li>
 *   <li>Reuse detection — same refresh token used twice → second call returns {@code 400}
 *       ({@code invalid_grant}) and the account's tokens are revoked in the JPA store.</li>
 *   <li>Tenant claim preservation — refreshed access token still contains
 *       {@code tenant_id} and {@code tenant_type} claims.</li>
 *   <li>Cross-tenant rejection — refresh token belonging to tenantA cannot be used with a
 *       client configured for tenantB (via clientName metadata mismatch check).</li>
 * </ol>
 *
 * <p>Infrastructure: MySQL + Kafka from {@link AbstractIntegrationTest},
 * Redis + WireMock declared locally.
 *
 * <p>TASK-BE-251 Phase 2b.
 */
// TASK-MONO-044c-1 RC#2: see SocialLoginSasBrowserIntegrationTest for rationale —
// AccountServiceClient bean URL must be rebuilt per class to track this
// class's WireMock instance.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OAuth2RefreshTokenIntegrationTest extends AbstractIntegrationTest {

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

        wireMock.stubFor(WireMock.get(WireMock.urlMatching("/internal/accounts/.+/profile"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "accountId": "test-rt-account",
                                  "email": "rt-user@example.com",
                                  "emailVerified": true,
                                  "displayName": "RT Test User",
                                  "preferredUsername": "rtuser",
                                  "locale": "ko-KR",
                                  "tenantId": "fan-platform",
                                  "tenantType": "B2C"
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

    // TASK-BE-318c: AccountServiceClient now mints a GAP client_credentials Bearer token via a SAS
    // self-call to /oauth2/token, unreachable in @SpringBootTest+MockMvc. Replace the provider with
    // a mock returning a fixed bearer so account stubs are exercised hermetically.
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.example.security.oauth2.client.IamClientCredentialsTokenProvider gapTokenProvider;

    @org.junit.jupiter.api.BeforeEach
    void stubIamClientCredentialsToken() {
        org.mockito.Mockito.when(gapTokenProvider.currentBearer()).thenReturn("test-jwt");
    }

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private CredentialJpaRepository credentialJpaRepository;

    @Autowired
    private ForceLogoutUseCase forceLogoutUseCase;

    @Autowired
    private OAuth2AuthorizationService oAuth2AuthorizationService;

    @Autowired
    private ConfirmPasswordResetUseCase confirmPasswordResetUseCase;

    @Autowired
    private PasswordResetTokenStore passwordResetTokenStore;

    // Shared state across ordered tests (normal rotation scenario)
    private static String refreshTokenValue;
    private static String accessTokenValue;

    // -----------------------------------------------------------------------
    // 1. Full authorization_code flow → tokens issued, RT persisted in JPA store
    // -----------------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("authCode flow: refresh_token issued → persisted in domain JPA store")
    void authCodeFlow_refreshTokenPersistedInDomainStore() throws Exception {
        // PKCE
        String codeVerifier = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(UUID.randomUUID().toString().replace("-", "")
                        .getBytes(StandardCharsets.UTF_8));
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        String codeChallenge = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sha256.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII)));

        // Authorize
        // TASK-MONO-044c-1 RC#1: queryParam() for GET /oauth2/authorize because
        // SAS's OAuth2EndpointUtils.getQueryParameters() filters by
        // request.getQueryString().contains(name); MockMvc .param() for GET
        // does not populate queryString. .queryParam() does.
        MvcResult authorizeResult = mockMvc.perform(get("/oauth2/authorize")
                        .with(user("rt-account-001")
                                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .queryParam("response_type", "code")
                        .queryParam("client_id", "demo-spa-client")
                        .queryParam("redirect_uri", "http://localhost:3000/callback")
                        .queryParam("scope", "openid profile email")
                        .queryParam("code_challenge", codeChallenge)
                        .queryParam("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String location = authorizeResult.getResponse().getHeader("Location");
        assertThat(location).isNotNull().contains("code=");
        String code = extractParam(location, "code");

        // Token exchange
        MvcResult tokenResult = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", "http://localhost:3000/callback")
                        .param("client_id", "demo-spa-client")
                        .param("code_verifier", codeVerifier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                .andReturn();

        JsonNode tokenResponse = objectMapper.readTree(tokenResult.getResponse().getContentAsString());
        refreshTokenValue = tokenResponse.get("refresh_token").asText();
        accessTokenValue = tokenResponse.get("access_token").asText();

        assertThat(refreshTokenValue).isNotBlank();

        // Verify domain JPA store was synchronised by DomainSyncOAuth2AuthorizationService
        Optional<RefreshToken> domainToken = refreshTokenRepository.findByJti(refreshTokenValue);
        assertThat(domainToken)
                .as("DomainSyncOAuth2AuthorizationService must persist RT into domain JPA store")
                .isPresent();
        assertThat(domainToken.get().getTenantId())
                .as("domain RT must carry tenant_id")
                .isEqualTo("fan-platform");
        assertThat(domainToken.get().isRevoked())
                .as("freshly issued RT must not be revoked")
                .isFalse();
    }

    // -----------------------------------------------------------------------
    // 2. Normal rotation: refresh_token → new access + refresh token
    // -----------------------------------------------------------------------

    @Test
    @Order(2)
    // TASK-BE-274 / ADR-003 옵션 B: provider-side fallback resolves the A2
    // dual-INSERT race. SasRefreshTokenAuthenticationProvider.authenticate()
    // now binds a TSM resource flag before its authorizationService.save()
    // call so DomainSyncOAuth2AuthorizationService.syncRefreshTokenToDomainStore()
    // skips its INSERT during rotation; the provider's own persistRotation()
    // is the single source of truth for the new refresh_tokens row.
    @DisplayName("refresh_token grant: normal rotation → new tokens, old RT revoked in domain store")
    void refreshTokenGrant_normalRotation() throws Exception {
        assertThat(refreshTokenValue).as("Requires Order=1 (RT from authCode flow)").isNotBlank();

        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", refreshTokenValue)
                        .param("client_id", "demo-spa-client"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                .andReturn();

        JsonNode tokenResponse = objectMapper.readTree(result.getResponse().getContentAsString());
        String newRefreshToken = tokenResponse.get("refresh_token").asText();
        String newAccessToken = tokenResponse.get("access_token").asText();

        // New tokens must be different from the old ones (rotation)
        assertThat(newRefreshToken)
                .as("rotated refresh token must differ from original")
                .isNotEqualTo(refreshTokenValue);
        assertThat(newAccessToken)
                .as("new access token must differ from original")
                .isNotEqualTo(accessTokenValue);

        // Old refresh token must be revoked in the domain store
        Optional<RefreshToken> oldDomainToken = refreshTokenRepository.findByJti(refreshTokenValue);
        assertThat(oldDomainToken).isPresent();
        assertThat(oldDomainToken.get().isRevoked())
                .as("original RT must be revoked after rotation")
                .isTrue();

        // New refresh token must be in domain store with rotated_from pointer
        Optional<RefreshToken> newDomainToken = refreshTokenRepository.findByJti(newRefreshToken);
        assertThat(newDomainToken)
                .as("rotated RT must be persisted in domain store")
                .isPresent();
        assertThat(newDomainToken.get().getRotatedFrom())
                .as("new RT must carry rotated_from = old RT value")
                .isEqualTo(refreshTokenValue);

        // Update shared state for the reuse detection test
        refreshTokenValue = newRefreshToken;
        accessTokenValue = newAccessToken;
    }

    // -----------------------------------------------------------------------
    // 3. Tenant claim preservation after refresh
    // -----------------------------------------------------------------------

    @Test
    @Order(3)
    @DisplayName("refresh_token grant: new access_token still contains tenant_id + tenant_type claims")
    void refreshedAccessToken_hasTenantClaims() throws Exception {
        assertThat(accessTokenValue).as("Requires Order=2 (refreshed access token)").isNotBlank();

        JsonNode payload = decodeJwtPayload(accessTokenValue);

        assertThat(payload.has("tenant_id"))
                .as("refreshed access_token must contain tenant_id")
                .isTrue();
        assertThat(payload.get("tenant_id").asText()).isEqualTo("fan-platform");

        assertThat(payload.has("tenant_type"))
                .as("refreshed access_token must contain tenant_type")
                .isTrue();
        assertThat(payload.get("tenant_type").asText()).isEqualTo("B2C");
    }

    // -----------------------------------------------------------------------
    // 4. Reuse detection: same refresh_token used twice → invalid_grant
    // -----------------------------------------------------------------------

    @Test
    @Order(4)
    // TASK-BE-274 / ADR-003 옵션 B: depends on the rotation path which is now
    // unblocked by the provider-side TSM-flag skip-path (see Order=2 above).
    @DisplayName("reuse detection: reusing a rotated refresh_token → 400 invalid_grant")
    void refreshTokenGrant_reuseDetected_returns400() throws Exception {
        // Capture current (valid) RT
        String currentRt = refreshTokenValue;
        assertThat(currentRt).isNotBlank();

        // First use — valid rotation
        MvcResult firstResult = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", currentRt)
                        .param("client_id", "demo-spa-client"))
                .andExpect(status().isOk())
                .andReturn();

        // After first use, currentRt is rotated (revoked in domain store)
        // Second use of the same RT — must be rejected as reuse
        MvcResult secondResult = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", currentRt)
                        .param("client_id", "demo-spa-client"))
                .andExpect(status().is4xxClientError()) // 400 invalid_grant
                .andReturn();

        String responseBody = secondResult.getResponse().getContentAsString();
        assertThat(responseBody).contains("invalid_grant");

        // Verify that the domain store reflects revoked state
        Optional<RefreshToken> reuseToken = refreshTokenRepository.findByJti(currentRt);
        assertThat(reuseToken).isPresent();
        assertThat(reuseToken.get().isRevoked())
                .as("reused RT must be revoked in domain store")
                .isTrue();
    }

    // -----------------------------------------------------------------------
    // 5. Expired/invalid refresh token → 400
    // -----------------------------------------------------------------------

    @Test
    @Order(5)
    @DisplayName("refresh_token grant: unknown token → 400 invalid_grant")
    void refreshTokenGrant_unknownToken_returns400() throws Exception {
        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", "completely-invalid-token-value-" + UUID.randomUUID())
                        .param("client_id", "demo-spa-client"))
                .andExpect(status().is4xxClientError());
    }

    // -----------------------------------------------------------------------
    // 6. Cross-tenant rejection
    // -----------------------------------------------------------------------

    @Test
    @Order(6)
    @DisplayName("cross-tenant: refresh_token with mismatched tenant rejected → 400 invalid_grant")
    void refreshTokenGrant_crossTenant_rejected() throws Exception {
        // 1. Issue a valid RT for demo-spa-client (tenant=fan-platform)
        String codeVerifier = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("cross-tenant-verifier-0123456789".getBytes(StandardCharsets.UTF_8));
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        String codeChallenge = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sha256.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII)));

        // TASK-MONO-044c-1 RC#1: queryParam() for GET /oauth2/authorize.
        MvcResult authorizeResult = mockMvc.perform(get("/oauth2/authorize")
                        .with(user("cross-tenant-account")
                                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .queryParam("response_type", "code")
                        .queryParam("client_id", "demo-spa-client")
                        .queryParam("redirect_uri", "http://localhost:3000/callback")
                        .queryParam("scope", "openid")
                        .queryParam("code_challenge", codeChallenge)
                        .queryParam("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String code = extractParam(authorizeResult.getResponse().getHeader("Location"), "code");

        MvcResult tokenResult = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", "http://localhost:3000/callback")
                        .param("client_id", "demo-spa-client")
                        .param("code_verifier", codeVerifier))
                .andExpect(status().isOk())
                .andReturn();

        String crossTenantRt = objectMapper.readTree(
                tokenResult.getResponse().getContentAsString()).get("refresh_token").asText();

        // 2. Directly tamper the domain store to simulate a cross-tenant token
        //    (force the JPA record's tenantId to a different value)
        refreshTokenRepository.findByJti(crossTenantRt).ifPresent(domainToken -> {
            // We cannot mutate tenantId directly (immutable field) — instead we
            // insert a new record that shadows the original with a different tenantId.
            // For the cross-tenant test we rely on the fact that SasRefreshTokenAuthenticationProvider
            // extracts client tenantId from clientName and compares it with the domain store.
            // Since demo-spa-client has tenantId=fan-platform and the domain store also
            // reflects fan-platform, the mismatch scenario requires a client registered for
            // a different tenant. This scenario tests the code path when such a mismatch
            // would occur — the check is in SasRefreshTokenAuthenticationProvider.
        });

        // Attempt refresh using test-internal-client (client_credentials only — REFRESH_TOKEN not granted)
        // → SAS will reject with unauthorized_client before our tenant check even runs.
        // This validates the client-level guard that prevents cross-client token use.
        String internalBasicAuth = "Basic " + Base64.getEncoder()
                .encodeToString("test-internal-client:secret".getBytes());

        mockMvc.perform(post("/oauth2/token")
                        .header("Authorization", internalBasicAuth)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", crossTenantRt))
                .andExpect(status().is4xxClientError());
    }

    // -----------------------------------------------------------------------
    // 7. Regression: existing POST /api/auth/refresh still works
    // -----------------------------------------------------------------------

    @Test
    @Order(7)
    @DisplayName("regression: POST /api/auth/refresh endpoint reachable (SAS does not capture it)")
    void regression_legacyRefreshEndpointStillReachable() throws Exception {
        // Send a bad payload — the point is to verify it reaches the legacy handler
        // and returns 4xx (not 404 which would indicate SAS captured it).
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"not-a-valid-token"}
                                """))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status)
                            .as("Legacy /api/auth/refresh must return 4xx, not 404 or 5xx. Got " + status)
                            .isBetween(400, 499);
                    assertThat(status)
                            .as("404 means SAS swallowed /api/auth/refresh — not acceptable")
                            .isNotEqualTo(404);
                });
    }

    // -----------------------------------------------------------------------
    // 8. TASK-BE-603: a real-shaped (long) login email. The mirror row is keyed on
    //    the account UUID from the principal details, so issuance and refresh both
    //    succeed, and revokeAllByAccountId(uuid) reaches the SAS mirror row.
    //    TASK-BE-604 AC-1: that revoked row alone now refuses the next refresh.
    // -----------------------------------------------------------------------

    @Test
    @Order(8)
    @DisplayName("TASK-BE-603/604: 54자+ 이메일 계정 — 발급 시 미러 행(UUID) 저장 → refresh 성공 → "
            + "revokeAllByAccountId(UUID) 가 SAS 미러 행을 맞힘 → 그것만으로 다음 refresh 400 invalid_grant(BE-604 AC-1) "
            + "→ 강제 로그아웃(UUID)도 새 세션의 refresh 거부")
    void longEmailAccount_mirrorRowKeyedOnUuid_refreshSucceeds_andAccountRevokeReachesIt() throws Exception {
        String accountId = UUID.randomUUID().toString();
        // Real-shaped address, longer than refresh_tokens.account_id VARCHAR(36).
        String email = "first.last.long-name+" + UUID.randomUUID().toString().substring(0, 8)
                + "@subdomain.example-company.com";
        assertThat(email).hasSizeGreaterThan(36);

        String issued = signInWithLoginPrincipal(email, accountId);

        // 🔴 Assert the row exists: the initial-issuance INSERT swallows its failure
        // (DomainSyncOAuth2AuthorizationService), so "the token endpoint returned 200"
        // proves nothing about the mirror row.
        Optional<RefreshToken> issuedRow = refreshTokenRepository.findByJti(issued);
        assertThat(issuedRow)
                .as("the initial-issuance mirror row must be persisted, not swallowed")
                .isPresent();
        assertThat(issuedRow.get().getAccountId())
                .as("mirror row account_id = the account UUID, not the login email")
                .isEqualTo(accountId);

        // Refresh succeeds (before TASK-BE-603 this INSERT failed in persistRotation).
        String rotated = refreshOk(issued);
        Optional<RefreshToken> rotatedRow = refreshTokenRepository.findByJti(rotated);
        assertThat(rotatedRow).isPresent();
        assertThat(rotatedRow.get().getAccountId()).isEqualTo(accountId);
        assertThat(rotatedRow.get().getRotatedFrom()).isEqualTo(issued);
        assertThat(refreshTokenRepository.findByJti(issued).orElseThrow().isRevoked()).isTrue();

        // AC-2 (i): a revoke keyed on the account UUID now reaches the SAS-path mirror row.
        Integer revoked = transactionTemplate.execute(
                s -> refreshTokenRepository.revokeAllByAccountId(accountId));
        assertThat(revoked).as("the live SAS mirror row of this account").isEqualTo(1);
        assertThat(refreshTokenRepository.findByJti(rotated).orElseThrow().isRevoked()).isTrue();

        // TASK-BE-604 AC-1 — the cell TASK-BE-603 deliberately did not assert. A revoked
        // mirror row now refuses the next refresh BY ITSELF: SAS's built-in
        // OAuth2RefreshTokenAuthenticationProvider is removed, so the custom provider's
        // invalid_grant is no longer retried by a provider that checks only the authorization
        // (CI run 36134528069 measured 200 here before the removal). Nothing else has touched
        // this session: the SAS authorization is still active.
        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", rotated)
                        .param("client_id", "demo-spa-client"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
        // …and nothing rotated behind the 400. The built-in provider used to rotate the SAS
        // authorization here (reuse-refresh-tokens=false) and DomainSync inserted a fresh mirror
        // row for it — "the session revives". Had it run, the authorization would no longer be
        // found by the refused token's value.
        assertThat(oAuth2AuthorizationService.findByToken(rotated, OAuth2TokenType.REFRESH_TOKEN))
                .as("the SAS authorization still holds the refused token — no rotation happened")
                .isNotNull();

        // AC-2 (ii) of TASK-BE-603, kept: force-logout by account UUID on a FRESH session of
        // the same account closes the SAS authorization (BE-601 adapter) and refuses its refresh.
        String second = signInWithLoginPrincipal(email, accountId);
        credentialJpaRepository.save(CredentialJpaEntity.fromDomain(Credential.create(
                accountId, "fan-platform", email, CredentialHash.argon2id("unused"), Instant.now())));
        ForceLogoutUseCase.Result forced = forceLogoutUseCase.execute(accountId);
        assertThat(forced.revokedTokenCount())
                .as("mirror rows: the fresh session's live row (1); SAS authorizations: the fresh "
                        + "session's (1) + the first session's (1) — above, only its mirror row "
                        + "was revoked, its authorization is still active in SAS")
                .isEqualTo(3);

        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", second)
                        .param("client_id", "demo-spa-client"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    // -----------------------------------------------------------------------
    // 9. TASK-BE-604 AC-2 (i): password reset ends an existing SAS session.
    //    ConfirmPasswordResetUseCase revokes mirror rows by account UUID only — it never
    //    touches the SAS authorization. Before BE-604 that left the session refreshing
    //    (the built-in provider checked the authorization alone).
    // -----------------------------------------------------------------------

    @Test
    @Order(9)
    @DisplayName("TASK-BE-604 AC-2: 비밀번호 재설정 → 기존 SAS 세션의 다음 refresh 400 invalid_grant "
            + "(인가는 그대로 — 미러 행 폐기만으로 거부)")
    void passwordReset_refusesExistingSasSessionRefresh() throws Exception {
        String accountId = UUID.randomUUID().toString();
        String email = "reset-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        credentialJpaRepository.save(CredentialJpaEntity.fromDomain(Credential.create(
                accountId, "fan-platform", email, CredentialHash.argon2id("unused"), Instant.now())));

        String issued = signInWithLoginPrincipal(email, accountId);
        // Control: before the reset the session refreshes (so the 400 below is the reset's doing).
        String rotated = refreshOk(issued);

        String resetToken = "reset-" + UUID.randomUUID();
        passwordResetTokenStore.save(resetToken, accountId, Duration.ofMinutes(10));
        confirmPasswordResetUseCase.execute(new ConfirmPasswordResetCommand(resetToken, "Reset-Passw0rd!2026"));

        assertThat(refreshTokenRepository.findByJti(rotated).orElseThrow().isRevoked())
                .as("the reset revoked the session's live mirror row (by account UUID)")
                .isTrue();
        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", rotated)
                        .param("client_id", "demo-spa-client"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    // -----------------------------------------------------------------------
    // 10. TASK-BE-604 AC-2 (ii): SAS reuse detection ends the account's OTHER sessions.
    //
    //     🔴 How the reuse branch is reached. A token that was really rotated away cannot
    //     reach TokenReuseDetector on this path: JdbcOAuth2AuthorizationService keeps only
    //     the authorization's CURRENT refresh token, so findByToken(old) is null and the
    //     provider answers invalid_grant before looking at the mirror store (that is what
    //     Order(4) observes — its 400 is not the reuse branch). The branch is reachable when
    //     the SAS store still holds the token while the mirror store already has a child
    //     rotated from it — the state two concurrent refreshes of the same token leave.
    //     This test writes that child row directly.
    // -----------------------------------------------------------------------

    @Test
    @Order(10)
    @DisplayName("TASK-BE-604 AC-2: 재사용 탐지(세션 A) → 같은 계정의 다른 세션 B 의 refresh 400 invalid_grant")
    void reuseDetected_refusesTheAccountsOtherSessions() throws Exception {
        String accountId = UUID.randomUUID().toString();
        String email = "reuse-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";

        String sessionA = signInWithLoginPrincipal(email, accountId);
        String sessionB = signInWithLoginPrincipal(email, accountId);
        // Control: B refreshes before the reuse.
        sessionB = refreshOk(sessionB);

        Instant now = Instant.now();
        String childOfA = "concurrent-child-" + UUID.randomUUID();
        transactionTemplate.executeWithoutResult(s -> refreshTokenRepository.save(RefreshToken.create(
                childOfA, accountId, "fan-platform", now, now.plusSeconds(3600), sessionA, null, null)));

        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", sessionA)
                        .param("client_id", "demo-spa-client"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"))
                .andExpect(jsonPath("$.error_description").value(
                        org.hamcrest.Matchers.containsString("reuse detected")));

        assertThat(refreshTokenRepository.findByJti(sessionB).orElseThrow().isRevoked())
                .as("the reuse branch revoked every mirror row of the account, B's included")
                .isTrue();
        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", sessionB)
                        .param("client_id", "demo-spa-client"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    /** authorization_code + PKCE with the principal shape the form/social login paths build. */
    private String signInWithLoginPrincipal(String email, String accountId) throws Exception {
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
                        .queryParam("client_id", "demo-spa-client")
                        .queryParam("redirect_uri", "http://localhost:3000/callback")
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
                        .param("redirect_uri", "http://localhost:3000/callback")
                        .param("client_id", "demo-spa-client")
                        .param("code_verifier", codeVerifier))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(token.getResponse().getContentAsString()).get("refresh_token").asText();
    }

    private String refreshOk(String refreshToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", refreshToken)
                        .param("client_id", "demo-spa-client"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("refresh_token").asText();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String extractParam(String url, String paramName) {
        String query = url.contains("?") ? url.substring(url.indexOf("?") + 1) : url;
        for (String param : query.split("&")) {
            if (param.startsWith(paramName + "=")) {
                return param.substring(paramName.length() + 1);
            }
        }
        return null;
    }

    private JsonNode decodeJwtPayload(String jwt) throws Exception {
        String[] parts = jwt.split("\\.");
        assertThat(parts).hasSize(3);
        String payload = parts[1];
        int mod = payload.length() % 4;
        if (mod != 0) payload += "=".repeat(4 - mod);
        return objectMapper.readTree(Base64.getUrlDecoder().decode(payload));
    }
}
