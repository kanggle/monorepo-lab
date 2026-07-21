package com.example.account.application.service;

import com.example.account.application.command.SocialSignupCommand;
import com.example.account.application.event.AccountEventPublisher;
import com.example.account.application.result.SocialSignupResult;
import com.example.account.domain.account.Account;
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
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SocialSignupUseCase unit tests")
class SocialSignupUseCaseTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private AccountEventPublisher eventPublisher;

    @Mock
    private AccountIdentityProvisioner accountIdentityProvisioner;

    @Mock
    private TenantRepository tenantRepository;

    private SocialSignupUseCase socialSignupUseCase;

    /** TASK-BE-507: social signup validates its tenant first, like every other create path. */
    @BeforeEach
    void setUp() {
        // Real ActiveTenantGuard over the mocked TenantRepository — behaviour and every
        // assertion below are preserved verbatim; only the wiring changed.
        socialSignupUseCase = new SocialSignupUseCase(accountRepository, profileRepository,
                eventPublisher, accountIdentityProvisioner, new ActiveTenantGuard(tenantRepository));
        lenient().when(tenantRepository.findById(any(TenantId.class)))
                .thenAnswer(inv -> {
                    TenantId id = inv.getArgument(0);
                    return Optional.of(Tenant.reconstitute(id, id.value(), TenantType.B2C_CONSUMER,
                            TenantStatus.ACTIVE, Instant.now(), Instant.now()));
                });
    }

    @Test
    @DisplayName("New email creates account and returns created=true with status 201")
    void execute_newEmail_createsAccountAndProfile() {
        // given
        SocialSignupCommand command = new SocialSignupCommand(
                "new@example.com", "GOOGLE", "google-123", "John Doe", null);

        given(accountRepository.findByEmail(TenantId.FAN_PLATFORM, "new@example.com"))
                .willReturn(Optional.empty());

        Account savedAccount = Account.reconstitute(
                "acc-new", TenantId.FAN_PLATFORM, "new@example.com", "hash-new", AccountStatus.ACTIVE,
                Instant.now(), Instant.now(), null, null, null, 0);
        given(accountRepository.save(any(Account.class))).willReturn(savedAccount);
        // TASK-BE-381 (ADR-036 M1): born-unified mint returns the central identity.
        given(accountIdentityProvisioner.mintIdentity(any(), any())).willReturn("idy-new");

        // when
        SocialSignupResult result = socialSignupUseCase.execute(command);

        // then
        assertThat(result.created()).isTrue();
        assertThat(result.accountId()).isEqualTo("acc-new");
        assertThat(result.email()).isEqualTo("new@example.com");
        assertThat(result.status()).isEqualTo("ACTIVE");

        verify(accountRepository).save(any(Account.class));
        verify(profileRepository).save(any(Profile.class));
        verify(eventPublisher).publishAccountCreated(any(Account.class), any(), any());
        // TASK-BE-381: the minted identity is assigned to the new account (born-unified).
        verify(accountRepository).assignIdentityId(any(), eq("acc-new"), eq("idy-new"));
    }

    @Test
    @DisplayName("Existing email returns existing account with created=false")
    void execute_existingEmail_returnsExistingAccount() {
        // given
        SocialSignupCommand command = new SocialSignupCommand(
                "existing@example.com", "KAKAO", "kakao-456", "Jane", null);

        Account existingAccount = Account.reconstitute(
                "acc-existing", TenantId.FAN_PLATFORM, "existing@example.com", "hash-existing",
                AccountStatus.ACTIVE, Instant.now(), Instant.now(), null, null, null, 0);
        given(accountRepository.findByEmail(TenantId.FAN_PLATFORM, "existing@example.com"))
                .willReturn(Optional.of(existingAccount));

        // when
        SocialSignupResult result = socialSignupUseCase.execute(command);

        // then
        assertThat(result.created()).isFalse();
        assertThat(result.accountId()).isEqualTo("acc-existing");
        assertThat(result.email()).isEqualTo("existing@example.com");
        assertThat(result.status()).isEqualTo("ACTIVE");

        verify(accountRepository, never()).save(any());
        verify(profileRepository, never()).save(any());
        verify(eventPublisher, never()).publishAccountCreated(any(), any(), any());
    }

    @Test
    @DisplayName("Existing locked account returns with LOCKED status")
    void execute_lockedAccount_returnsLockedStatus() {
        // given
        SocialSignupCommand command = new SocialSignupCommand(
                "locked@example.com", "GOOGLE", "google-789", "Locked User", null);

        Account lockedAccount = Account.reconstitute(
                "acc-locked", TenantId.FAN_PLATFORM, "locked@example.com", "hash-locked",
                AccountStatus.LOCKED, Instant.now(), Instant.now(), null, null, null, 0);
        given(accountRepository.findByEmail(TenantId.FAN_PLATFORM, "locked@example.com"))
                .willReturn(Optional.of(lockedAccount));

        // when
        SocialSignupResult result = socialSignupUseCase.execute(command);

        // then
        assertThat(result.created()).isFalse();
        assertThat(result.status()).isEqualTo("LOCKED");
    }

    @Test
    @DisplayName("Race condition on concurrent signup recovers by returning existing account")
    void execute_raceCondition_returnsExistingAccount() {
        // given
        SocialSignupCommand command = new SocialSignupCommand(
                "race@example.com", "GOOGLE", "google-race", "Racer", null);

        given(accountRepository.findByEmail(TenantId.FAN_PLATFORM, "race@example.com"))
                .willReturn(Optional.empty())  // first check: not found
                .willReturn(Optional.of(Account.reconstitute(  // after race: found
                        "acc-raced", TenantId.FAN_PLATFORM, "race@example.com", "hash-raced",
                        AccountStatus.ACTIVE, Instant.now(), Instant.now(), null, null, null, 0)));

        given(accountRepository.save(any(Account.class)))
                .willThrow(new DataIntegrityViolationException("Duplicate key"));

        // when
        SocialSignupResult result = socialSignupUseCase.execute(command);

        // then
        assertThat(result.created()).isFalse();
        assertThat(result.accountId()).isEqualTo("acc-raced");
    }

    @Test
    @DisplayName("Email is normalized (trimmed and lowercased) for lookup")
    void execute_emailNormalized_forLookup() {
        // given
        SocialSignupCommand command = new SocialSignupCommand(
                "  Test@Example.COM  ", "GOOGLE", "google-norm", "Norm", null);

        Account existingAccount = Account.reconstitute(
                "acc-norm", TenantId.FAN_PLATFORM, "test@example.com", "hash-norm",
                AccountStatus.ACTIVE, Instant.now(), Instant.now(), null, null, null, 0);
        given(accountRepository.findByEmail(TenantId.FAN_PLATFORM, "test@example.com"))
                .willReturn(Optional.of(existingAccount));

        // when
        SocialSignupResult result = socialSignupUseCase.execute(command);

        // then
        assertThat(result.created()).isFalse();
        verify(accountRepository).findByEmail(TenantId.FAN_PLATFORM, "test@example.com");
    }

    // ── TASK-BE-507 ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TASK-BE-507: 소셜 가입도 client tenant 로 태어난다 — 토큰(ecommerce)과 계정 행이 더는 모순되지 않는다")
    void execute_tenantFromClient_accountIsBornInThatTenant() {
        TenantId ecommerce = new TenantId("ecommerce");
        SocialSignupCommand command = new SocialSignupCommand(
                "shopper@example.com", "GOOGLE", "google-shop", "Shopper", "ecommerce");

        given(accountRepository.findByEmail(ecommerce, "shopper@example.com")).willReturn(Optional.empty());
        given(accountRepository.save(any(Account.class))).willAnswer(inv -> inv.getArgument(0));

        socialSignupUseCase.execute(command);

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(saved.capture());
        assertThat(saved.getValue().getTenantId()).isEqualTo(ecommerce);
        // The account.created event (→ ecommerce user-service profile projection) carries it too.
        verify(eventPublisher).publishAccountCreated(any(Account.class), eq("ecommerce"), any());
    }

    @Test
    @DisplayName("TASK-BE-507: 같은 이메일이라도 다른 tenant 면 다른 계정 — 조회가 tenant 로 스코프된다")
    void execute_sameEmailOtherTenant_isNotTheSameAccount() {
        TenantId ecommerce = new TenantId("ecommerce");
        SocialSignupCommand command = new SocialSignupCommand(
                "dual@example.com", "GOOGLE", "google-dual", "Dual", "ecommerce");

        // The fan-platform account with this email exists, but the lookup is scoped to ecommerce
        // and must NOT return it (pre-BE-507 it would have, because the lookup was fan-pinned).
        given(accountRepository.findByEmail(ecommerce, "dual@example.com")).willReturn(Optional.empty());
        given(accountRepository.save(any(Account.class))).willAnswer(inv -> inv.getArgument(0));

        SocialSignupResult result = socialSignupUseCase.execute(command);

        assertThat(result.created()).isTrue();
        verify(accountRepository).findByEmail(ecommerce, "dual@example.com");
        verify(accountRepository, never()).findByEmail(eq(TenantId.FAN_PLATFORM), any());
    }
}
