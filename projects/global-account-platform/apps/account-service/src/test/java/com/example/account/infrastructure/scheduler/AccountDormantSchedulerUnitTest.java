package com.example.account.infrastructure.scheduler;

import com.example.account.application.command.ChangeStatusCommand;
import com.example.account.application.service.AccountStatusUseCase;
import com.example.account.domain.account.Account;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.status.StatusChangeReason;
import com.example.account.domain.tenant.TenantId;
import com.example.account.infrastructure.persistence.AccountJpaEntity;
import com.example.account.infrastructure.persistence.AccountJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountDormantScheduler 단위 테스트")
class AccountDormantSchedulerUnitTest {

    @Mock
    private AccountJpaRepository accountJpaRepository;

    @Mock
    private AccountStatusUseCase accountStatusUseCase;

    private MeterRegistry meterRegistry;

    private AccountDormantScheduler scheduler;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        scheduler = new AccountDormantScheduler(
                accountJpaRepository, accountStatusUseCase, meterRegistry);
    }

    @Test
    @DisplayName("365일 초과 미접속 ACTIVE 계정을 DORMANT로 전환한다")
    void runDormantBatch_transitionsEligibleAccounts() {
        AccountJpaEntity entity = buildEntity("acc-1");
        given(accountJpaRepository.findActiveDormantCandidates(any(Instant.class)))
                .willReturn(List.of(entity));

        scheduler.runDormantBatch();

        ArgumentCaptor<ChangeStatusCommand> captor = ArgumentCaptor.forClass(ChangeStatusCommand.class);
        verify(accountStatusUseCase).changeStatus(captor.capture());
        ChangeStatusCommand cmd = captor.getValue();
        assertThat(cmd.accountId()).isEqualTo("acc-1");
        assertThat(cmd.targetStatus()).isEqualTo(AccountStatus.DORMANT);
        assertThat(cmd.reason()).isEqualTo(StatusChangeReason.DORMANT_365D);
        assertThat(cmd.actorType()).isEqualTo("system");
        assertThat(cmd.actorId()).isNull();

        // Metrics: processed=1, failed counter not registered (lazy)
        assertThat(meterRegistry.counter("scheduler.dormant.processed").count()).isEqualTo(1.0);
        assertThat(meterRegistry.find("scheduler.dormant.failed").counter()).isNull();
        // Duration timer always recorded
        assertThat(meterRegistry.timer("scheduler.dormant.duration_ms").count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("개별 계정 전환 실패 시 해당 계정 skip, 나머지 계속 처리된다")
    void runDormantBatch_skipsFailedAccountAndContinues() {
        AccountJpaEntity fail = buildEntity("acc-fail");
        AccountJpaEntity ok   = buildEntity("acc-ok");
        given(accountJpaRepository.findActiveDormantCandidates(any(Instant.class)))
                .willReturn(List.of(fail, ok));
        willThrow(new RuntimeException("DB error"))
                .given(accountStatusUseCase).changeStatus(commandFor("acc-fail"));

        scheduler.runDormantBatch();

        verify(accountStatusUseCase, times(2)).changeStatus(any());
        // Metrics: 1 processed, 1 failed
        assertThat(meterRegistry.counter("scheduler.dormant.processed").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("scheduler.dormant.failed").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("대상 계정이 없으면 changeStatus를 호출하지 않는다")
    void runDormantBatch_noCandidates_doesNothing() {
        given(accountJpaRepository.findActiveDormantCandidates(any(Instant.class)))
                .willReturn(List.of());

        scheduler.runDormantBatch();

        verify(accountStatusUseCase, never()).changeStatus(any());
        // Counters not registered (no increments)
        assertThat(meterRegistry.find("scheduler.dormant.processed").counter()).isNull();
        assertThat(meterRegistry.find("scheduler.dormant.failed").counter()).isNull();
        // Duration timer still recorded
        assertThat(meterRegistry.timer("scheduler.dormant.duration_ms").count()).isEqualTo(1L);
    }

    private AccountJpaEntity buildEntity(String id) {
        Account account = Account.reconstitute(
                id, TenantId.FAN_PLATFORM, "user@example.com", "hash",
                AccountStatus.ACTIVE,
                Instant.now().minusSeconds(400L * 86400),
                Instant.now(),
                null,
                Instant.now().minusSeconds(400L * 86400),
                null,
                0);
        return AccountJpaEntity.fromDomain(account);
    }

    private ChangeStatusCommand commandFor(String accountId) {
        return new ChangeStatusCommand(accountId, AccountStatus.DORMANT,
                StatusChangeReason.DORMANT_365D, "system", null, null);
    }
}
