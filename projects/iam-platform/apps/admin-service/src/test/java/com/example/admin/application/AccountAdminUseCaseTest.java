package com.example.admin.application;

import com.example.admin.application.exception.AuditFailureException;
import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.IdempotencyKeyConflictException;
import com.example.admin.application.exception.NonRetryableDownstreamException;
import com.example.admin.application.exception.ReasonRequiredException;
import com.example.admin.application.exception.StateTransitionInvalidException;
import com.example.admin.application.exception.TargetAccountNotFoundException;
import com.example.admin.infrastructure.client.AccountServiceClient;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class AccountAdminUseCaseTest {

    @Mock
    AccountServiceClient accountServiceClient;

    @Mock
    AdminActionAuditor auditor;

    @InjectMocks
    AccountAdminUseCase useCase;

    private OperatorContext operator() {
        return new OperatorContext("op-1", "jti-1");
    }

    @Test
    void lock_success_records_in_progress_then_success_completion() {
        when(auditor.newAuditId()).thenReturn("audit-1");
        when(accountServiceClient.lock(anyString(), anyString(), anyString(), any(), anyString(), anyString()))
                .thenReturn(new AccountServiceClient.LockResponse(
                        "acc-1", "ACTIVE", "LOCKED", Instant.now(), null));

        LockAccountResult r = useCase.lock(new LockAccountCommand(
                "acc-1", "fraud", "T-1", "idemp-1", operator(), "fan-platform"));

        assertThat(r.auditId()).isEqualTo("audit-1");
        assertThat(r.currentStatus()).isEqualTo("LOCKED");

        // Critical: recordStart must run BEFORE downstream call, then recordCompletion after.
        InOrder order = inOrder(auditor, accountServiceClient);
        order.verify(auditor).recordStart(any());
        order.verify(accountServiceClient).lock(anyString(), anyString(), anyString(), any(), anyString(), anyString());
        order.verify(auditor).recordCompletion(any());
    }

    @Test
    void lock_downstream_failure_records_failure_completion_and_throws() {
        when(auditor.newAuditId()).thenReturn("audit-2");
        doThrow(new DownstreamFailureException("boom"))
                .when(accountServiceClient).lock(anyString(), anyString(), anyString(), any(), anyString(), anyString());

        assertThatThrownBy(() -> useCase.lock(new LockAccountCommand(
                "acc-1", "fraud", null, "idemp-2", operator(), "fan-platform")))
                .isInstanceOf(DownstreamFailureException.class);

        verify(auditor, times(1)).recordStart(any());
        verify(auditor, times(1)).recordCompletion(any());
    }

    @Test
    void lock_missing_reason_throws_reason_required_before_any_audit() {
        assertThatThrownBy(() -> useCase.lock(new LockAccountCommand(
                "acc-1", "", null, "idemp-3", operator(), "fan-platform")))
                .isInstanceOf(ReasonRequiredException.class);

        verify(auditor, never()).recordStart(any());
        verify(auditor, never()).recordCompletion(any());
    }

    @Test
    void lock_circuit_open_records_failure_completion_and_rethrows() {
        // Regression for TASK-BE-033-fix: CallNotPermittedException must not be
        // swallowed by the generic RuntimeException branch — it must trigger an
        // outcome=FAILURE completion row (A10 fail-closed) and re-throw so the
        // presentation layer maps it to 503 CIRCUIT_OPEN.
        when(auditor.newAuditId()).thenReturn("audit-cb-1");
        CallNotPermittedException cbEx = CallNotPermittedException.createCallNotPermittedException(
                CircuitBreaker.of("accountService", CircuitBreakerConfig.ofDefaults()));
        doThrow(cbEx).when(accountServiceClient)
                .lock(anyString(), anyString(), anyString(), any(), anyString(), anyString());

        assertThatExceptionOfType(CallNotPermittedException.class)
                .isThrownBy(() -> useCase.lock(new LockAccountCommand(
                        "acc-1", "fraud", null, "idemp-cb-1", operator(), "fan-platform")));

        var captor = forClass(AdminActionAuditor.CompletionRecord.class);
        verify(auditor, times(1)).recordStart(any());
        verify(auditor, times(1)).recordCompletion(captor.capture());
        assertThat(captor.getValue().outcome()).isEqualTo(Outcome.FAILURE);
        assertThat(captor.getValue().downstreamDetail()).contains("CIRCUIT_OPEN");
    }

    @Test
    void unlock_circuit_open_records_failure_completion_and_rethrows() {
        when(auditor.newAuditId()).thenReturn("audit-cb-2");
        CallNotPermittedException cbEx = CallNotPermittedException.createCallNotPermittedException(
                CircuitBreaker.of("accountService", CircuitBreakerConfig.ofDefaults()));
        doThrow(cbEx).when(accountServiceClient)
                .unlock(anyString(), anyString(), anyString(), any(), anyString(), anyString());

        assertThatExceptionOfType(CallNotPermittedException.class)
                .isThrownBy(() -> useCase.unlock(new UnlockAccountCommand(
                        "acc-1", "restore", null, "idemp-cb-2", operator(), "fan-platform")));

        var captor = forClass(AdminActionAuditor.CompletionRecord.class);
        verify(auditor, times(1)).recordCompletion(captor.capture());
        assertThat(captor.getValue().outcome()).isEqualTo(Outcome.FAILURE);
        assertThat(captor.getValue().downstreamDetail()).contains("CIRCUIT_OPEN");
    }

    // ── TASK-MONO-735 ────────────────────────────────────────────────────────

    @Test
    void lock_replayed_idempotency_key_is_409_without_new_audit_row_or_downstream_call() {
        // The live 500: the console re-sent the same key after a failure; recordStart's INSERT
        // died on idx_admin_actions_idemp → AuditFailureException → 500 AUDIT_FAILURE.
        when(auditor.isIdempotencyKeyUsed("op-1", ActionCode.ACCOUNT_LOCK, "idemp-replay"))
                .thenReturn(true);

        assertThatThrownBy(() -> useCase.lock(new LockAccountCommand(
                "acc-1", "fraud", null, "idemp-replay", operator(), "*")))
                .isInstanceOf(IdempotencyKeyConflictException.class);

        verify(auditor, never()).recordStart(any());
        verify(auditor, never()).recordCompletion(any());
        verify(accountServiceClient, never()).lock(anyString(), anyString(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void unlock_replayed_idempotency_key_is_409() {
        when(auditor.isIdempotencyKeyUsed("op-1", ActionCode.ACCOUNT_UNLOCK, "idemp-replay"))
                .thenReturn(true);

        assertThatThrownBy(() -> useCase.unlock(new UnlockAccountCommand(
                "acc-1", "restore", null, "idemp-replay", operator(), "*")))
                .isInstanceOf(IdempotencyKeyConflictException.class);

        verify(auditor, never()).recordStart(any());
    }

    @Test
    void lock_downstream_404_records_failure_and_surfaces_account_not_found_not_503() {
        when(auditor.newAuditId()).thenReturn("audit-404");
        doThrow(new NonRetryableDownstreamException("account-service error 404", null, 404, "ACCOUNT_NOT_FOUND"))
                .when(accountServiceClient).lock(anyString(), anyString(), anyString(), any(), anyString(), anyString());

        assertThatThrownBy(() -> useCase.lock(new LockAccountCommand(
                "acc-1", "fraud", null, "idemp-404", operator(), "*")))
                .isInstanceOf(TargetAccountNotFoundException.class)
                // no longer the DownstreamFailureException family that maps to 503
                .isNotInstanceOf(DownstreamFailureException.class);

        var captor = forClass(AdminActionAuditor.CompletionRecord.class);
        verify(auditor, times(1)).recordStart(any());
        verify(auditor, times(1)).recordCompletion(captor.capture());
        assertThat(captor.getValue().outcome()).isEqualTo(Outcome.FAILURE);
    }

    @Test
    void lock_downstream_409_surfaces_state_transition_invalid() {
        when(auditor.newAuditId()).thenReturn("audit-409");
        doThrow(new NonRetryableDownstreamException("account-service error 409", null, 409, "STATE_TRANSITION_INVALID"))
                .when(accountServiceClient).lock(anyString(), anyString(), anyString(), any(), anyString(), anyString());

        assertThatThrownBy(() -> useCase.lock(new LockAccountCommand(
                "acc-1", "fraud", null, "idemp-409", operator(), "ecommerce")))
                .isInstanceOf(StateTransitionInvalidException.class);

        verify(auditor, times(1)).recordCompletion(any());
    }

    @Test
    void lock_downstream_other_4xx_stays_downstream_failure() {
        when(auditor.newAuditId()).thenReturn("audit-400");
        NonRetryableDownstreamException bad =
                new NonRetryableDownstreamException("account-service error 400", null, 400, "VALIDATION_ERROR");
        doThrow(bad)
                .when(accountServiceClient).lock(anyString(), anyString(), anyString(), any(), anyString(), anyString());

        assertThatThrownBy(() -> useCase.lock(new LockAccountCommand(
                "acc-1", "fraud", null, "idemp-400", operator(), "ecommerce")))
                .isSameAs(bad);
    }

    @Test
    void lock_audit_start_failure_aborts_before_downstream() {
        when(auditor.newAuditId()).thenReturn("audit-5");
        doThrow(new AuditFailureException("db down", new RuntimeException()))
                .when(auditor).recordStart(any());

        assertThatThrownBy(() -> useCase.lock(new LockAccountCommand(
                "acc-1", "fraud", null, "idemp-5", operator(), "fan-platform")))
                .isInstanceOf(AuditFailureException.class);

        // A10 fail-closed: downstream must NOT be called when audit INSERT fails.
        verify(accountServiceClient, never()).lock(anyString(), anyString(), anyString(), any(), anyString(), anyString());
    }
}
