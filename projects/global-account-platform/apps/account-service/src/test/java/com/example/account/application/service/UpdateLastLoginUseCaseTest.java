package com.example.account.application.service;

import com.example.account.domain.account.Account;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.tenant.TenantId;
import com.example.messaging.outbox.ProcessedEventJpaEntity;
import com.example.messaging.outbox.ProcessedEventJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link UpdateLastLoginUseCase} (TASK-BE-103).
 *
 * <p>Covers the three required scenarios from the task acceptance criteria:
 * normal flow, duplicate event (DB-level dedup), account-not-found
 * (poison-pill guard).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateLastLoginUseCase 단위 테스트")
class UpdateLastLoginUseCaseTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private ProcessedEventJpaRepository processedEventRepository;

    @InjectMocks
    private UpdateLastLoginUseCase useCase;

    private static final String EVENT_ID = "11111111-1111-1111-1111-111111111111";
    private static final String ACCOUNT_ID = "acc-1";

    @Test
    @DisplayName("정상 흐름: existsByEventId false → recordLoginSuccess + save + processed_events insert")
    void execute_happyPath_updatesLastLoginAndMarksProcessed() {
        Instant occurredAt = Instant.parse("2026-04-26T10:00:00Z");
        Account account = activeAccount(ACCOUNT_ID, null);

        given(processedEventRepository.existsByEventId(EVENT_ID)).willReturn(false);
        given(accountRepository.findById(TenantId.FAN_PLATFORM, ACCOUNT_ID)).willReturn(Optional.of(account));

        useCase.execute(EVENT_ID, ACCOUNT_ID, occurredAt);

        // Account was advanced and persisted.
        ArgumentCaptor<Account> savedAccount = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(savedAccount.capture());
        assertThat(savedAccount.getValue().getLastLoginSucceededAt()).isEqualTo(occurredAt);

        // Dedup row written with the correct event type (flushed eagerly so a
        // duplicate-delivery race surfaces the constraint violation inside the
        // try/catch instead of at commit-time deferred flush).
        ArgumentCaptor<ProcessedEventJpaEntity> savedDedup =
                ArgumentCaptor.forClass(ProcessedEventJpaEntity.class);
        verify(processedEventRepository).saveAndFlush(savedDedup.capture());
        assertThat(savedDedup.getValue().getEventId()).isEqualTo(EVENT_ID);
        assertThat(savedDedup.getValue().getEventType()).isEqualTo("auth.login.succeeded");
    }

    @Test
    @DisplayName("중복 이벤트: existsByEventId true → 즉시 return, account/processed_events 모두 미접근")
    void execute_duplicateEventId_skipsAllSideEffects() {
        given(processedEventRepository.existsByEventId(EVENT_ID)).willReturn(true);

        useCase.execute(EVENT_ID, ACCOUNT_ID, Instant.parse("2026-04-26T10:00:00Z"));

        verify(accountRepository, never()).findById(any(), any());
        verify(accountRepository, never()).save(any());
        verify(processedEventRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("계정 미존재: findById empty → save 미수행, 예외 미전파 (dedup row는 이미 저장됨)")
    void execute_accountNotFound_returnsWithoutSideEffects() {
        given(processedEventRepository.existsByEventId(EVENT_ID)).willReturn(false);
        given(accountRepository.findById(TenantId.FAN_PLATFORM, ACCOUNT_ID)).willReturn(Optional.empty());

        useCase.execute(EVENT_ID, ACCOUNT_ID, Instant.parse("2026-04-26T10:00:00Z"));

        // Dedup row IS persisted (dedup-first ordering): re-delivery of the same
        // unknown-account event must short-circuit on existsByEventId rather
        // than re-walk this code path.
        verify(processedEventRepository).saveAndFlush(any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("max 시맨틱: 저장된 timestamp가 더 최신이면 lastLoginSucceededAt를 갱신하지 않는다")
    void execute_olderTimestamp_doesNotRegress() {
        Instant newer = Instant.parse("2026-04-26T10:00:00Z");
        Instant older = newer.minus(1, ChronoUnit.HOURS);
        Account account = activeAccount(ACCOUNT_ID, newer);

        given(processedEventRepository.existsByEventId(EVENT_ID)).willReturn(false);
        given(accountRepository.findById(TenantId.FAN_PLATFORM, ACCOUNT_ID)).willReturn(Optional.of(account));

        useCase.execute(EVENT_ID, ACCOUNT_ID, older);

        ArgumentCaptor<Account> savedAccount = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(savedAccount.capture());
        // Field unchanged because older < newer (max semantics).
        assertThat(savedAccount.getValue().getLastLoginSucceededAt()).isEqualTo(newer);
    }

    @Test
    @DisplayName("processed_events saveAndFlush 충돌(DataIntegrityViolation)은 swallow되고, 계정은 건드리지 않는다 (dedup-first)")
    void execute_dedupRaceCondition_doesNotPropagate() {
        Instant occurredAt = Instant.parse("2026-04-26T10:00:00Z");

        given(processedEventRepository.existsByEventId(EVENT_ID)).willReturn(false);
        given(processedEventRepository.saveAndFlush(any()))
                .willThrow(new DataIntegrityViolationException("uk_processed_events_event_id"));

        // No exception escapes the use case — saveAndFlush() forces the INSERT
        // SQL to execute inside the try/catch (vs. deferred flush at commit
        // time) so the constraint violation is caught here, not at commit.
        assertThatCode(() -> useCase.execute(EVENT_ID, ACCOUNT_ID, occurredAt))
                .doesNotThrowAnyException();

        // Dedup-first ordering: when the dedup INSERT loses the redelivery
        // race, the concurrent winner already updated the account, so we must
        // NOT touch the account at all.
        verify(accountRepository, never()).findById(any(), any());
        verify(accountRepository, never()).save(any());
    }

    private static Account activeAccount(String id, Instant lastLoginSucceededAt) {
        return Account.reconstitute(
                id,
                TenantId.FAN_PLATFORM,
                "user@example.com",
                "hash",
                AccountStatus.ACTIVE,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                null,
                lastLoginSucceededAt,
                null,
                0
        );
    }
}
