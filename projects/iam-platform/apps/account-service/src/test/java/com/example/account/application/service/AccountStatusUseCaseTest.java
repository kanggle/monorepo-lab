package com.example.account.application.service;

import com.example.account.application.command.ChangeStatusCommand;
import com.example.account.application.event.AccountEventPublisher;
import com.example.account.application.exception.AccountNotFoundException;
import com.example.account.application.result.AccountStatusResult;
import com.example.account.application.result.DeleteAccountResult;
import com.example.account.application.result.StatusChangeResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.history.AccountStatusHistoryEntry;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.AccountStatusHistoryRepository;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.status.AccountStatusMachine;
import com.example.account.domain.status.StatusChangeReason;
import com.example.account.domain.tenant.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountStatusUseCase 단위 테스트")
class AccountStatusUseCaseTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AccountStatusHistoryRepository historyRepository;
    @Mock
    private AccountEventPublisher eventPublisher;

    private AccountStatusUseCase useCase;

    private static final int GRACE_PERIOD_DAYS = 30;

    @BeforeEach
    void setUp() {
        useCase = new AccountStatusUseCase(
                accountRepository, historyRepository,
                new AccountStatusMachine(), eventPublisher,
                GRACE_PERIOD_DAYS);
    }

    // ── getStatus ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getStatus — 계정 있고 히스토리 있음 → 히스토리 기준 응답")
    void getStatus_withHistory_returnsHistoryDetails() {
        Account account = activeAccount("acc-1");
        AccountStatusHistoryEntry entry = historyEntry("acc-1", AccountStatus.LOCKED,
                AccountStatus.ACTIVE, StatusChangeReason.ADMIN_UNLOCK);
        when(accountRepository.findById(TenantId.FAN_PLATFORM, "acc-1")).thenReturn(Optional.of(account));
        when(historyRepository.findTopByAccountIdOrderByOccurredAtDesc("acc-1"))
                .thenReturn(Optional.of(entry));

        AccountStatusResult result = useCase.getStatus("acc-1");

        assertThat(result.accountId()).isEqualTo("acc-1");
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.reason()).isEqualTo(StatusChangeReason.ADMIN_UNLOCK.name());
    }

    @Test
    @DisplayName("getStatus — 히스토리 없음 → createdAt 반환, reasonCode null")
    void getStatus_noHistory_returnsCreatedAtAndNullReason() {
        Account account = activeAccount("acc-2");
        when(accountRepository.findById(TenantId.FAN_PLATFORM, "acc-2")).thenReturn(Optional.of(account));
        when(historyRepository.findTopByAccountIdOrderByOccurredAtDesc("acc-2"))
                .thenReturn(Optional.empty());

        AccountStatusResult result = useCase.getStatus("acc-2");

        assertThat(result.reason()).isNull();
        assertThat(result.statusChangedAt()).isEqualTo(account.getCreatedAt());
    }

    @Test
    @DisplayName("getStatus — 계정 없음 → AccountNotFoundException")
    void getStatus_accountNotFound_throwsAccountNotFoundException() {
        when(accountRepository.findById(eq(TenantId.FAN_PLATFORM), eq("missing")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.getStatus("missing"))
                .isInstanceOf(AccountNotFoundException.class);
    }

    // ── changeStatus ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("changeStatus ACTIVE→LOCKED — publishAccountLocked 이벤트 발행")
    void changeStatus_activeToLocked_publishesLockedEvent() {
        Account account = activeAccount("acc-3");
        when(accountRepository.findById(TenantId.FAN_PLATFORM, "acc-3")).thenReturn(Optional.of(account));
        when(historyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ChangeStatusCommand cmd = new ChangeStatusCommand(
                "acc-3", AccountStatus.LOCKED, StatusChangeReason.ADMIN_LOCK,
                "ADMIN", "admin-1", null);

        StatusChangeResult result = useCase.changeStatus(cmd);

        assertThat(result.previousStatus()).isEqualTo("ACTIVE");
        assertThat(result.currentStatus()).isEqualTo("LOCKED");
        verify(eventPublisher).publishStatusChanged(any(), any(), any(), any(), any(), any(), any());
        verify(eventPublisher).publishAccountLocked(any(), any(), any(), any(), any(), any());
        verify(eventPublisher, never()).publishAccountUnlocked(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("changeStatus LOCKED→ACTIVE — publishAccountUnlocked 이벤트 발행")
    void changeStatus_lockedToActive_publishesUnlockedEvent() {
        Account account = lockedAccount("acc-4");
        when(accountRepository.findById(TenantId.FAN_PLATFORM, "acc-4")).thenReturn(Optional.of(account));
        when(historyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ChangeStatusCommand cmd = new ChangeStatusCommand(
                "acc-4", AccountStatus.ACTIVE, StatusChangeReason.ADMIN_UNLOCK,
                "ADMIN", "admin-1", null);

        StatusChangeResult result = useCase.changeStatus(cmd);

        assertThat(result.previousStatus()).isEqualTo("LOCKED");
        assertThat(result.currentStatus()).isEqualTo("ACTIVE");
        verify(eventPublisher).publishAccountUnlocked(any(), any(), any(), any(), any(), any());
        verify(eventPublisher, never()).publishAccountLocked(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("changeStatus — 계정 없음 → AccountNotFoundException")
    void changeStatus_accountNotFound_throwsAccountNotFoundException() {
        when(accountRepository.findById(eq(TenantId.FAN_PLATFORM), eq("no-acc")))
                .thenReturn(Optional.empty());

        ChangeStatusCommand cmd = new ChangeStatusCommand(
                "no-acc", AccountStatus.LOCKED, StatusChangeReason.ADMIN_LOCK,
                "ADMIN", "admin-1", null);

        assertThatThrownBy(() -> useCase.changeStatus(cmd))
                .isInstanceOf(AccountNotFoundException.class);
    }

    // ── deleteAccount ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteAccount — 정상 삭제 후 gracePeriodEndsAt 반환 및 이벤트 발행")
    void deleteAccount_existing_returnsGracePeriodAndPublishesEvents() {
        Account account = activeAccount("acc-5");
        when(accountRepository.findById(TenantId.FAN_PLATFORM, "acc-5")).thenReturn(Optional.of(account));
        when(historyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DeleteAccountResult result = useCase.deleteAccount(
                "acc-5", StatusChangeReason.USER_REQUEST, "USER", "acc-5");

        assertThat(result.accountId()).isEqualTo("acc-5");
        assertThat(result.gracePeriodEndsAt()).isAfter(Instant.now());
        verify(eventPublisher).publishStatusChanged(any(), any(), any(), any(), any(), any(), any());
        verify(eventPublisher).publishAccountDeleted(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("deleteAccount — 계정 없음 → AccountNotFoundException")
    void deleteAccount_accountNotFound_throwsAccountNotFoundException() {
        when(accountRepository.findById(eq(TenantId.FAN_PLATFORM), eq("gone")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.deleteAccount(
                "gone", StatusChangeReason.ADMIN_DELETE, "ADMIN", "admin-1"))
                .isInstanceOf(AccountNotFoundException.class);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static Account activeAccount(String id) {
        Instant now = Instant.now();
        return Account.reconstitute(id, TenantId.FAN_PLATFORM, "test@example.com", null,
                AccountStatus.ACTIVE, now, now, null, null, null, 0);
    }

    private static Account lockedAccount(String id) {
        Instant now = Instant.now();
        return Account.reconstitute(id, TenantId.FAN_PLATFORM, "locked@example.com", null,
                AccountStatus.LOCKED, now, now, null, null, null, 0);
    }

    private static AccountStatusHistoryEntry historyEntry(String accountId,
                                                           AccountStatus from,
                                                           AccountStatus to,
                                                           StatusChangeReason reason) {
        return AccountStatusHistoryEntry.create(accountId, from, to, reason, "ADMIN", "admin-1", null);
    }
}
