package com.example.auth.integration;

import com.example.auth.application.port.EmailSenderPort;
import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.credentials.CredentialHash;
import com.example.auth.infrastructure.persistence.CredentialJpaEntity;
import com.example.auth.infrastructure.persistence.CredentialJpaRepository;
import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import com.example.security.password.Argon2idPasswordHasher;
import com.example.testsupport.integration.AbstractIntegrationTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-627 AC-2 / AC-3 — the real DB/Redis round trip the page-controller slice tests cannot
 * exercise: a seeded credential's password is actually changed in MySQL via the browser pages,
 * the old password then fails {@code POST /login} and the new one succeeds, and reusing the same
 * (now-consumed) token is rejected by the real {@code PasswordResetTokenStore} (Redis), not a
 * mock.
 *
 * <p>Harness copied from {@link FormLoginIntegrationTest}: {@link AbstractIntegrationTest}'s
 * shared static MySQL container, a per-class Redis {@code GenericContainer} (the password-reset
 * token store + rate limiter are Redis-backed), a WireMock stand-in for account-service, and the
 * same {@code IamClientCredentialsTokenProvider} mock (the GAP client_credentials Bearer is minted
 * via a SAS self-call unreachable in MockMvc). No new production-code test hooks: the reset token
 * is recovered the same way a real mail would carry it — by capturing the argument
 * {@link com.example.auth.application.RequestPasswordResetUseCase} passes to
 * {@link EmailSenderPort#sendPasswordResetEmail}, which is mocked out purely to avoid a real
 * SMTP/log dependency, not to change any production code path.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PasswordResetIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    /**
     * {@link com.example.auth.application.ConfirmPasswordResetUseCase#recoverSelfRecoverableLock}
     * always asks account-service for the account's status after a successful reset (TASK-BE-612,
     * fail-soft). Stubbed ACTIVE so that call is a clean no-op, like {@link FormLoginIntegrationTest}
     * stubs the status lookup on the login path.
     */
    static WireMockServer accountService;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

        accountService = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        accountService.start();
        registry.add("auth.account-service.base-url", accountService::baseUrl);
        accountService.stubFor(WireMock.get(WireMock.urlPathMatching(
                        "/internal/accounts/[^/]+/status-with-tenant"))
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                { "accountId": "%s", "tenantId": "%s", "status": "ACTIVE",
                                  "statusChangedAt": "2026-01-01T00:00:00Z" }
                                """.formatted(TEST_ACCOUNT_ID, TEST_TENANT_ID))));
    }

    @AfterAll
    static void stopAccountService() {
        if (accountService != null && accountService.isRunning()) {
            accountService.stop();
        }
    }

    // Same reason FormLoginIntegrationTest mocks it: unreachable self-call in MockMvc.
    @MockitoBean
    IamClientCredentialsTokenProvider gapTokenProvider;

    // The ONLY mock standing in for a real mail transport — see class Javadoc. The use case under
    // test (RequestPasswordResetUseCase) is otherwise completely real, including the Redis save.
    @MockitoBean
    EmailSenderPort emailSenderPort;

    private static final String TEST_EMAIL = "password-reset-it@example.com";
    private static final String OLD_PASSWORD = "OldPassw0rd!";
    private static final String NEW_PASSWORD = "NewPassw0rd!2";
    private static final String TEST_ACCOUNT_ID = "password-reset-it-account-001";
    private static final String TEST_TENANT_ID = "fan-platform";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CredentialJpaRepository credentialJpaRepository;

    @BeforeEach
    void seedCredential() {
        credentialJpaRepository.deleteAll();
        Argon2idPasswordHasher hasher = new Argon2idPasswordHasher();
        String hash = hasher.hash(OLD_PASSWORD);
        credentialJpaRepository.save(CredentialJpaEntity.fromDomain(
                Credential.create(TEST_ACCOUNT_ID, TEST_TENANT_ID, TEST_EMAIL,
                        CredentialHash.argon2id(hash), Instant.now())));

        Mockito.when(gapTokenProvider.currentBearer()).thenReturn("test-jwt");
    }

    /**
     * Requests a reset for {@link #TEST_EMAIL} through the real HTML page (CSRF, real
     * {@code RequestPasswordResetUseCase}, real Redis save) and returns the plaintext token the
     * use case generated — captured off the {@link EmailSenderPort} mock, exactly the value a real
     * mail's link would carry as {@code ?token=...}.
     */
    private String requestResetAndCaptureToken() throws Exception {
        mockMvc.perform(post("/password-reset/request")
                        .with(csrf())
                        .param("email", TEST_EMAIL))
                .andExpect(status().isOk());

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(emailSenderPort).sendPasswordResetEmail(
                org.mockito.ArgumentMatchers.eq(TEST_EMAIL), tokenCaptor.capture());
        String token = tokenCaptor.getValue();
        assertThat(token).as("a real UUID token must have been generated and saved to Redis").isNotBlank();
        return token;
    }

    @Test
    @DisplayName("AC-2: 유효 토큰으로 새 비밀번호 저장 → 로그인 화면 리다이렉트 · 옛 비밀번호 로그인 실패 · 새 비밀번호 로그인 성공")
    void ac2_validToken_changesPassword_oldPasswordFails_newPasswordSucceeds() throws Exception {
        String token = requestResetAndCaptureToken();

        // POST /password-reset (CSRF, real ConfirmPasswordResetUseCase: Argon2id hash persisted to
        // MySQL, refresh tokens/SAS authorizations revoked, token deleted from Redis).
        mockMvc.perform(post("/password-reset")
                        .with(csrf())
                        .param("token", token)
                        .param("newPassword", NEW_PASSWORD)
                        .param("confirmPassword", NEW_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?passwordReset"));

        // AC-2: the OLD password must now fail against the real, DB-persisted credential row.
        MockHttpSession oldPasswordSession = new MockHttpSession();
        mockMvc.perform(post("/login")
                        .session(oldPasswordSession)
                        .with(csrf())
                        .param("username", TEST_EMAIL)
                        .param("password", OLD_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
        assertThat(oldPasswordSession.getAttribute("SPRING_SECURITY_CONTEXT"))
                .as("AC-2: the old password must not authenticate after a committed reset")
                .isNull();

        // AC-2: the NEW password must succeed — same form-login path FormLoginIntegrationTest's
        // happy path uses (session migrates on successful authentication).
        MockHttpSession newPasswordSession = new MockHttpSession();
        MvcResult newPasswordLogin = mockMvc.perform(post("/login")
                        .session(newPasswordSession)
                        .with(csrf())
                        .param("username", TEST_EMAIL)
                        .param("password", NEW_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(newPasswordLogin.getResponse().getRedirectedUrl())
                .as("AC-2: the new password must NOT land back on /login?error")
                .isNotEqualTo("/login?error");
        MockHttpSession authedSession =
                (MockHttpSession) newPasswordLogin.getRequest().getSession(false);
        assertThat(authedSession)
                .as("AC-2: the new password must establish an authenticated session "
                        + "(post-login session-fixation rotation, same as FormLoginIntegrationTest)")
                .isNotNull();
        assertThat(authedSession.getAttribute("SPRING_SECURITY_CONTEXT"))
                .as("AC-2: the new password must authenticate")
                .isNotNull();
    }

    @Test
    @DisplayName("AC-3 (실제 DB 판정): 이미 소비된 토큰 재사용 → «링크가 만료되었거나 이미 사용되었습니다», 두 번째 비밀번호 변경은 적용되지 않는다")
    void ac3_reusingConsumedToken_isRejectedByRealTokenStore() throws Exception {
        String token = requestResetAndCaptureToken();

        // First confirm — consumes the token for real (Redis DELETE), same call as AC-2's.
        mockMvc.perform(post("/password-reset")
                        .with(csrf())
                        .param("token", token)
                        .param("newPassword", NEW_PASSWORD)
                        .param("confirmPassword", NEW_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?passwordReset"));

        // Second confirm with the SAME token — PasswordResetTokenStore.findAccountId() is a real
        // Redis lookup now, not a mock, so this proves single-use is enforced end to end.
        String secondAttemptPassword = "SecondAttempt1!";
        mockMvc.perform(post("/password-reset")
                        .with(csrf())
                        .param("token", token)
                        .param("newPassword", secondAttemptPassword)
                        .param("confirmPassword", secondAttemptPassword))
                .andExpect(status().isBadRequest());

        // AC-3 control: the second attempt's password must NOT have taken effect — only the
        // NEW_PASSWORD from the first (consumed) confirm authenticates.
        MockHttpSession rejectedAttemptSession = new MockHttpSession();
        mockMvc.perform(post("/login")
                        .session(rejectedAttemptSession)
                        .with(csrf())
                        .param("username", TEST_EMAIL)
                        .param("password", secondAttemptPassword))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));

        MockHttpSession stillWorksSession = new MockHttpSession();
        MvcResult stillWorksLogin = mockMvc.perform(post("/login")
                        .session(stillWorksSession)
                        .with(csrf())
                        .param("username", TEST_EMAIL)
                        .param("password", NEW_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(stillWorksLogin.getResponse().getRedirectedUrl())
                .as("the first (consumed) reset's password must still be the active one")
                .isNotEqualTo("/login?error");
    }
}
