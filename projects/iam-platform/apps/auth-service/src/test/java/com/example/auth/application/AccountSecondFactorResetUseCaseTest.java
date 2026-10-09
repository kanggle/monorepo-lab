package com.example.auth.application;

import com.example.auth.application.AccountSecondFactorResetUseCase.Outcome;
import com.example.auth.application.AccountSecondFactorResetUseCase.Result;
import com.example.auth.application.exception.AccountServiceUnavailableException;
import com.example.auth.application.port.AccountServicePort;
import com.example.auth.application.result.AccountStatusWithTenantLookupResult;
import com.example.auth.domain.mfa.AccountTotp;
import com.example.auth.domain.repository.AccountTotpRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-771 S6 — the auth half of the administrator second-factor reset (admin-to-auth.md § POST
 * /internal/auth/accounts/{accountId}/second-factor/reset): a row is deleted whatever account-service says; only
 * «no row» asks account-service, and a failed read there is not guessed into either 404.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("계정 2단계 인증 관리자 리셋 — auth 쪽 (TASK-MONO-771 S6)")
class AccountSecondFactorResetUseCaseTest {

    private static final String ACCOUNT = "0199de70-0000-7000-8000-000000000776";
    private static final String OPERATOR = "op-s6";
    private static final Instant NOW = Instant.parse("2026-10-09T03:00:00Z");

    @Mock
    private AccountTotpRepository repository;

    @Mock
    private AccountServicePort accountServicePort;

    private AccountSecondFactorResetUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AccountSecondFactorResetUseCase(repository, accountServicePort, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("확정 등록 → 행 삭제 · RESET · wasConfirmed=true · account-service 는 묻지 않는다")
    void confirmed_deleted() {
        when(repository.findByAccountId(ACCOUNT)).thenReturn(Optional.of(confirmed()));

        Result r = useCase.reset(ACCOUNT, OPERATOR);

        assertThat(r.outcome()).isEqualTo(Outcome.RESET);
        assertThat(r.wasConfirmed()).isTrue();
        assertThat(r.resetAt()).isEqualTo(NOW);
        assertThat(r.accountId()).isEqualTo(ACCOUNT);
        verify(repository).deleteByAccountId(ACCOUNT);
        verifyNoInteractions(accountServicePort);
    }

    @Test
    @DisplayName("대기 행만 있음 → 그것도 지운다 · wasConfirmed=false")
    void pendingOnly_deleted() {
        when(repository.findByAccountId(ACCOUNT)).thenReturn(Optional.of(pending()));

        Result r = useCase.reset(ACCOUNT, OPERATOR);

        assertThat(r.outcome()).isEqualTo(Outcome.RESET);
        assertThat(r.wasConfirmed()).isFalse();
        verify(repository).deleteByAccountId(ACCOUNT);
        verifyNoInteractions(accountServicePort);
    }

    @Test
    @DisplayName("행 없음 + 계정 있음 → NOT_ENROLLED · 아무것도 지우지 않는다")
    void noRow_accountExists_notEnrolled() {
        when(repository.findByAccountId(ACCOUNT)).thenReturn(Optional.empty());
        when(accountServicePort.getAccountStatusAndTenant(ACCOUNT)).thenReturn(Optional.of(
                new AccountStatusWithTenantLookupResult(ACCOUNT, "fan-platform", "ACTIVE")));

        Result r = useCase.reset(ACCOUNT, OPERATOR);

        assertThat(r.outcome()).isEqualTo(Outcome.NOT_ENROLLED);
        assertThat(r.resetAt()).isNull();
        verify(repository, never()).deleteByAccountId(anyString());
    }

    @Test
    @DisplayName("행 없음 + account-service 404 → ACCOUNT_NOT_FOUND")
    void noRow_noAccount_accountNotFound() {
        when(repository.findByAccountId(ACCOUNT)).thenReturn(Optional.empty());
        when(accountServicePort.getAccountStatusAndTenant(ACCOUNT)).thenReturn(Optional.empty());

        assertThat(useCase.reset(ACCOUNT, OPERATOR).outcome()).isEqualTo(Outcome.ACCOUNT_NOT_FOUND);
        verify(repository, never()).deleteByAccountId(anyString());
    }

    @Test
    @DisplayName("🔴 행 없음 + account-service 읽기 실패 → 예외 전파(503) — «없음» 둘 중 하나로 추측하지 않는다")
    void noRow_lookupFails_propagates() {
        when(repository.findByAccountId(ACCOUNT)).thenReturn(Optional.empty());
        when(accountServicePort.getAccountStatusAndTenant(ACCOUNT))
                .thenThrow(new AccountServiceUnavailableException("down", new RuntimeException()));

        assertThatThrownBy(() -> useCase.reset(ACCOUNT, OPERATOR))
                .isInstanceOf(AccountServiceUnavailableException.class);
        verify(repository, never()).deleteByAccountId(anyString());
    }

    // ------------------------------------------------------------------ fixtures

    private static AccountTotp pending() {
        return AccountTotp.pending(ACCOUNT, "fan-platform", new byte[]{1, 2, 3}, "v1",
                NOW.minusSeconds(60));
    }

    private static AccountTotp confirmed() {
        return new AccountTotp(ACCOUNT, "fan-platform", new byte[]{1, 2, 3}, "v1",
                NOW.minusSeconds(3600), List.of("h$1", "h$2"), 1L, NOW.minusSeconds(3600),
                NOW.minusSeconds(7200), NOW.minusSeconds(3600), 3);
    }
}
