package com.example.account.application.service;

import com.example.account.application.command.SignupCommand;
import com.example.account.application.event.AccountEventPublisher;
import com.example.account.application.exception.AccountAlreadyExistsException;
import com.example.account.application.exception.TenantNotFoundException;
import com.example.account.application.exception.TenantSuspendedException;
import com.example.account.application.port.AuthServicePort;
import com.example.account.application.result.SignupResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.account.PasswordPolicyViolationException;
import com.example.account.domain.profile.Profile;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.ProfileRepository;
import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.tenant.Tenant;
import com.example.account.domain.tenant.TenantId;
import com.example.account.domain.tenant.TenantStatus;
import com.example.account.domain.tenant.TenantType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
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
    @Mock private TenantRepository tenantRepository;

    private SignupUseCase signupUseCase;

    /**
     * TASK-BE-507: every signup now resolves + validates its tenant first, so each test needs
     * an ACTIVE tenant behind whichever tenant the command carries.
     */
    @BeforeEach
    void setUp() {
        // Real ActiveTenantGuard over the mocked TenantRepository, so the extracted tenant guard
        // runs for real — every stub and assertion below is preserved verbatim from the
        // pre-extraction test.
        signupUseCase = new SignupUseCase(accountRepository, profileRepository, eventPublisher,
                authServicePort, accountIdentityProvisioner, new ActiveTenantGuard(tenantRepository));
        // lenient: the two rejection tests below shadow this with a tenant-specific stub, which
        // would otherwise make this one "unused" under STRICT_STUBS.
        lenient().when(tenantRepository.findById(any(TenantId.class)))
                .thenAnswer(inv -> Optional.of(activeTenant(inv.getArgument(0))));
    }

    private static Tenant activeTenant(TenantId tenantId) {
        return Tenant.reconstitute(tenantId, tenantId.value(), TenantType.B2C_CONSUMER,
                TenantStatus.ACTIVE, Instant.now(), Instant.now());
    }

    /** No tenant on the command — the header-less caller, pinned to fan-platform (net-zero). */
    private SignupCommand sampleCommand() {
        return commandForTenant(null);
    }

    private SignupCommand commandForTenant(String tenantId) {
        return new SignupCommand(
                "new@example.com",
                "password123!",
                "Jane",
                "en-US",
                "UTC",
                tenantId
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
                "new@example.com", "lowercaseonly", "Jane", "en-US", "UTC", null);

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

    // ── TASK-BE-507 ────────────────────────────────────────────────────────────────────
    // Nothing in this repository asserted what tenant a consumer signup lands in. That is
    // exactly why the fan-platform hard-pin survived long enough for every ecommerce shopper
    // to be born in the wrong tenant (TASK-BE-506 measured the radius). These are the guards.

    @Test
    @DisplayName("TASK-BE-507: ecommerce 클라이언트로 가입하면 계정이 ecommerce tenant 로 태어난다 — credential/이벤트까지 동일 tenant")
    void execute_tenantFromClient_accountIsBornInThatTenant() {
        TenantId ecommerce = new TenantId("ecommerce");
        given(accountRepository.existsByEmail(ecommerce, "new@example.com")).willReturn(false);
        given(accountRepository.save(any(Account.class))).willAnswer(inv -> inv.getArgument(0));
        given(accountIdentityProvisioner.mintIdentity(eq("ecommerce"), eq("new@example.com")))
                .willReturn("idy-1");

        signupUseCase.execute(commandForTenant("ecommerce"));

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(saved.capture());
        assertThat(saved.getValue().getTenantId()).isEqualTo(ecommerce);

        // The tenant must survive every downstream hop, not just the account row: the credential
        // row (BE-313 pass-through → auth_db) and the account.created event both carry it.
        verify(authServicePort).createCredential(
                any(), eq("new@example.com"), eq("password123!"), eq("ecommerce"), eq("idy-1"));
        verify(eventPublisher).publishAccountCreated(any(Account.class), eq("ecommerce"), any());
    }

    @Test
    @DisplayName("TASK-BE-507 net-zero: tenant 없이 오면 fan-platform — 기존 소비자 경로 무손실")
    void execute_noTenant_pinsToFanPlatform() {
        given(accountRepository.existsByEmail(TenantId.FAN_PLATFORM, "new@example.com")).willReturn(false);
        given(accountRepository.save(any(Account.class))).willAnswer(inv -> inv.getArgument(0));

        signupUseCase.execute(sampleCommand());

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(saved.capture());
        assertThat(saved.getValue().getTenantId()).isEqualTo(TenantId.FAN_PLATFORM);
    }

    @Test
    @DisplayName("TASK-BE-507: 미등록 tenant 로는 계정이 태어나지 않는다 — FK 위반(=오해를 부르는 409)이 아니라 TenantNotFound")
    void execute_unknownTenant_rejectedBeforeCreate() {
        given(tenantRepository.findById(new TenantId("ghost"))).willReturn(Optional.empty());

        assertThatThrownBy(() -> signupUseCase.execute(commandForTenant("ghost")))
                .isInstanceOf(TenantNotFoundException.class);

        verify(accountRepository, never()).save(any(Account.class));
        verify(authServicePort, never()).createCredential(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("TASK-BE-507: SUSPENDED tenant 로도 계정이 태어나지 않는다")
    void execute_suspendedTenant_rejectedBeforeCreate() {
        TenantId suspended = new TenantId("suspended-co");
        given(tenantRepository.findById(suspended)).willReturn(Optional.of(
                Tenant.reconstitute(suspended, "Suspended Co", TenantType.B2B_ENTERPRISE,
                        TenantStatus.SUSPENDED, Instant.now(), Instant.now())));

        assertThatThrownBy(() -> signupUseCase.execute(commandForTenant("suspended-co")))
                .isInstanceOf(TenantSuspendedException.class);

        verify(accountRepository, never()).save(any(Account.class));
    }
}
