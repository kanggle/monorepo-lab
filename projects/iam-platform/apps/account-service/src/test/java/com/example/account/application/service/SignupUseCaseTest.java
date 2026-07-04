package com.example.account.application.service;

import com.example.account.application.command.SignupCommand;
import com.example.account.application.event.AccountEventPublisher;
import com.example.account.application.exception.AccountAlreadyExistsException;
import com.example.account.application.port.AuthServicePort;
import com.example.account.application.result.SignupResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.account.PasswordPolicyViolationException;
import com.example.account.domain.profile.Profile;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.ProfileRepository;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.tenant.TenantId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SignupUseCase — TASK-BE-063 credential provisioning")
class SignupUseCaseTest {

    @Mock private AccountRepository accountRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private AccountEventPublisher eventPublisher;
    @Mock private AuthServicePort authServicePort;
    @Mock private AccountIdentityProvisioner accountIdentityProvisioner;

    @InjectMocks private SignupUseCase signupUseCase;

    private SignupCommand sampleCommand() {
        return new SignupCommand(
                "new@example.com",
                "password123!",
                "Jane",
                "en-US",
                "UTC"
        );
    }

    private Account sampleSavedAccount() {
        return Account.reconstitute(
                "acc-1", TenantId.FAN_PLATFORM, "new@example.com", null,
                AccountStatus.ACTIVE, Instant.now(), Instant.now(), null, null, null, 0);
    }

    @Test
    @DisplayName("성공 플로우 — 계정/프로필 저장 후 auth-service createCredential 호출하고 이벤트 발행")
    void execute_happyPath_invokesCreateCredentialAfterPersist() {
        given(accountRepository.existsByEmail(TenantId.FAN_PLATFORM, "new@example.com")).willReturn(false);
        given(accountRepository.save(any(Account.class))).willReturn(sampleSavedAccount());
        // TASK-BE-381 (ADR-036 M1): born-unified mint returns the central identity.
        given(accountIdentityProvisioner.mintIdentity(any(), eq("new@example.com"))).willReturn("idy-1");

        SignupResult result = signupUseCase.execute(sampleCommand());

        assertThat(result.accountId()).isEqualTo("acc-1");
        // TASK-MONO-263 (ADR-032 D5 step 4): createCredential no longer carries accountType.
        verify(authServicePort).createCredential(eq("acc-1"), eq("new@example.com"), eq("password123!"),
                any(), eq("idy-1"));
        verify(profileRepository).save(any(Profile.class));
        verify(eventPublisher).publishAccountCreated(any(Account.class), any(), any());
        // TASK-BE-381: the minted identity is assigned to the new account (born-unified).
        verify(accountRepository).assignIdentityId(any(TenantId.class), eq("acc-1"), eq("idy-1"));
    }

    @Test
    @DisplayName("TASK-BE-381: born-unified mint 가 실패해도(REQUIRES_NEW 격리) 가입은 진행 — fail-soft, identity 미할당")
    void execute_identityMintFails_signupStillSucceeds_failSoft() {
        given(accountRepository.existsByEmail(TenantId.FAN_PLATFORM, "new@example.com")).willReturn(false);
        given(accountRepository.save(any(Account.class))).willReturn(sampleSavedAccount());
        // mint throws (identity infra unavailable / inner tx rollback-only) → must NOT abort signup.
        willThrow(new RuntimeException("identity infra unavailable"))
                .given(accountIdentityProvisioner).mintIdentity(any(), eq("new@example.com"));

        SignupResult result = signupUseCase.execute(sampleCommand());

        // signup completes (born unlinked) — credential + event still happen, identity NOT assigned.
        assertThat(result.accountId()).isEqualTo("acc-1");
        verify(authServicePort).createCredential(eq("acc-1"), eq("new@example.com"), eq("password123!"), any(), isNull());
        verify(eventPublisher).publishAccountCreated(any(Account.class), any(), any());
        verify(accountRepository, never()).assignIdentityId(any(), any(), any());
    }

    @Test
    @DisplayName("auth-service 가 409 를 반환하면 AccountAlreadyExistsException 으로 변환 — 이벤트 미발행")
    void execute_credentialConflict_translatesToAccountAlreadyExists() {
        given(accountRepository.existsByEmail(TenantId.FAN_PLATFORM, "new@example.com")).willReturn(false);
        given(accountRepository.save(any(Account.class))).willReturn(sampleSavedAccount());
        willThrow(new AuthServicePort.CredentialAlreadyExistsConflict("acc-1"))
                .given(authServicePort).createCredential(any(), any(), any(), any(), any());

        assertThatThrownBy(() -> signupUseCase.execute(sampleCommand()))
                .isInstanceOf(AccountAlreadyExistsException.class);

        verify(eventPublisher, never()).publishAccountCreated(any(), any(), any());
    }

    @Test
    @DisplayName("auth-service 장애 시 AuthServiceUnavailable 전파 — @Transactional 롤백으로 계정/프로필 커밋 안 됨, 이벤트 미발행")
    void execute_authServiceUnavailable_propagatesAndSkipsEvent() {
        given(accountRepository.existsByEmail(TenantId.FAN_PLATFORM, "new@example.com")).willReturn(false);
        given(accountRepository.save(any(Account.class))).willReturn(sampleSavedAccount());
        willThrow(new AuthServicePort.AuthServiceUnavailable("down", new RuntimeException()))
                .given(authServicePort).createCredential(any(), any(), any(), any(), any());

        assertThatThrownBy(() -> signupUseCase.execute(sampleCommand()))
                .isInstanceOf(AuthServicePort.AuthServiceUnavailable.class);

        // unit test cannot observe the DB rollback — it verifies the event side-effect is
        // suppressed, which is the observable contract from outside the transaction.
        verify(eventPublisher, never()).publishAccountCreated(any(), any(), any());
    }

    @Test
    @DisplayName("TASK-BE-473: 복잡도 미달 패스워드는 boundary 에서 차단 — 계정 저장/auth-service 호출/이벤트 없음")
    void execute_weakPassword_shortCircuitsBeforeAccountCreate() {
        given(accountRepository.existsByEmail(TenantId.FAN_PLATFORM, "new@example.com")).willReturn(false);
        // "lowercaseonly" — 1 of 4 character classes, fails the ≥3 complexity rule.
        SignupCommand weak = new SignupCommand(
                "new@example.com", "lowercaseonly", "Jane", "en-US", "UTC");

        assertThatThrownBy(() -> signupUseCase.execute(weak))
                .isInstanceOf(PasswordPolicyViolationException.class);

        verify(accountRepository, never()).save(any(Account.class));
        verify(authServicePort, never()).createCredential(any(), any(), any(), any(), any());
        verify(eventPublisher, never()).publishAccountCreated(any(), any(), any());
    }

    @Test
    @DisplayName("중복 이메일은 pre-check 에서 차단 — auth-service 호출 안 함")
    void execute_duplicateEmail_shortCircuitsBeforeAuthServiceCall() {
        given(accountRepository.existsByEmail(TenantId.FAN_PLATFORM, "new@example.com")).willReturn(true);

        assertThatThrownBy(() -> signupUseCase.execute(sampleCommand()))
                .isInstanceOf(AccountAlreadyExistsException.class);

        verify(authServicePort, never()).createCredential(any(), any(), any(), any(), any());
    }
}
