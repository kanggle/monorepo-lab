package com.example.auth.application;

import com.example.auth.application.command.OAuthCallbackCommand;
import com.example.auth.application.exception.AccountServiceUnavailableException;
import com.example.auth.application.exception.SocialSignupEmailRegisteredException;
import com.example.auth.application.port.AccountServicePort;
import com.example.auth.application.port.OAuthClient;
import com.example.auth.application.port.OAuthClientProvider;
import com.example.auth.application.port.OAuthProviderConfig;
import com.example.auth.application.port.OAuthProviderConfigPort;
import com.example.auth.application.result.AccountStatusWithTenantLookupResult;
import com.example.auth.application.result.BrowserLoginResolution;
import com.example.auth.application.result.ConsumerSiteMembershipLookupResult;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-617 (ADR-MONO-078 D2 · D4; oauth-social-login.md § 계정 연결 전략) — the social-login callback on
 * the consumer pool. Like {@link OAuthLoginUseCaseSocialTenantTest}, the transactional tail is the REAL
 * {@link SocialIdentityPersistStep} over the real {@link SocialLoginSteps}, so the tenant the identity row
 * is written under is the one the code actually writes, not a stub's.
 *
 * <p>Cells: a new consumer-site social signup is a pool account with a {@code consumer-pool} identity
 * row (AC-1); a pool identity is found FIRST on any consumer site — one account on fan and store (AC-1);
 * 🔴 an email equal to a password pool account's is refused, not linked (AC-2); a pre-078 SITE identity
 * keeps resolving to its site account (AC-3); a B2B client ignores a pool identity; the consumer-site
 * question fails closed.
 */
@ExtendWith(MockitoExtension.class)
class OAuthLoginUseCaseConsumerPoolTest {

    private static final String STATE = "state-617";
    private static final String CODE = "code-617";
    private static final String REDIRECT_URI = "http://iam.local/login/oauth/google/callback";
    private static final String FAN = "fan-platform";
    private static final String STORE = "ecommerce";
    private static final String POOL = "consumer-pool";
    private static final String UID = "google-uid-617";
    private static final String EMAIL = "fan@example.com";
    private static final SessionContext CTX = new SessionContext("10.0.*.*", "Chrome 120", null);
    private static final OAuthUserInfo USER_INFO = new OAuthUserInfo(UID, EMAIL, "Fan", OAuthProvider.GOOGLE);

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

    private static ConsumerSiteMembershipLookupResult site(String site, boolean consumerSite, String status) {
        return new ConsumerSiteMembershipLookupResult(site, consumerSite,
                consumerSite ? "B2C_CONSUMER" : "B2B_ENTERPRISE", status, List.of());
    }

    private void accountServiceAnswers(String accountId, String accountTenant) {
        when(accountServicePort.getAccountStatusAndTenant(accountId))
                .thenReturn(Optional.of(new AccountStatusWithTenantLookupResult(accountId, accountTenant, "ACTIVE")));
    }

    private SocialIdentity savedIdentity() {
        ArgumentCaptor<SocialIdentity> saved = ArgumentCaptor.forClass(SocialIdentity.class);
        verify(socialIdentityRepository).save(saved.capture());
        return saved.getValue();
    }

    // ── AC-1 ──────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-1: 팬 소셜 신규 가입 → 풀 계정(tenantId=consumer-pool) · 신원 행은 consumer-pool · 풀 principal")
    void newFanSocialSignup_poolAccount_identityRowInPool() {
        when(socialIdentityRepository.findByTenantIdAndProviderAndProviderUserId(FAN, "GOOGLE", UID))
                .thenReturn(Optional.empty());
        // the transactional upsert looks the row up again — in the pool, where it is about to be created
        when(socialIdentityRepository.findByTenantIdAndProviderAndProviderUserId(POOL, "GOOGLE", UID))
                .thenReturn(Optional.empty());
        when(accountServicePort.socialSignup(EMAIL, "GOOGLE", UID, "Fan", FAN))
                .thenReturn(new SocialSignupResult("acc-pool", "ACTIVE", true, POOL));
        accountServiceAnswers("acc-pool", POOL);

        BrowserLoginResolution result = useCase.resolveBrowserLogin(command, FAN);

        assertThat(result.accountId()).isEqualTo("acc-pool");
        assertThat(result.isNewAccount()).isTrue();
        assertThat(result.poolAccount()).as("the session becomes a pool principal").isTrue();
        SocialIdentity row = savedIdentity();
        assertThat(row.getTenantId()).isEqualTo(POOL);
        assertThat(row.getAccountId()).isEqualTo("acc-pool");
        // Signed up through the fan client: the pool had no identity, so the fan tenant was asked next.
        verify(socialIdentityRepository).findPoolIdentity("GOOGLE", UID);
        // Login events carry the account's own tenant, as for a form-login pool principal.
        verify(loginEventRecorder).recordSucceeded("acc-pool", POOL, CTX, "OAUTH_GOOGLE");
    }

    @Test
    @DisplayName("AC-1: 풀 신원으로 스토어 client 재방문 → 같은 풀 계정 (가입 호출 없음 · 스토어 신원 행 조회 없음)")
    void poolIdentity_onTheOtherSite_sameAccount_noSignup() {
        SocialIdentity poolRow = SocialIdentity.create("acc-pool", POOL, "GOOGLE", UID, EMAIL);
        when(socialIdentityRepository.findPoolIdentity("GOOGLE", UID)).thenReturn(Optional.of(poolRow));
        when(accountServicePort.getConsumerSiteMembership(STORE, "acc-pool"))
                .thenReturn(site(STORE, true, null)); // not yet a store member — the consent screen's job
        when(socialIdentityRepository.findByTenantIdAndProviderAndProviderUserId(POOL, "GOOGLE", UID))
                .thenReturn(Optional.of(poolRow));
        accountServiceAnswers("acc-pool", POOL);

        BrowserLoginResolution result = useCase.resolveBrowserLogin(command, STORE);

        assertThat(result.accountId()).isEqualTo("acc-pool");
        assertThat(result.isNewAccount()).isFalse();
        assertThat(result.poolAccount()).isTrue();
        verify(accountServicePort, never()).socialSignup(any(), any(), any(), any(), any());
        verify(socialIdentityRepository, never()).findByTenantIdAndProviderAndProviderUserId(eq(STORE), any(), any());
        assertThat(savedIdentity().getTenantId()).as("the pool row is upserted, no store row").isEqualTo(POOL);
    }

    // ── AC-2 ──────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("🔴 AC-2 대조군: 비밀번호 풀 계정과 같은 이메일 → account-service 409 → email_registered · 신원 행 0 · 세션 없음")
    void passwordPoolAccountEmail_refused_noIdentityRow() {
        when(socialIdentityRepository.findByTenantIdAndProviderAndProviderUserId(FAN, "GOOGLE", UID))
                .thenReturn(Optional.empty());
        SocialSignupEmailRegisteredException refused =
                new SocialSignupEmailRegisteredException("Email already registered as a consumer-pool account");
        when(accountServicePort.socialSignup(EMAIL, "GOOGLE", UID, "Fan", FAN)).thenThrow(refused);

        assertThatThrownBy(() -> useCase.resolveBrowserLogin(command, FAN)).isSameAs(refused);

        verify(socialIdentityRepository, never()).save(any());
        verify(accountServicePort, never()).getAccountStatusAndTenant(anyString());
    }

    // ── AC-3 ──────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-3: 078 이전 사이트 소셜 신원(팬) → 그대로 그 사이트 계정 · 신원 행도 팬 테넌트 · 풀 principal 아님")
    void preDecisionSiteIdentity_stillResolvesToItsSiteAccount() {
        when(socialIdentityRepository.findByTenantIdAndProviderAndProviderUserId(FAN, "GOOGLE", UID))
                .thenReturn(Optional.of(SocialIdentity.create("acc-site", FAN, "GOOGLE", UID, EMAIL)));
        accountServiceAnswers("acc-site", FAN);

        BrowserLoginResolution result = useCase.resolveBrowserLogin(command, FAN);

        assertThat(result.accountId()).isEqualTo("acc-site");
        assertThat(result.poolAccount()).isFalse();
        assertThat(savedIdentity().getTenantId()).isEqualTo(FAN);
        verify(socialIdentityRepository).findPoolIdentity("GOOGLE", UID);
        verify(accountServicePort, never()).socialSignup(any(), any(), any(), any(), any());
        verify(accountServicePort, never()).getConsumerSiteMembership(any(), any());
    }

    @Test
    @DisplayName("AC-3: 기존 사이트 계정에 이메일로 연결된 응답(tenantId=fan-platform) → 사이트 principal, 신원 행은 팬")
    void signupLinkedToSiteAccount_isNotAPoolPrincipal() {
        when(socialIdentityRepository.findByTenantIdAndProviderAndProviderUserId(FAN, "GOOGLE", UID))
                .thenReturn(Optional.empty());
        when(accountServicePort.socialSignup(EMAIL, "GOOGLE", UID, "Fan", FAN))
                .thenReturn(new SocialSignupResult("acc-site", "ACTIVE", false, FAN));
        accountServiceAnswers("acc-site", FAN);

        BrowserLoginResolution result = useCase.resolveBrowserLogin(command, FAN);

        assertThat(result.poolAccount()).isFalse();
        assertThat(savedIdentity().getTenantId()).isEqualTo(FAN);
    }

    // ── boundaries ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("B2B client(wms): 풀 신원이 있어도 보지 않는다 — 그 테넌트 신원으로 (폼 로그인의 풀-먼저와 같은 판정)")
    void b2bClient_ignoresThePoolIdentity() {
        when(socialIdentityRepository.findPoolIdentity("GOOGLE", UID))
                .thenReturn(Optional.of(SocialIdentity.create("acc-pool", POOL, "GOOGLE", UID, EMAIL)));
        when(accountServicePort.getConsumerSiteMembership("wms", "acc-pool")).thenReturn(site("wms", false, null));
        when(socialIdentityRepository.findByTenantIdAndProviderAndProviderUserId("wms", "GOOGLE", UID))
                .thenReturn(Optional.of(SocialIdentity.create("acc-wms", "wms", "GOOGLE", UID, EMAIL)));
        accountServiceAnswers("acc-wms", "wms");

        BrowserLoginResolution result = useCase.resolveBrowserLogin(command, "wms");

        assertThat(result.accountId()).isEqualTo("acc-wms");
        assertThat(result.poolAccount()).isFalse();
        assertThat(savedIdentity().getTenantId()).isEqualTo("wms");
    }

    @Test
    @DisplayName("콘솔(iam) client: 풀 신원을 조회조차 하지 않는다 (D1) · 가입 응답이 풀이라 해도 풀 principal 이 되지 않는다")
    void consoleClient_neverAPoolPrincipal() {
        when(socialIdentityRepository.findByTenantIdAndProviderAndProviderUserId("iam", "GOOGLE", UID))
                .thenReturn(Optional.empty());
        when(accountServicePort.socialSignup(EMAIL, "GOOGLE", UID, "Fan", "iam"))
                .thenReturn(new SocialSignupResult("acc-x", "ACTIVE", true, POOL));
        accountServiceAnswers("acc-x", POOL);

        BrowserLoginResolution result = useCase.resolveBrowserLogin(command, "iam");

        assertThat(result.poolAccount()).isFalse();
        verify(socialIdentityRepository, never()).findPoolIdentity(any(), any());
    }

    @Test
    @DisplayName("소비자 사이트 판정 조회 실패 → fail-closed (AccountServiceUnavailable → temporarily_unavailable) · 신원 행 쓰기 없음")
    void consumerSiteLookupFailure_failsClosed() {
        when(socialIdentityRepository.findPoolIdentity("GOOGLE", UID))
                .thenReturn(Optional.of(SocialIdentity.create("acc-pool", POOL, "GOOGLE", UID, EMAIL)));
        when(accountServicePort.getConsumerSiteMembership(STORE, "acc-pool"))
                .thenThrow(new IllegalStateException("connection reset"));

        assertThatThrownBy(() -> useCase.resolveBrowserLogin(command, STORE))
                .isInstanceOf(AccountServiceUnavailableException.class);

        verify(socialIdentityRepository, never()).save(any());
        verify(accountServicePort, never()).socialSignup(any(), any(), any(), any(), any());
    }
}
