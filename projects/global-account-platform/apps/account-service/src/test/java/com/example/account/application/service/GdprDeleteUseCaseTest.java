package com.example.account.application.service;

import com.example.account.application.event.AccountEventPublisher;
import com.example.account.application.exception.AccountNotFoundException;
import com.example.account.application.result.GdprDeleteResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.history.AccountStatusHistoryEntry;
import com.example.account.domain.profile.Profile;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.AccountStatusHistoryRepository;
import com.example.account.domain.repository.ProfileRepository;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.status.AccountStatusMachine;
import com.example.account.domain.status.StateTransitionException;
import com.example.account.domain.status.StatusChangeReason;
import com.example.account.domain.tenant.TenantId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("GdprDeleteUseCase 단위 테스트")
class GdprDeleteUseCaseTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private AccountStatusHistoryRepository historyRepository;

    @Mock
    private AccountEventPublisher eventPublisher;

    private final AccountStatusMachine statusMachine = new AccountStatusMachine();

    private GdprDeleteUseCase gdprDeleteUseCase;

    private static final String ACCOUNT_ID = "acc-1";
    private static final String OPERATOR_ID = "op-1";
    private static final String ORIGINAL_EMAIL = "user@example.com";

    private GdprDeleteUseCase newUseCase() {
        return new GdprDeleteUseCase(
                accountRepository,
                profileRepository,
                historyRepository,
                statusMachine,
                eventPublisher);
    }

    private Account activeAccount() {
        return Account.reconstitute(
                ACCOUNT_ID, TenantId.FAN_PLATFORM, ORIGINAL_EMAIL, null,
                AccountStatus.ACTIVE,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                null, null, null, 0);
    }

    private Account deletedAccount() {
        return Account.reconstitute(
                ACCOUNT_ID, TenantId.FAN_PLATFORM, ORIGINAL_EMAIL, null,
                AccountStatus.DELETED,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"), null, null, 0);
    }

    private Profile profileWithPii() {
        return Profile.reconstitute(
                1L, ACCOUNT_ID, "John Doe", "+82-10-1234-5678",
                java.time.LocalDate.of(1990, 1, 15),
                "ko-KR", "Asia/Seoul", null, Instant.now(), null);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("정상 처리 — ACTIVE 계정을 DELETED 로 전이하고 이메일을 SHA-256 해시로 마스킹, 프로필 PII NULL, 이벤트 2회 발행")
    void execute_activeAccountWithProfile_masksAndPublishesEvents() {
        gdprDeleteUseCase = newUseCase();
        Account account = activeAccount();
        Profile profile = profileWithPii();

        given(accountRepository.findById(TenantId.FAN_PLATFORM, ACCOUNT_ID)).willReturn(Optional.of(account));
        given(profileRepository.findByAccountId(ACCOUNT_ID)).willReturn(Optional.of(profile));

        GdprDeleteResult result = gdprDeleteUseCase.execute(ACCOUNT_ID, OPERATOR_ID);

        // Result fields
        assertThat(result.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(result.status()).isEqualTo("DELETED");
        assertThat(result.maskedAt()).isNotNull();
        // emailHash must be exactly 64 hex characters (SHA-256 full hex)
        assertThat(result.emailHash()).hasSize(64).matches("[0-9a-f]{64}");
        // emailHash must equal sha256(originalEmail)
        assertThat(result.emailHash()).isEqualTo(sha256Hex(ORIGINAL_EMAIL));

        // Account state mutation
        assertThat(account.getStatus()).isEqualTo(AccountStatus.DELETED);
        assertThat(account.getEmailHash()).isEqualTo(result.emailHash());
        assertThat(account.getEmail()).isEqualTo("gdpr_" + result.emailHash() + "@deleted.local");

        // Account is saved
        verify(accountRepository).save(account);

        // Profile PII is masked and saved (retention.md §2.5):
        //   display_name → fixed string "탈퇴한 사용자"
        //   phone_number, birth_date, preferences → NULL
        assertThat(profile.getDisplayName()).isEqualTo("탈퇴한 사용자");
        assertThat(profile.getPhoneNumber()).isNull();
        assertThat(profile.getBirthDate()).isNull();
        assertThat(profile.getMaskedAt()).isNotNull();
        verify(profileRepository).save(profile);

        // History entry recorded with operator/regulated_deletion
        ArgumentCaptor<AccountStatusHistoryEntry> historyCaptor =
                ArgumentCaptor.forClass(AccountStatusHistoryEntry.class);
        verify(historyRepository).save(historyCaptor.capture());
        AccountStatusHistoryEntry savedHistory = historyCaptor.getValue();
        assertThat(savedHistory.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(savedHistory.getFromStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(savedHistory.getToStatus()).isEqualTo(AccountStatus.DELETED);
        assertThat(savedHistory.getReasonCode()).isEqualTo(StatusChangeReason.REGULATED_DELETION);
        assertThat(savedHistory.getActorType()).isEqualTo("operator");
        assertThat(savedHistory.getActorId()).isEqualTo(OPERATOR_ID);

        // Two events: status_changed + account_deleted_anonymized
        verify(eventPublisher).publishStatusChanged(
                any(Account.class), any(String.class), eq("ACTIVE"),
                eq(StatusChangeReason.REGULATED_DELETION.name()),
                eq("operator"), eq(OPERATOR_ID), any(Instant.class));
        verify(eventPublisher).publishAccountDeletedAnonymized(
                any(Account.class), any(String.class), eq(StatusChangeReason.REGULATED_DELETION.name()),
                eq("operator"), eq(OPERATOR_ID), any(Instant.class), any(Instant.class));
    }

    @Test
    @DisplayName("프로필 미존재 — 계정 처리는 정상 완료되고 profileRepository.save 는 호출되지 않는다")
    void execute_accountWithoutProfile_skipsProfileMasking() {
        gdprDeleteUseCase = newUseCase();
        Account account = activeAccount();

        given(accountRepository.findById(TenantId.FAN_PLATFORM, ACCOUNT_ID)).willReturn(Optional.of(account));
        given(profileRepository.findByAccountId(ACCOUNT_ID)).willReturn(Optional.empty());

        GdprDeleteResult result = gdprDeleteUseCase.execute(ACCOUNT_ID, OPERATOR_ID);

        assertThat(result.status()).isEqualTo("DELETED");
        assertThat(result.emailHash()).hasSize(64);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.DELETED);

        verify(accountRepository).save(account);
        verify(profileRepository, never()).save(any(Profile.class));

        // Events still published exactly once each
        verify(eventPublisher, times(1)).publishStatusChanged(
                any(), any(), any(), any(), any(), any(), any(Instant.class));
        verify(eventPublisher, times(1)).publishAccountDeletedAnonymized(
                any(), any(), any(), any(), any(), any(Instant.class), any(Instant.class));
    }

    @Test
    @DisplayName("이미 DELETED 상태인 계정 — StateTransitionException 발생, 저장/이벤트 발행 안 됨")
    void execute_alreadyDeletedAccount_throwsStateTransitionException() {
        gdprDeleteUseCase = newUseCase();
        Account account = deletedAccount();

        given(accountRepository.findById(TenantId.FAN_PLATFORM, ACCOUNT_ID)).willReturn(Optional.of(account));

        assertThatThrownBy(() -> gdprDeleteUseCase.execute(ACCOUNT_ID, OPERATOR_ID))
                .isInstanceOf(StateTransitionException.class);

        verify(accountRepository, never()).save(any(Account.class));
        verify(profileRepository, never()).save(any(Profile.class));
        verify(historyRepository, never()).save(any(AccountStatusHistoryEntry.class));
        verify(eventPublisher, never()).publishStatusChanged(
                any(), any(), any(), any(), any(), any(), any(Instant.class));
        verify(eventPublisher, never()).publishAccountDeletedAnonymized(
                any(), any(), any(), any(), any(), any(Instant.class), any(Instant.class));
    }

    @Test
    @DisplayName("계정 미존재 — AccountNotFoundException 발생")
    void execute_accountNotFound_throwsAccountNotFoundException() {
        gdprDeleteUseCase = newUseCase();

        given(accountRepository.findById(TenantId.FAN_PLATFORM, ACCOUNT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> gdprDeleteUseCase.execute(ACCOUNT_ID, OPERATOR_ID))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining(ACCOUNT_ID);

        verify(accountRepository, never()).save(any(Account.class));
        verify(profileRepository, never()).save(any(Profile.class));
        verify(historyRepository, never()).save(any(AccountStatusHistoryEntry.class));
        verify(eventPublisher, never()).publishStatusChanged(
                any(), any(), any(), any(), any(), any(), any(Instant.class));
        verify(eventPublisher, never()).publishAccountDeletedAnonymized(
                any(), any(), any(), any(), any(), any(Instant.class), any(Instant.class));
    }
}
