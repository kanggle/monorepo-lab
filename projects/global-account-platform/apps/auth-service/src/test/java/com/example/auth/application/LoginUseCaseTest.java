package com.example.auth.application;

import com.example.auth.application.command.LoginCommand;
import com.example.auth.application.event.AuthEventPublisher;
import com.example.auth.application.exception.AccountLockedException;
import com.example.auth.application.exception.CredentialsInvalidException;
import com.example.auth.application.exception.LoginRateLimitedException;
import com.example.auth.application.exception.LoginTenantAmbiguousException;
import com.example.auth.application.port.AccountServicePort;
import com.example.auth.application.port.TokenGeneratorPort;
import com.example.auth.application.result.AccountStatusLookupResult;
import com.example.auth.application.result.LoginResult;
import com.example.auth.application.result.RegisterDeviceSessionResult;
import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.credentials.CredentialHash;
import com.example.auth.domain.repository.CredentialRepository;
import com.example.auth.domain.repository.LoginAttemptCounter;
import com.example.auth.domain.repository.RefreshTokenRepository;
import com.example.auth.domain.session.SessionContext;
import com.example.auth.domain.tenant.TenantContext;
import com.example.auth.domain.token.RefreshToken;
import com.example.auth.domain.token.TokenPair;
import com.example.security.password.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginUseCaseTest {

    @Mock
    private CredentialRepository credentialRepository;
    @Mock
    private AccountServicePort accountServicePort;
    @Mock
    private PasswordHasher passwordHasher;
    @Mock
    private TokenGeneratorPort tokenGeneratorPort;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private LoginAttemptCounter loginAttemptCounter;
    @Mock
    private AuthEventPublisher authEventPublisher;
    @Mock
    private RegisterOrUpdateDeviceSessionUseCase registerOrUpdateDeviceSessionUseCase;

    @InjectMocks
    private LoginUseCase loginUseCase;

    private static final String EMAIL = "test@example.com";
    private static final String PASSWORD = "password123";
    private static final String ACCOUNT_ID = "acc-123";
    private static final String TENANT_ID = "fan-platform";
    private static final String HASH = "$argon2id$hash";
    private static final SessionContext CTX = new SessionContext("127.0.0.1", "Chrome/120", "fp-123");

    private static Credential credential(String accountId, String tenantId, String email, String hash) {
        return Credential.create(accountId, tenantId, email, CredentialHash.argon2id(hash), Instant.now());
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(loginUseCase, "maxFailureCount", 5);
    }

    @Test
    @DisplayName("Login succeeds with valid credentials (tenant-aware)")
    void loginSuccess() {
        // given
        when(loginAttemptCounter.getFailureCount(anyString())).thenReturn(0);
        Credential cred = credential(ACCOUNT_ID, TENANT_ID, EMAIL, HASH);
        when(credentialRepository.findAllByEmail(EMAIL)).thenReturn(List.of(cred));
        when(accountServicePort.getAccountStatus(ACCOUNT_ID))
                .thenReturn(Optional.of(new AccountStatusLookupResult(ACCOUNT_ID, "ACTIVE")));
        when(passwordHasher.verify(PASSWORD, HASH)).thenReturn(true);
        when(registerOrUpdateDeviceSessionUseCase.execute(eq(ACCOUNT_ID), anyString(), any(SessionContext.class)))
                .thenReturn(new RegisterDeviceSessionResult("dev-1", true, List.of()));
        when(tokenGeneratorPort.generateTokenPair(eq(ACCOUNT_ID), eq("user"), eq("dev-1"),
                any(TenantContext.class)))
                .thenReturn(new TokenPair("access-jwt", "refresh-jwt", 1800));
        when(tokenGeneratorPort.extractJti("refresh-jwt")).thenReturn("jti-123");
        when(tokenGeneratorPort.refreshTokenTtlSeconds()).thenReturn(604800L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        // when
        LoginResult result = loginUseCase.execute(new LoginCommand(EMAIL, PASSWORD, null, CTX));

        // then
        assertThat(result.accessToken()).isEqualTo("access-jwt");
        assertThat(result.refreshToken()).isEqualTo("refresh-jwt");
        assertThat(result.expiresIn()).isEqualTo(1800);
        assertThat(result.tokenType()).isEqualTo("Bearer");

        verify(loginAttemptCounter).resetFailureCount(anyString());
    }

    @Test
    @DisplayName("Login succeeds when tenantId is explicitly specified")
    void loginSuccessWithExplicitTenant() {
        // given
        when(loginAttemptCounter.getFailureCount(anyString())).thenReturn(0);
        Credential cred = credential(ACCOUNT_ID, TENANT_ID, EMAIL, HASH);
        when(credentialRepository.findByTenantIdAndEmail(TENANT_ID, EMAIL))
                .thenReturn(Optional.of(cred));
        when(accountServicePort.getAccountStatus(ACCOUNT_ID))
                .thenReturn(Optional.of(new AccountStatusLookupResult(ACCOUNT_ID, "ACTIVE")));
        when(passwordHasher.verify(PASSWORD, HASH)).thenReturn(true);
        when(registerOrUpdateDeviceSessionUseCase.execute(eq(ACCOUNT_ID), anyString(), any(SessionContext.class)))
                .thenReturn(new RegisterDeviceSessionResult("dev-1", true, List.of()));
        when(tokenGeneratorPort.generateTokenPair(eq(ACCOUNT_ID), eq("user"), eq("dev-1"),
                any(TenantContext.class)))
                .thenReturn(new TokenPair("access-jwt", "refresh-jwt", 1800));
        when(tokenGeneratorPort.extractJti("refresh-jwt")).thenReturn("jti-123");
        when(tokenGeneratorPort.refreshTokenTtlSeconds()).thenReturn(604800L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        // when
        LoginResult result = loginUseCase.execute(new LoginCommand(EMAIL, PASSWORD, TENANT_ID, CTX));

        // then
        assertThat(result.accessToken()).isEqualTo("access-jwt");
        // Tenant-specific path should NOT call findAllByEmail
        verify(credentialRepository, never()).findAllByEmail(anyString());
        verify(credentialRepository).findByTenantIdAndEmail(TENANT_ID, EMAIL);
    }

    @Test
    @DisplayName("Login fails with LOGIN_TENANT_AMBIGUOUS when same email in two tenants and no tenantId given")
    void loginFailsWithTenantAmbiguous() {
        // given
        when(loginAttemptCounter.getFailureCount(anyString())).thenReturn(0);
        Credential cred1 = credential(ACCOUNT_ID, "fan-platform", EMAIL, HASH);
        Credential cred2 = credential("acc-456", "wms", EMAIL, HASH);
        when(credentialRepository.findAllByEmail(EMAIL)).thenReturn(List.of(cred1, cred2));

        // when/then
        assertThatThrownBy(() -> loginUseCase.execute(new LoginCommand(EMAIL, PASSWORD, null, CTX)))
                .isInstanceOf(LoginTenantAmbiguousException.class);

        // No password verification should happen
        verify(passwordHasher, never()).verify(anyString(), anyString());
        verify(accountServicePort, never()).getAccountStatus(anyString());
    }

    @Test
    @DisplayName("TASK-BE-260: LOGIN_TENANT_AMBIGUOUS path publishes auth.login.failed with non-null tenantId (uses rate-limit fallback)")
    void ambiguousEmail_publishesFailedEventWithNonNullTenantId() {
        // given: command with no explicit tenantId; two credentials match the same email
        when(loginAttemptCounter.getFailureCount(anyString())).thenReturn(0);
        Credential cred1 = credential(ACCOUNT_ID, "fan-platform", EMAIL, HASH);
        Credential cred2 = credential("acc-456", "wms", EMAIL, HASH);
        when(credentialRepository.findAllByEmail(EMAIL)).thenReturn(List.of(cred1, cred2));

        // when
        assertThatThrownBy(() -> loginUseCase.execute(new LoginCommand(EMAIL, PASSWORD, null, CTX)))
                .isInstanceOf(LoginTenantAmbiguousException.class);

        // then: publishLoginFailed called with non-null tenantId (fan-platform fallback)
        ArgumentCaptor<String> tenantIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(authEventPublisher).publishLoginFailed(
                eq(null), anyString(), tenantIdCaptor.capture(),
                eq("LOGIN_TENANT_AMBIGUOUS"), eq(0), any(SessionContext.class));
        assertThat(tenantIdCaptor.getValue()).isNotNull().isNotBlank();
        assertThat(tenantIdCaptor.getValue()).isEqualTo(TenantContext.DEFAULT_TENANT_ID);
    }

    @Test
    @DisplayName("Login fails with invalid password")
    void loginFailsInvalidPassword() {
        when(loginAttemptCounter.getFailureCount(anyString())).thenReturn(0);
        Credential cred = credential(ACCOUNT_ID, TENANT_ID, EMAIL, HASH);
        when(credentialRepository.findAllByEmail(EMAIL)).thenReturn(List.of(cred));
        when(accountServicePort.getAccountStatus(ACCOUNT_ID))
                .thenReturn(Optional.of(new AccountStatusLookupResult(ACCOUNT_ID, "ACTIVE")));
        when(passwordHasher.verify(PASSWORD, HASH)).thenReturn(false);

        assertThatThrownBy(() -> loginUseCase.execute(new LoginCommand(EMAIL, PASSWORD, null, CTX)))
                .isInstanceOf(CredentialsInvalidException.class);

        verify(loginAttemptCounter).incrementFailureCount(anyString());
    }

    @Test
    @DisplayName("Login fails when credential row is missing → CredentialsInvalid")
    void loginFailsCredentialMissing() {
        when(loginAttemptCounter.getFailureCount(anyString())).thenReturn(0);
        when(credentialRepository.findAllByEmail(EMAIL)).thenReturn(List.of());

        assertThatThrownBy(() -> loginUseCase.execute(new LoginCommand(EMAIL, PASSWORD, null, CTX)))
                .isInstanceOf(CredentialsInvalidException.class);

        verify(loginAttemptCounter).incrementFailureCount(anyString());
        verify(accountServicePort, never()).getAccountStatus(anyString());
    }

    @Test
    @DisplayName("Login fails when account-service can no longer find the account (stale credential)")
    void loginFailsAccountGoneForCredential() {
        when(loginAttemptCounter.getFailureCount(anyString())).thenReturn(0);
        Credential cred = credential(ACCOUNT_ID, TENANT_ID, EMAIL, HASH);
        when(credentialRepository.findAllByEmail(EMAIL)).thenReturn(List.of(cred));
        when(accountServicePort.getAccountStatus(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loginUseCase.execute(new LoginCommand(EMAIL, PASSWORD, null, CTX)))
                .isInstanceOf(CredentialsInvalidException.class);

        verify(loginAttemptCounter).incrementFailureCount(anyString());
    }

    @Test
    @DisplayName("Login fails when rate limited")
    void loginFailsRateLimited() {
        when(loginAttemptCounter.getFailureCount(anyString())).thenReturn(5);

        assertThatThrownBy(() -> loginUseCase.execute(new LoginCommand(EMAIL, PASSWORD, null, CTX)))
                .isInstanceOf(LoginRateLimitedException.class);

        verify(credentialRepository, never()).findAllByEmail(anyString());
        verify(accountServicePort, never()).getAccountStatus(anyString());
    }

    @Test
    @DisplayName("Login fails when account is locked")
    void loginFailsAccountLocked() {
        when(loginAttemptCounter.getFailureCount(anyString())).thenReturn(0);
        Credential cred = credential(ACCOUNT_ID, TENANT_ID, EMAIL, HASH);
        when(credentialRepository.findAllByEmail(EMAIL)).thenReturn(List.of(cred));
        when(accountServicePort.getAccountStatus(ACCOUNT_ID))
                .thenReturn(Optional.of(new AccountStatusLookupResult(ACCOUNT_ID, "LOCKED")));

        assertThatThrownBy(() -> loginUseCase.execute(new LoginCommand(EMAIL, PASSWORD, null, CTX)))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    @DisplayName("Login with specific unknown tenant → CREDENTIALS_INVALID (not AMBIGUOUS)")
    void loginWithSpecificTenantNotFound() {
        when(loginAttemptCounter.getFailureCount(anyString())).thenReturn(0);
        when(credentialRepository.findByTenantIdAndEmail("wms", EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loginUseCase.execute(new LoginCommand(EMAIL, PASSWORD, "wms", CTX)))
                .isInstanceOf(CredentialsInvalidException.class);
    }

    @Test
    @DisplayName("Email hash is deterministic and shortened")
    void emailHashDeterministic() {
        String hash1 = LoginUseCase.hashEmail("test@example.com");
        String hash2 = LoginUseCase.hashEmail("test@example.com");
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(10);
    }
}
