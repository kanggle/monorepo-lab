package com.example.auth.application;

import com.example.auth.application.command.OAuthCallbackCommand;
import com.example.auth.application.command.OAuthCallbackTxnCommand;
import com.example.auth.application.exception.InvalidOAuthRedirectUriException;
import com.example.auth.application.exception.InvalidOAuthStateException;
import com.example.auth.application.exception.OAuthEmailRequiredException;
import com.example.auth.application.exception.OAuthProviderException;
import com.example.auth.application.port.AccountServicePort;
import com.example.auth.application.port.OAuthClient;
import com.example.auth.application.port.OAuthClientProvider;
import com.example.auth.application.port.OAuthProviderConfig;
import com.example.auth.application.port.OAuthProviderConfigPort;
import com.example.auth.application.result.AccountStatusLookupResult;
import com.example.auth.application.result.OAuthLoginResult;
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
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OAuthLoginUseCase} — orchestration layer.
 *
 * <p>TASK-BE-069 + TASK-BE-072 guarantees:
 * <ul>
 *   <li>External provider HTTP (token+userinfo) is invoked BEFORE the
 *       {@link OAuthLoginTransactionalStep} (which owns the DB transaction).</li>
 *   <li>Internal account-service HTTP ({@code socialSignup} on new-identity path,
 *       {@code getAccountStatus} always) is invoked BEFORE the transactional step.</li>
 *   <li>When any of those HTTP calls fails, the transactional step is not invoked —
 *       no DB writes happen.</li>
 *   <li>The txn step receives the already-fetched provider data AND the resolved
 *       {@code accountId} / {@code isNewAccount} / {@code accountStatus} via
 *       {@link OAuthCallbackTxnCommand}.</li>
 * </ul>
 *
 * <p>TASK-BE-300: the pre-txn social-identity existence read is now via the
 * {@code SocialIdentityRepository} domain port + {@code SocialIdentity} domain model
 * (was the JPA entity/repository directly).
 *
 * <p>TASK-BE-301: the provider-client selector is now the {@code OAuthClientProvider}
 * application port (was the concrete {@code OAuthClientFactory}) and the per-provider
 * configuration is the {@code OAuthProviderConfigPort} application port (was the Spring
 * {@code OAuthProperties} {@code @ConfigurationProperties} type). The stub
 * {@code OAuthProviderConfig} records carry the SAME values the previous
 * {@code OAuthProperties.ProviderProperties} setup used (clientId
 * {@code test-google-client-id}, the same redirectUri / allowlist / scopes / authUri,
 * and the legacy single-redirect fallback mapped to its resolved allowlist), so every
 * assertion below — authorization-URL content, redirect-URI exact-match allowlist,
 * trailing-slash reject, legacy/blank fallback, callback HTTP-before-txn ordering — is
 * byte-identical to the pre-extraction test. The port hoist is behavior-neutral.
 */
@ExtendWith(MockitoExtension.class)
class OAuthLoginUseCaseTest {

    @Mock private OAuthProviderConfigPort oAuthProviderConfigPort;
    @Mock private OAuthClientProvider oAuthClientProvider;
    @Mock private OAuthStateStore oAuthStateStore;
    @Mock private OAuthLoginTransactionalStep oAuthLoginTransactionalStep;
    @Mock private AccountServicePort accountServicePort;
    @Mock private SocialIdentityRepository socialIdentityRepository;
    @Mock private OAuthClient oAuthClient;

    @InjectMocks
    private OAuthLoginUseCase oAuthLoginUseCase;

    private static final String STATE = "state-abc";
    private static final String CODE = "auth-code-123";
    private static final String REDIRECT_URI = "http://localhost:3000/oauth/callback";
    private static final SessionContext CTX = new SessionContext("127.0.0.1", "Chrome/120", "fp-1");
    private static final OAuthUserInfo USER_INFO = new OAuthUserInfo(
            "provider-user-1", "user@example.com", "User", OAuthProvider.GOOGLE);

    private OAuthCallbackCommand command;

    @BeforeEach
    void setUp() {
        command = new OAuthCallbackCommand("GOOGLE", CODE, STATE, REDIRECT_URI, CTX);

        // Mirrors the previous OAuthProperties.ProviderProperties google setup exactly:
        // clientId, defaultRedirectUri (= props.redirectUri), single-entry allowlist
        // (= props.allowedRedirectUris == [REDIRECT_URI]), scopes, authUri. The
        // allowedRedirectUris value reproduces resolveAllowedRedirectUris() for that
        // setup (non-empty allowlist → copy).
        OAuthProviderConfig googleConfig = new OAuthProviderConfig(
                "test-google-client-id",
                "https://accounts.google.com/o/oauth2/v2/auth",
                "openid,email,profile",
                REDIRECT_URI,
                List.of(REDIRECT_URI));
        // Lenient: a couple of tests short-circuit before reading provider config
        // (e.g. invalid-state callback). Strict stubs would fail those tests
        // even though the setup is logically required for most others.
        lenient().when(oAuthProviderConfigPort.get(OAuthProvider.GOOGLE)).thenReturn(googleConfig);
    }

    @Test
    @DisplayName("callback (new identity): provider HTTP → socialSignup HTTP → getAccountStatus HTTP "
            + "→ persistLogin, txn command carries resolved accountId/isNewAccount/accountStatus")
    void callback_newIdentity_allHttpBeforeTransactionalStep() {
        // given
        when(oAuthStateStore.consumeAtomic(STATE)).thenReturn(Optional.of(OAuthProvider.GOOGLE));
        when(oAuthClientProvider.getClient(OAuthProvider.GOOGLE)).thenReturn(oAuthClient);
        when(oAuthClient.exchangeCodeForUserInfo(CODE, REDIRECT_URI)).thenReturn(USER_INFO);
        when(socialIdentityRepository.findByProviderAndProviderUserId("GOOGLE", "provider-user-1"))
                .thenReturn(Optional.empty());
        when(accountServicePort.socialSignup(
                "user@example.com", "GOOGLE", "provider-user-1", "User", null))
                .thenReturn(new SocialSignupResult("acc-123", "ACTIVE", true));
        when(accountServicePort.getAccountStatus("acc-123"))
                .thenReturn(Optional.of(new AccountStatusLookupResult("acc-123", "ACTIVE")));
        OAuthLoginResult expected = new OAuthLoginResult(
                "access-jwt", "refresh-jwt", 1800, 604800L, true);
        when(oAuthLoginTransactionalStep.persistLogin(any(OAuthCallbackTxnCommand.class)))
                .thenReturn(expected);

        // when
        OAuthLoginResult result = oAuthLoginUseCase.callback(command);

        // then — all HTTP (provider + account-service) must happen BEFORE the transactional step
        InOrder order = inOrder(oAuthClient, accountServicePort, oAuthLoginTransactionalStep);
        order.verify(oAuthClient).exchangeCodeForUserInfo(CODE, REDIRECT_URI);
        order.verify(accountServicePort).socialSignup(
                "user@example.com", "GOOGLE", "provider-user-1", "User", null);
        order.verify(accountServicePort).getAccountStatus("acc-123");
        order.verify(oAuthLoginTransactionalStep).persistLogin(any(OAuthCallbackTxnCommand.class));

        // and the txn command carries all pre-resolved data
        ArgumentCaptor<OAuthCallbackTxnCommand> captor =
                ArgumentCaptor.forClass(OAuthCallbackTxnCommand.class);
        verify(oAuthLoginTransactionalStep).persistLogin(captor.capture());
        OAuthCallbackTxnCommand txnCommand = captor.getValue();
        assertThat(txnCommand.provider()).isEqualTo(OAuthProvider.GOOGLE);
        assertThat(txnCommand.userInfo()).isEqualTo(USER_INFO);
        assertThat(txnCommand.sessionContext()).isEqualTo(CTX);
        assertThat(txnCommand.accountId()).isEqualTo("acc-123");
        assertThat(txnCommand.isNewAccount()).isTrue();
        assertThat(txnCommand.accountStatus()).contains("ACTIVE");

        assertThat(result).isSameAs(expected);
    }

    @Test
    @DisplayName("callback (existing identity): socialSignup NOT called, accountId taken from "
            + "pre-existing SocialIdentity, getAccountStatus still runs before txn")
    void callback_existingIdentity_skipsSocialSignup() {
        when(oAuthStateStore.consumeAtomic(STATE)).thenReturn(Optional.of(OAuthProvider.GOOGLE));
        when(oAuthClientProvider.getClient(OAuthProvider.GOOGLE)).thenReturn(oAuthClient);
        when(oAuthClient.exchangeCodeForUserInfo(CODE, REDIRECT_URI)).thenReturn(USER_INFO);
        SocialIdentity existing = SocialIdentity.create(
                "acc-existing", "fan-platform", "GOOGLE", "provider-user-1", "user@example.com");
        when(socialIdentityRepository.findByProviderAndProviderUserId("GOOGLE", "provider-user-1"))
                .thenReturn(Optional.of(existing));
        when(accountServicePort.getAccountStatus("acc-existing"))
                .thenReturn(Optional.of(new AccountStatusLookupResult("acc-existing", "ACTIVE")));
        when(oAuthLoginTransactionalStep.persistLogin(any(OAuthCallbackTxnCommand.class)))
                .thenReturn(new OAuthLoginResult("a", "r", 1, 1L, false));

        oAuthLoginUseCase.callback(command);

        // socialSignup must NOT be called on the existing-identity path
        verify(accountServicePort, never()).socialSignup(anyString(), anyString(), anyString(), anyString(), any());

        // Ordering: provider HTTP → getAccountStatus → persistLogin
        InOrder order = inOrder(oAuthClient, accountServicePort, oAuthLoginTransactionalStep);
        order.verify(oAuthClient).exchangeCodeForUserInfo(CODE, REDIRECT_URI);
        order.verify(accountServicePort).getAccountStatus("acc-existing");
        order.verify(oAuthLoginTransactionalStep).persistLogin(any(OAuthCallbackTxnCommand.class));

        ArgumentCaptor<OAuthCallbackTxnCommand> captor =
                ArgumentCaptor.forClass(OAuthCallbackTxnCommand.class);
        verify(oAuthLoginTransactionalStep).persistLogin(captor.capture());
        assertThat(captor.getValue().accountId()).isEqualTo("acc-existing");
        assertThat(captor.getValue().isNewAccount()).isFalse();
        assertThat(captor.getValue().accountStatus()).contains("ACTIVE");
    }

    @Test
    @DisplayName("callback: getAccountStatus returns empty → txn command carries empty status; "
            + "txn step decides what to do with it")
    void callback_accountStatusEmpty_propagatesEmpty() {
        when(oAuthStateStore.consumeAtomic(STATE)).thenReturn(Optional.of(OAuthProvider.GOOGLE));
        when(oAuthClientProvider.getClient(OAuthProvider.GOOGLE)).thenReturn(oAuthClient);
        when(oAuthClient.exchangeCodeForUserInfo(CODE, REDIRECT_URI)).thenReturn(USER_INFO);
        when(socialIdentityRepository.findByProviderAndProviderUserId("GOOGLE", "provider-user-1"))
                .thenReturn(Optional.empty());
        when(accountServicePort.socialSignup(anyString(), anyString(), anyString(), anyString(), isNull()))
                .thenReturn(new SocialSignupResult("acc-new", "ACTIVE", true));
        when(accountServicePort.getAccountStatus("acc-new")).thenReturn(Optional.empty());
        when(oAuthLoginTransactionalStep.persistLogin(any(OAuthCallbackTxnCommand.class)))
                .thenReturn(new OAuthLoginResult("a", "r", 1, 1L, true));

        oAuthLoginUseCase.callback(command);

        ArgumentCaptor<OAuthCallbackTxnCommand> captor =
                ArgumentCaptor.forClass(OAuthCallbackTxnCommand.class);
        verify(oAuthLoginTransactionalStep).persistLogin(captor.capture());
        assertThat(captor.getValue().accountStatus()).isEmpty();
    }

    @Test
    @DisplayName("callback: invalid state short-circuits — no HTTP call, no txn step")
    void callback_invalidStateSkipsHttpAndTxn() {
        when(oAuthStateStore.consumeAtomic(STATE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> oAuthLoginUseCase.callback(command))
                .isInstanceOf(InvalidOAuthStateException.class);

        verify(oAuthClientProvider, never()).getClient(any());
        verify(accountServicePort, never()).socialSignup(anyString(), anyString(), anyString(), anyString(), any());
        verify(accountServicePort, never()).getAccountStatus(anyString());
        verify(oAuthLoginTransactionalStep, never()).persistLogin(any());
    }

    @Test
    @DisplayName("callback: provider HTTP failure propagates; account-service HTTP and txn step NOT called")
    void callback_providerHttpFailure_skipsAccountServiceAndTxn() {
        when(oAuthStateStore.consumeAtomic(STATE)).thenReturn(Optional.of(OAuthProvider.GOOGLE));
        when(oAuthClientProvider.getClient(OAuthProvider.GOOGLE)).thenReturn(oAuthClient);
        OAuthProviderException providerFailure =
                new OAuthProviderException("google token exchange failed");
        when(oAuthClient.exchangeCodeForUserInfo(CODE, REDIRECT_URI)).thenThrow(providerFailure);

        assertThatThrownBy(() -> oAuthLoginUseCase.callback(command))
                .isSameAs(providerFailure);

        verify(accountServicePort, never()).socialSignup(anyString(), anyString(), anyString(), anyString(), any());
        verify(accountServicePort, never()).getAccountStatus(anyString());
        verify(oAuthLoginTransactionalStep, never()).persistLogin(any());
    }

    @Test
    @DisplayName("callback: empty email from provider → reject BEFORE account-service HTTP and txn step")
    void callback_emptyEmail_skipsAccountServiceAndTxn() {
        when(oAuthStateStore.consumeAtomic(STATE)).thenReturn(Optional.of(OAuthProvider.GOOGLE));
        when(oAuthClientProvider.getClient(OAuthProvider.GOOGLE)).thenReturn(oAuthClient);
        OAuthUserInfo noEmail = new OAuthUserInfo(
                "provider-user-1", "", "User", OAuthProvider.GOOGLE);
        when(oAuthClient.exchangeCodeForUserInfo(CODE, REDIRECT_URI)).thenReturn(noEmail);

        assertThatThrownBy(() -> oAuthLoginUseCase.callback(command))
                .isInstanceOf(OAuthEmailRequiredException.class);

        verify(accountServicePort, never()).socialSignup(anyString(), anyString(), anyString(), anyString(), any());
        verify(accountServicePort, never()).getAccountStatus(anyString());
        verify(oAuthLoginTransactionalStep, never()).persistLogin(any());
    }

    @Test
    @DisplayName("callback: when the transactional step fails, HTTP calls are NOT retried "
            + "(already performed — single-shot semantics preserved)")
    void callback_txnFailure_httpNotRetried() {
        when(oAuthStateStore.consumeAtomic(STATE)).thenReturn(Optional.of(OAuthProvider.GOOGLE));
        when(oAuthClientProvider.getClient(OAuthProvider.GOOGLE)).thenReturn(oAuthClient);
        when(oAuthClient.exchangeCodeForUserInfo(CODE, REDIRECT_URI)).thenReturn(USER_INFO);
        when(socialIdentityRepository.findByProviderAndProviderUserId("GOOGLE", "provider-user-1"))
                .thenReturn(Optional.empty());
        when(accountServicePort.socialSignup(anyString(), anyString(), anyString(), anyString(), isNull()))
                .thenReturn(new SocialSignupResult("acc-1", "ACTIVE", true));
        when(accountServicePort.getAccountStatus("acc-1"))
                .thenReturn(Optional.of(new AccountStatusLookupResult("acc-1", "ACTIVE")));
        RuntimeException dbFailure = new RuntimeException("db down");
        when(oAuthLoginTransactionalStep.persistLogin(any(OAuthCallbackTxnCommand.class)))
                .thenThrow(dbFailure);

        assertThatThrownBy(() -> oAuthLoginUseCase.callback(command))
                .isSameAs(dbFailure);

        // HTTP fetches happened exactly once; no retry after txn failure
        verify(oAuthClient).exchangeCodeForUserInfo(CODE, REDIRECT_URI);
        verify(accountServicePort).socialSignup("user@example.com", "GOOGLE", "provider-user-1", "User", null);
        verify(accountServicePort).getAccountStatus("acc-1");
    }

    @Test
    @DisplayName("authorize: redirect_uri 화이트리스트 외 값 → InvalidOAuthRedirectUriException")
    void authorize_redirectUriNotInAllowlist_throws() {
        assertThatThrownBy(() ->
                oAuthLoginUseCase.authorize("GOOGLE", "https://attacker.example.com/callback"))
                .isInstanceOf(InvalidOAuthRedirectUriException.class);

        // state 가 저장되기 전에 reject 되어야 함
        verify(oAuthStateStore, never()).store(anyString(), any());
    }

    @Test
    @DisplayName("authorize: redirect_uri 화이트리스트 일치 → state 저장 + URL 반환")
    void authorize_redirectUriInAllowlist_succeeds() {
        var result = oAuthLoginUseCase.authorize("GOOGLE", REDIRECT_URI);

        assertThat(result.authorizationUrl()).contains("client_id=test-google-client-id");
        assertThat(result.state()).isNotBlank();
        verify(oAuthStateStore).store(result.state(), OAuthProvider.GOOGLE);
    }

    @Test
    @DisplayName("authorize: redirectUri null → 기본값(config.defaultRedirectUri) 으로 fallback, 화이트리스트 통과")
    void authorize_nullRedirectUri_fallsBackToDefault() {
        var result = oAuthLoginUseCase.authorize("GOOGLE", null);

        assertThat(result.authorizationUrl()).contains("redirect_uri=");
        assertThat(result.state()).isNotBlank();
        verify(oAuthStateStore).store(result.state(), OAuthProvider.GOOGLE);
    }

    @Test
    @DisplayName("authorize: 단일 redirect-uri 만 설정된 레거시 props → resolveAllowedRedirectUris 가 fallback 으로 동작")
    void authorize_legacyConfigWithoutAllowlist_fallsBackToSingleRedirectUri() {
        // Legacy props (empty allowedRedirectUris + a single redirectUri) resolve
        // via resolveAllowedRedirectUris() to a single-entry allowlist. The adapter
        // reproduces that, so the equivalent config carries
        // allowedRedirectUris == [defaultRedirectUri].
        OAuthProviderConfig legacy = new OAuthProviderConfig(
                "legacy-id",
                "https://legacy.example.com/auth",
                "openid",
                "https://legacy.example.com/cb",
                List.of("https://legacy.example.com/cb"));
        when(oAuthProviderConfigPort.get(OAuthProvider.GOOGLE)).thenReturn(legacy);

        var result = oAuthLoginUseCase.authorize("GOOGLE", "https://legacy.example.com/cb");
        assertThat(result.state()).isNotBlank();

        assertThatThrownBy(() ->
                oAuthLoginUseCase.authorize("GOOGLE", "https://attacker.example.com/cb"))
                .isInstanceOf(InvalidOAuthRedirectUriException.class);
    }

    @Test
    @DisplayName("callback: redirect_uri 화이트리스트 외 값 → 예외, provider HTTP/account-service HTTP 둘 다 호출 안 됨")
    void callback_redirectUriNotInAllowlist_throws() {
        when(oAuthStateStore.consumeAtomic(STATE)).thenReturn(Optional.of(OAuthProvider.GOOGLE));

        OAuthCallbackCommand evil = new OAuthCallbackCommand(
                "GOOGLE", CODE, STATE, "https://attacker.example.com/cb", CTX);

        assertThatThrownBy(() -> oAuthLoginUseCase.callback(evil))
                .isInstanceOf(InvalidOAuthRedirectUriException.class);

        verify(oAuthClientProvider, never()).getClient(any());
        verify(accountServicePort, never()).socialSignup(anyString(), anyString(), anyString(), anyString(), any());
        verify(accountServicePort, never()).getAccountStatus(anyString());
        verify(oAuthLoginTransactionalStep, never()).persistLogin(any());
    }

    @Test
    @DisplayName("authorize: redirectUri trailing-slash 차이 → exact-match 로 reject (정규화 우회 방지)")
    void authorize_redirectUriTrailingSlashDifference_throws() {
        assertThatThrownBy(() ->
                oAuthLoginUseCase.authorize("GOOGLE", REDIRECT_URI + "/"))
                .isInstanceOf(InvalidOAuthRedirectUriException.class);
    }

    @Test
    @DisplayName("callback: redirectUri blank → falls back to provider config default "
            + "and still calls HTTP before txn")
    void callback_redirectUriFallback() {
        // Old setup: props with only redirectUri=http://default/callback and an empty
        // allowedRedirectUris → resolveAllowedRedirectUris() == [http://default/callback].
        // The adapter reproduces that exactly.
        OAuthProviderConfig google = new OAuthProviderConfig(
                null,
                null,
                null,
                "http://default/callback",
                List.of("http://default/callback"));
        when(oAuthProviderConfigPort.get(OAuthProvider.GOOGLE)).thenReturn(google);
        when(oAuthStateStore.consumeAtomic(STATE)).thenReturn(Optional.of(OAuthProvider.GOOGLE));
        when(oAuthClientProvider.getClient(OAuthProvider.GOOGLE)).thenReturn(oAuthClient);
        when(oAuthClient.exchangeCodeForUserInfo(anyString(), anyString())).thenReturn(USER_INFO);
        when(socialIdentityRepository.findByProviderAndProviderUserId("GOOGLE", "provider-user-1"))
                .thenReturn(Optional.empty());
        when(accountServicePort.socialSignup(anyString(), anyString(), anyString(), anyString(), isNull()))
                .thenReturn(new SocialSignupResult("acc-1", "ACTIVE", true));
        when(accountServicePort.getAccountStatus("acc-1"))
                .thenReturn(Optional.of(new AccountStatusLookupResult("acc-1", "ACTIVE")));
        when(oAuthLoginTransactionalStep.persistLogin(any()))
                .thenReturn(new OAuthLoginResult("a", "r", 1, 1L, false));

        OAuthCallbackCommand blankRedirect = new OAuthCallbackCommand(
                "GOOGLE", CODE, STATE, "", CTX);
        oAuthLoginUseCase.callback(blankRedirect);

        verify(oAuthClient).exchangeCodeForUserInfo(CODE, "http://default/callback");
    }
}
