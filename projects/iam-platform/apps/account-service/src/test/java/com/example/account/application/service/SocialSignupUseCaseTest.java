package com.example.account.application.service;

import com.example.account.application.command.SocialSignupCommand;
import com.example.account.application.event.AccountEventPublisher;
import com.example.account.application.result.SocialSignupResult;
import com.example.account.domain.account.Account;
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
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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

    @InjectMocks
    private SocialSignupUseCase socialSignupUseCase;

    @Test
    @DisplayName("New email creates account and returns created=true with status 201")
    void execute_newEmail_createsAccountAndProfile() {
        // given
        SocialSignupCommand command = new SocialSignupCommand(
                "new@example.com", "GOOGLE", "google-123", "John Doe");

        given(accountRepository.findByEmail(TenantId.FAN_PLATFORM, "new@example.com"))
                .willReturn(Optional.empty());

        Account savedAccount = Account.reconstitute(
                "acc-new", TenantId.FAN_PLATFORM, "new@example.com", "hash-new", AccountStatus.ACTIVE,
                Instant.now(), Instant.now(), null, null, null, 0);
        given(accountRepository.save(any(Account.class))).willReturn(savedAccount);

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
    }

    @Test
    @DisplayName("Existing email returns existing account with created=false")
    void execute_existingEmail_returnsExistingAccount() {
        // given
        SocialSignupCommand command = new SocialSignupCommand(
                "existing@example.com", "KAKAO", "kakao-456", "Jane");

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
                "locked@example.com", "GOOGLE", "google-789", "Locked User");

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
                "race@example.com", "GOOGLE", "google-race", "Racer");

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
                "  Test@Example.COM  ", "GOOGLE", "google-norm", "Norm");

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
}
