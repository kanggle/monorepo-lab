package com.example.account.application.service;

import com.example.account.application.command.UpdateProfileCommand;
import com.example.account.application.exception.AccountNotFoundException;
import com.example.account.application.result.AccountMeResult;
import com.example.account.application.result.ProfileUpdateResult;
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

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProfileUseCase 단위 테스트")
class ProfileUseCaseTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private ProfileRepository profileRepository;

    @InjectMocks
    private ProfileUseCase useCase;

    private static final String ACCOUNT_ID = "acc-profile";

    // ── getMe ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getMe — 계정·프로필 모두 존재 → AccountMeResult 반환")
    void getMe_existingAccountAndProfile_returnsResult() {
        Account account = account(ACCOUNT_ID);
        Profile profile = profile(ACCOUNT_ID, "홍길동");
        when(accountRepository.findById(TenantId.FAN_PLATFORM, ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(profileRepository.findByAccountId(ACCOUNT_ID)).thenReturn(Optional.of(profile));

        AccountMeResult result = useCase.getMe(ACCOUNT_ID);

        assertThat(result.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(result.email()).isEqualTo("test@example.com");
        assertThat(result.profile().displayName()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("getMe — 계정 없음 → AccountNotFoundException")
    void getMe_accountNotFound_throwsAccountNotFoundException() {
        when(accountRepository.findById(TenantId.FAN_PLATFORM, ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.getMe(ACCOUNT_ID))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("getMe — 프로필 없음 → AccountNotFoundException")
    void getMe_profileNotFound_throwsAccountNotFoundException() {
        when(accountRepository.findById(TenantId.FAN_PLATFORM, ACCOUNT_ID)).thenReturn(Optional.of(account(ACCOUNT_ID)));
        when(profileRepository.findByAccountId(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.getMe(ACCOUNT_ID))
                .isInstanceOf(AccountNotFoundException.class);
    }

    // ── updateProfile ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateProfile — 정상 업데이트 → 저장 후 ProfileUpdateResult 반환")
    void updateProfile_existing_savesAndReturnsResult() {
        Profile profile = profile(ACCOUNT_ID, "기존 이름");
        when(accountRepository.findById(TenantId.FAN_PLATFORM, ACCOUNT_ID)).thenReturn(Optional.of(account(ACCOUNT_ID)));
        when(profileRepository.findByAccountId(ACCOUNT_ID)).thenReturn(Optional.of(profile));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileCommand cmd = new UpdateProfileCommand(
                ACCOUNT_ID, "새 이름", null, null, "en-US", "UTC", null);

        ProfileUpdateResult result = useCase.updateProfile(cmd);

        assertThat(result.displayName()).isEqualTo("새 이름");
        assertThat(result.locale()).isEqualTo("en-US");
        verify(profileRepository).save(profile);
    }

    @Test
    @DisplayName("updateProfile — 계정 없음 → AccountNotFoundException")
    void updateProfile_accountNotFound_throwsAccountNotFoundException() {
        when(accountRepository.findById(TenantId.FAN_PLATFORM, ACCOUNT_ID)).thenReturn(Optional.empty());

        UpdateProfileCommand cmd = new UpdateProfileCommand(
                ACCOUNT_ID, "이름", null, null, null, null, null);

        assertThatThrownBy(() -> useCase.updateProfile(cmd))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("updateProfile — 프로필 없음 → AccountNotFoundException")
    void updateProfile_profileNotFound_throwsAccountNotFoundException() {
        when(accountRepository.findById(TenantId.FAN_PLATFORM, ACCOUNT_ID)).thenReturn(Optional.of(account(ACCOUNT_ID)));
        when(profileRepository.findByAccountId(ACCOUNT_ID)).thenReturn(Optional.empty());

        UpdateProfileCommand cmd = new UpdateProfileCommand(
                ACCOUNT_ID, "이름", null, null, null, null, null);

        assertThatThrownBy(() -> useCase.updateProfile(cmd))
                .isInstanceOf(AccountNotFoundException.class);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static Account account(String id) {
        Instant now = Instant.now();
        return Account.reconstitute(id, TenantId.FAN_PLATFORM, "test@example.com", null,
                AccountStatus.ACTIVE, now, now, null, null, null, 0);
    }

    private static Profile profile(String accountId, String displayName) {
        return Profile.reconstitute(1L, accountId, displayName,
                null, null, "ko-KR", "Asia/Seoul", null, Instant.now(), null);
    }
}
