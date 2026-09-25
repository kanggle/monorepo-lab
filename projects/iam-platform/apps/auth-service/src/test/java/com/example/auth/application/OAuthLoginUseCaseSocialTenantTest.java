package com.example.auth.application;

import com.example.auth.application.command.OAuthCallbackCommand;
import com.example.auth.application.exception.AccountLockedException;
import com.example.auth.application.exception.AccountServiceUnavailableException;
import com.example.auth.application.exception.AccountStatusException;
import com.example.auth.application.port.AccountServicePort;
import com.example.auth.application.port.OAuthClient;
import com.example.auth.application.port.OAuthClientProvider;
import com.example.auth.application.port.OAuthProviderConfig;
import com.example.auth.application.port.OAuthProviderConfigPort;
import com.example.auth.application.result.AccountStatusWithTenantLookupResult;
import com.example.auth.application.result.BrowserLoginResolution;
import com.example.auth.application.result.SocialSignupResult;
import com.example.auth.domain.oauth.OAuthProvider;
import com.example.auth.domain.oauth.OAuthUserInfo;
import com.example.auth.domain.repository.OAuthStateStore;
import com.example.auth.domain.repository.SocialIdentityRepository;
import com.example.auth.domain.session.SessionContext;
import com.example.auth.domain.social.SocialIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-602 — the social-login callback uses the account's REAL tenant, as account-service
 * reports it, for the status guard and for the {@code auth.login.*} events.
 *
 * <p>Unlike {@link OAuthLoginUseCaseTest}, the transactional tail here is the REAL
 * {@link SocialIdentityPersistStep} over the real {@link SocialLoginSteps} (only the repository is
 * mocked), so "LOCKED → rejected" is the actual {@link AccountStatusRule} throwing, not a stub.
 *
 * <p>The tenant fixture that matters: the identity row and the initiating client both say
 * {@code ecommerce}. For a post-BE-507 store account the account row agrees; for a pre-BE-507
 * account it says {@code fan-platform}. Every tenant the caller holds is wrong for one of the two —
 * which is why the tenant must come back from account-service (owner decision, AC-0).
 */
@ExtendWith(MockitoExtension.class)
class OAuthLoginUseCaseSocialTenantTest {

    private static final String STATE = "state-602";
    private static final String CODE = "code-602";
    private static final String REDIRECT_URI = "http://iam.local/login/oauth/google/callback";
    private static final String CLIENT_TENANT = "ecommerce";
    private static final String EMAIL = "shopper@example.com";
    private static final String EMAIL_HASH = LoginHashes.emailHash(EMAIL);
    private static final SessionContext CTX = new SessionContext("10.0.*.*", "Chrome 120", null);
    private static final OAuthUserInfo USER_INFO =
            new OAuthUserInfo("google-uid-602", EMAIL, "Shopper", OAuthProvider.GOOGLE);

    @Mock private OAuthProviderConfigPort oAuthProviderConfigPort;
    @Mock private OAuthClientProvider oAuthClientProvider;
    @Mock private OAuthStateStore oAuthStateStore;
    @Mock private AccountServicePort accountServicePort;
    @Mock private SocialIdentityRepository socialIdentityRepository;
    @Mock private LoginEventRecorder loginEventRecorder;
    @Mock private OAuthClient oAuthClient;

    private OAuthLoginUseCase useCase;
    private OAuthCallbackCommand command;

    @BeforeEach
    void setUp() {
        SocialIdentityPersistStep realPersistStep =
                new SocialIdentityPersistStep(new SocialLoginSteps(socialIdentityRepository));
        useCase = new OAuthLoginUseCase(oAuthProviderConfigPort, oAuthClientProvider, oAuthStateStore,
                accountServicePort, socialIdentityRepository, realPersistStep, loginEventRecorder);
        command = new OAuthCallbackCommand("GOOGLE", CODE, STATE, REDIRECT_URI, CTX);

        when(oAuthStateStore.consumeAtomic(STATE)).thenReturn(Optional.of(OAuthProvider.GOOGLE));
        when(oAuthProviderConfigPort.get(OAuthProvider.GOOGLE)).thenReturn(new OAuthProviderConfig(
                "cid", "https://accounts.google.com/o/oauth2/v2/auth", "openid,email",
                REDIRECT_URI, List.of(REDIRECT_URI)));
        when(oAuthClientProvider.getClient(OAuthProvider.GOOGLE)).thenReturn(oAuthClient);
        when(oAuthClient.exchangeCodeForUserInfo(CODE, REDIRECT_URI)).thenReturn(USER_INFO);
    }

    private void existingIdentity(String accountId) {
        // The identity row carries the initiating client's tenant — for BOTH account generations.
        when(socialIdentityRepository.findByProviderAndProviderUserId("GOOGLE", "google-uid-602"))
                .thenReturn(Optional.of(SocialIdentity.create(
                        accountId, CLIENT_TENANT, "GOOGLE", "google-uid-602", EMAIL)));
    }

    private void accountServiceAnswers(String accountId, String accountTenant, String status) {
        when(accountServicePort.getAccountStatusAndTenant(accountId))
                .thenReturn(Optional.of(new AccountStatusWithTenantLookupResult(
                        accountId, accountTenant, status)));
    }

    // ── AC-1: status guard on the account's real tenant ──────────────────────────────────────

    @Test
    @DisplayName("AC-1: 스토어(ecommerce) 소셜 계정이 LOCKED → 소셜 로그인 거부 (BE-600 까지는 fan-platform 고정 조회의 404 로 통과했다)")
    void storeTenantSocialAccount_locked_isRejected() {
        existingIdentity("acc-ec");
        accountServiceAnswers("acc-ec", "ecommerce", "LOCKED");

        assertThatThrownBy(() -> useCase.resolveBrowserLogin(command, CLIENT_TENANT))
                .isInstanceOf(AccountLockedException.class);

        // The tenant-less lookup replaced the pinned one; the pinned one is not called at all.
        verify(accountServicePort, never()).getAccountStatus(anyString());
        verify(accountServicePort, never()).getAccountStatus(anyString(), any());
    }

    @Test
    @DisplayName("AC-1: DORMANT → 거부 (AccountStatusException)")
    void storeTenantSocialAccount_dormant_isRejected() {
        existingIdentity("acc-ec");
        accountServiceAnswers("acc-ec", "ecommerce", "DORMANT");

        assertThatThrownBy(() -> useCase.resolveBrowserLogin(command, CLIENT_TENANT))
                .isInstanceOf(AccountStatusException.class);
    }

    @Test
    @DisplayName("AC-1: 신규 소셜 가입 — account-service 404 → 통과 (BE-600 규칙: 404 는 거부가 아니다), 이벤트 없음")
    void newSocialSignup_notFound_passes() {
        when(socialIdentityRepository.findByProviderAndProviderUserId("GOOGLE", "google-uid-602"))
                .thenReturn(Optional.empty());
        when(accountServicePort.socialSignup(EMAIL, "GOOGLE", "google-uid-602", "Shopper", CLIENT_TENANT))
                .thenReturn(new SocialSignupResult("acc-new", "ACTIVE", true));
        when(accountServicePort.getAccountStatusAndTenant("acc-new")).thenReturn(Optional.empty());

        BrowserLoginResolution result = useCase.resolveBrowserLogin(command, CLIENT_TENANT);

        assertThat(result.accountId()).isEqualTo("acc-new");
        assertThat(result.isNewAccount()).isTrue();
        verify(socialIdentityRepository).save(any(SocialIdentity.class));
        // No answer = no account tenant to report — nothing is emitted rather than a guessed tenant.
        verifyNoInteractions(loginEventRecorder);
    }

    @Test
    @DisplayName("AC-1: 조회 실패 → fail-closed (예외 전파 · 신원 행 쓰기 없음 · 이벤트 없음)")
    void lookupFailure_failsClosed() {
        existingIdentity("acc-ec");
        AccountServiceUnavailableException outage =
                new AccountServiceUnavailableException("status-with-tenant rejected: 401");
        when(accountServicePort.getAccountStatusAndTenant("acc-ec")).thenThrow(outage);

        assertThatThrownBy(() -> useCase.resolveBrowserLogin(command, CLIENT_TENANT)).isSameAs(outage);

        verify(socialIdentityRepository, never()).save(any());
        verifyNoInteractions(loginEventRecorder);
    }

    // ── AC-2: auth.login.* with the account's real tenant ────────────────────────────────────

    @Test
    @DisplayName("AC-2: 성공 → attempted + succeeded, tenantId=계정 행의 테넌트, loginMethod=OAUTH_GOOGLE")
    void success_emitsAttemptedAndSucceeded_withAccountsTenant() {
        existingIdentity("acc-ec");
        accountServiceAnswers("acc-ec", "ecommerce", "ACTIVE");

        useCase.resolveBrowserLogin(command, CLIENT_TENANT);

        verify(loginEventRecorder).recordAttempted("acc-ec", EMAIL_HASH, "ecommerce", CTX);
        verify(loginEventRecorder).recordSucceeded("acc-ec", "ecommerce", CTX, "OAUTH_GOOGLE");
        verify(loginEventRecorder, never()).recordFailed(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("AC-2: BE-507 이전 계정(신원·client=ecommerce, 계정=fan-platform) → 이벤트 tenantId 는 fan-platform (시작 client 의 테넌트가 아니다)")
    void preBe507Account_eventsCarryAccountsTenant_notClientTenant() {
        existingIdentity("acc-old");
        accountServiceAnswers("acc-old", "fan-platform", "ACTIVE");

        useCase.resolveBrowserLogin(command, CLIENT_TENANT);

        verify(loginEventRecorder).recordAttempted("acc-old", EMAIL_HASH, "fan-platform", CTX);
        verify(loginEventRecorder).recordSucceeded("acc-old", "fan-platform", CTX, "OAUTH_GOOGLE");
    }

    @Test
    @DisplayName("AC-2: LOCKED → attempted + failed(ACCOUNT_LOCKED), succeeded 없음")
    void locked_emitsFailedWithAccountLocked() {
        existingIdentity("acc-ec");
        accountServiceAnswers("acc-ec", "ecommerce", "LOCKED");

        assertThatThrownBy(() -> useCase.resolveBrowserLogin(command, CLIENT_TENANT))
                .isInstanceOf(AccountLockedException.class);

        verify(loginEventRecorder).recordAttempted("acc-ec", EMAIL_HASH, "ecommerce", CTX);
        verify(loginEventRecorder).recordFailed("acc-ec", EMAIL_HASH, "ecommerce", "ACCOUNT_LOCKED", CTX);
        verify(loginEventRecorder, never()).recordSucceeded(any(), any(), any(), any());
    }

    @Test
    @DisplayName("AC-2: 계약 밖 상태 값 → 거부되지만 failed 는 내지 않는다 (enum 에 없는 값을 만들지 않는다)")
    void unknownStatus_rejected_noFailedEvent() {
        existingIdentity("acc-ec");
        accountServiceAnswers("acc-ec", "ecommerce", "SUSPENDED_BY_MARS");

        assertThatThrownBy(() -> useCase.resolveBrowserLogin(command, CLIENT_TENANT))
                .isInstanceOf(AccountStatusException.class);

        verify(loginEventRecorder).recordAttempted("acc-ec", EMAIL_HASH, "ecommerce", CTX);
        verify(loginEventRecorder, never()).recordFailed(any(), any(), any(), any(), any());
        verify(loginEventRecorder, never()).recordSucceeded(any(), any(), any(), any());
    }

    @Test
    @DisplayName("AC-2: 텔레메트리 실패는 로그인 결과를 바꾸지 않는다 — 성공은 성공")
    void telemetryFailure_doesNotFailASuccessfulLogin() {
        existingIdentity("acc-ec");
        accountServiceAnswers("acc-ec", "ecommerce", "ACTIVE");
        doThrow(new IllegalStateException("outbox down"))
                .when(loginEventRecorder).recordAttempted(any(), any(), any(), any());
        doThrow(new IllegalStateException("outbox down"))
                .when(loginEventRecorder).recordSucceeded(any(), any(), any(), any());

        BrowserLoginResolution result = useCase.resolveBrowserLogin(command, CLIENT_TENANT);

        assertThat(result.accountId()).isEqualTo("acc-ec");
    }

    @Test
    @DisplayName("AC-2: 텔레메트리 실패는 로그인 결과를 바꾸지 않는다 — 거부는 같은 거부(텔레메트리 예외로 바뀌지 않는다)")
    void telemetryFailure_doesNotChangeARejection() {
        existingIdentity("acc-ec");
        accountServiceAnswers("acc-ec", "ecommerce", "LOCKED");
        doThrow(new IllegalStateException("outbox down"))
                .when(loginEventRecorder).recordFailed(any(), any(), any(), any(), any());

        assertThatThrownBy(() -> useCase.resolveBrowserLogin(command, CLIENT_TENANT))
                .isInstanceOf(AccountLockedException.class);
    }
}
