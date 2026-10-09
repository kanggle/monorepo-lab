package com.example.admin.application;

import com.example.admin.application.exception.AccountSecondFactorNotEnrolledException;
import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.IdempotencyKeyConflictException;
import com.example.admin.application.exception.NonRetryableDownstreamException;
import com.example.admin.application.exception.ReasonRequiredException;
import com.example.admin.application.exception.TargetAccountNotFoundException;
import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.port.AccountSecondFactorResetPort;
import com.example.admin.application.port.OperatorLookupPort;
import com.example.admin.domain.rbac.Permission;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-771 S6 (OD-6) — {@code POST /api/admin/accounts/{accountId}/2fa/reset}: platform-scope second gate,
 * idempotency, and the A10 audit shape (IN_PROGRESS → SUCCESS | FAILURE, every started action completed), plus the
 * mapping of auth-service's two 404s onto the public contract.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("계정 2단계 인증 리셋 — admin 유스케이스 (TASK-MONO-771 S6)")
class AccountSecondFactorResetUseCaseTest {

    private static final String ACCOUNT = "acc-s6";
    private static final String OPERATOR_ID = "op-s6";
    private static final OperatorContext OP = new OperatorContext(OPERATOR_ID, "jti-1");
    private static final ResetAccountSecondFactorCommand CMD =
            new ResetAccountSecondFactorCommand(ACCOUNT, "본인 확인: 신분증 대조", "T-1", "idem-1", OP);

    @Mock AccountSecondFactorResetPort resetPort;
    @Mock OperatorLookupPort operatorLookupPort;
    @Mock AdminActionAuditor auditor;
    @InjectMocks AccountSecondFactorResetUseCase useCase;

    /**
     * A premise of the positive paths, not their subject — lenient, so removing the platform-scope gate reddens only
     * the two deny tests that are about it (bite), instead of every test through an unused-stub error.
     */
    private void platformOperator() {
        lenient().when(operatorLookupPort.findByOperatorId(OPERATOR_ID))
                .thenReturn(Optional.of(new OperatorLookupPort.OperatorLookupRef(1L, OPERATOR_ID, "*")));
    }

    private void freshKey() {
        when(auditor.isIdempotencyKeyUsed(OPERATOR_ID, ActionCode.ACCOUNT_2FA_RESET, "idem-1")).thenReturn(false);
        when(auditor.newAuditId()).thenReturn("audit-1");
    }

    @Test
    @DisplayName("플랫폼 운영자 → 하류 리셋 · SUCCESS 완료 행(detail wasConfirmed=true) · 응답")
    void platform_success() {
        platformOperator();
        freshKey();
        Instant resetAt = Instant.parse("2026-10-09T03:00:00Z");
        when(resetPort.reset(ACCOUNT, OPERATOR_ID, CMD.reason(), "idem-1"))
                .thenReturn(new AccountSecondFactorResetPort.ResetResult(ACCOUNT, resetAt, true));

        ResetAccountSecondFactorResult r = useCase.reset(CMD);

        assertThat(r.accountId()).isEqualTo(ACCOUNT);
        assertThat(r.operatorId()).isEqualTo(OPERATOR_ID);
        assertThat(r.resetAt()).isEqualTo(resetAt);
        assertThat(r.auditId()).isEqualTo("audit-1");
        ArgumentCaptor<AdminActionAuditor.StartRecord> start = ArgumentCaptor.forClass(AdminActionAuditor.StartRecord.class);
        verify(auditor).recordStart(start.capture());
        assertThat(start.getValue().actionCode()).isEqualTo(ActionCode.ACCOUNT_2FA_RESET);
        assertThat(start.getValue().targetType()).isEqualTo("ACCOUNT");
        assertThat(start.getValue().targetId()).isEqualTo(ACCOUNT);
        assertThat(start.getValue().reason()).isEqualTo("본인 확인: 신분증 대조");
        ArgumentCaptor<AdminActionAuditor.CompletionRecord> done =
                ArgumentCaptor.forClass(AdminActionAuditor.CompletionRecord.class);
        verify(auditor).recordCompletion(done.capture());
        assertThat(done.getValue().outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(done.getValue().downstreamDetail()).isEqualTo("wasConfirmed=true");
    }

    @Test
    @DisplayName("🔴 OD-6 2차 게이트: 키 보유 + 테넌트 홈 운영자 → 403 TENANT_SCOPE_DENIED · DENIED 행 · 하류 0 · 감사 시작 0")
    void tenantScopedHolder_denied() {
        when(operatorLookupPort.findByOperatorId(OPERATOR_ID))
                .thenReturn(Optional.of(new OperatorLookupPort.OperatorLookupRef(1L, OPERATOR_ID, "acme-corp")));

        assertThatThrownBy(() -> useCase.reset(CMD)).isInstanceOf(TenantScopeDeniedException.class);

        verify(auditor).recordCrossTenantDenied(OP, "acme-corp", ActionCode.ACCOUNT_2FA_RESET,
                Permission.ACCOUNT_2FA_RESET, "*");
        verify(auditor, never()).recordStart(any());
        verifyNoInteractions(resetPort);
    }

    @Test
    @DisplayName("운영자 행 없음 → 403 TENANT_SCOPE_DENIED · 하류 0")
    void unknownOperator_denied() {
        when(operatorLookupPort.findByOperatorId(OPERATOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.reset(CMD)).isInstanceOf(TenantScopeDeniedException.class);
        verifyNoInteractions(resetPort);
    }

    @Test
    @DisplayName("사유 공백 → 400 REASON_REQUIRED · 아무것도 묻지 않는다")
    void blankReason_rejectedFirst() {
        ResetAccountSecondFactorCommand blank = new ResetAccountSecondFactorCommand(ACCOUNT, " ", null, "idem-1", OP);

        assertThatThrownBy(() -> useCase.reset(blank)).isInstanceOf(ReasonRequiredException.class);
        verifyNoInteractions(operatorLookupPort, auditor, resetPort);
    }

    @Test
    @DisplayName("같은 운영자 · 같은 키 재사용 → 409 · 감사 시작 0 · 하류 0")
    void reusedKey_409() {
        platformOperator();
        when(auditor.isIdempotencyKeyUsed(OPERATOR_ID, ActionCode.ACCOUNT_2FA_RESET, "idem-1")).thenReturn(true);

        assertThatThrownBy(() -> useCase.reset(CMD)).isInstanceOf(IdempotencyKeyConflictException.class);
        verify(auditor, never()).recordStart(any());
        verifyNoInteractions(resetPort);
    }

    @Test
    @DisplayName("auth 404 TOTP_NOT_ENROLLED → 공개 404 TOTP_NOT_ENROLLED · FAILURE 행")
    void notEnrolled_mapped() {
        platformOperator();
        freshKey();
        when(resetPort.reset(ACCOUNT, OPERATOR_ID, CMD.reason(), "idem-1")).thenThrow(
                new NonRetryableDownstreamException("auth-service error 404", null, 404, "TOTP_NOT_ENROLLED"));

        assertThatThrownBy(() -> useCase.reset(CMD)).isInstanceOf(AccountSecondFactorNotEnrolledException.class);
        assertFailureRow("TOTP_NOT_ENROLLED");
    }

    @Test
    @DisplayName("auth 404 ACCOUNT_NOT_FOUND → 공개 404 ACCOUNT_NOT_FOUND · FAILURE 행")
    void accountNotFound_mapped() {
        platformOperator();
        freshKey();
        when(resetPort.reset(ACCOUNT, OPERATOR_ID, CMD.reason(), "idem-1")).thenThrow(
                new NonRetryableDownstreamException("auth-service error 404", null, 404, "ACCOUNT_NOT_FOUND"));

        assertThatThrownBy(() -> useCase.reset(CMD)).isInstanceOf(TargetAccountNotFoundException.class);
        assertFailureRow("ACCOUNT_NOT_FOUND");
    }

    @Test
    @DisplayName("auth 5xx · 그 밖 → 503 DOWNSTREAM_ERROR 그대로 · FAILURE 행 (A10)")
    void downstream5xx_failureRow() {
        platformOperator();
        freshKey();
        DownstreamFailureException boom = new DownstreamFailureException("auth-service error 500", null);
        when(resetPort.reset(ACCOUNT, OPERATOR_ID, CMD.reason(), "idem-1")).thenThrow(boom);

        assertThatThrownBy(() -> useCase.reset(CMD)).isSameAs(boom);
        assertFailureRow("auth-service error 500");
    }

    @Test
    @DisplayName("circuit open → 그대로 전파(503 CIRCUIT_OPEN) · FAILURE 행")
    void circuitOpen_failureRow() {
        platformOperator();
        freshKey();
        CallNotPermittedException open = mock(CallNotPermittedException.class);
        when(resetPort.reset(ACCOUNT, OPERATOR_ID, CMD.reason(), "idem-1")).thenThrow(open);

        assertThatThrownBy(() -> useCase.reset(CMD)).isSameAs(open);
        ArgumentCaptor<AdminActionAuditor.CompletionRecord> done =
                ArgumentCaptor.forClass(AdminActionAuditor.CompletionRecord.class);
        verify(auditor).recordCompletion(done.capture());
        assertThat(done.getValue().outcome()).isEqualTo(Outcome.FAILURE);
        assertThat(done.getValue().downstreamDetail()).startsWith("CIRCUIT_OPEN");
    }

    private void assertFailureRow(String detail) {
        verify(auditor).recordStart(any());
        ArgumentCaptor<AdminActionAuditor.CompletionRecord> done =
                ArgumentCaptor.forClass(AdminActionAuditor.CompletionRecord.class);
        verify(auditor).recordCompletion(done.capture());
        assertThat(done.getValue().outcome()).isEqualTo(Outcome.FAILURE);
        assertThat(done.getValue().downstreamDetail()).isEqualTo(detail);
    }
}
