package com.example.admin.application;

import com.example.admin.application.exception.AuditFailureException;
import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.ReasonRequiredException;
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
        when(accountServiceClient.lock(anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(new AccountServiceClient.LockResponse(
                        "acc-1", "ACTIVE", "LOCKED", Instant.now(), null));

        LockAccountResult r = useCase.lock(new LockAccountCommand(
                "acc-1", "fraud", "T-1", "idemp-1", operator()));

        assertThat(r.auditId()).isEqualTo("audit-1");
        assertThat(r.currentStatus()).isEqualTo("LOCKED");

        // Critical: recordStart must run BEFORE downstream call, then recordCompletion after.
        InOrder order = inOrder(auditor, accountServiceClient);
        order.verify(auditor).recordStart(any());
        order.verify(accountServiceClient).lock(anyString(), anyString(), anyString(), any(), anyString());
        order.verify(auditor).recordCompletion(any());
    }

    @Test
    void lock_downstream_failure_records_failure_completion_and_throws() {
        when(auditor.newAuditId()).thenReturn("audit-2");
        doThrow(new DownstreamFailureException("boom"))
                .when(accountServiceClient).lock(anyString(), anyString(), anyString(), any(), anyString());

        assertThatThrownBy(() -> useCase.lock(new LockAccountCommand(
                "acc-1", "fraud", null, "idemp-2", operator())))
                .isInstanceOf(DownstreamFailureException.class);

        verify(auditor, times(1)).recordStart(any());
        verify(auditor, times(1)).recordCompletion(any());
    }

    @Test
    void lock_missing_reason_throws_reason_required_before_any_audit() {
        assertThatThrownBy(() -> useCase.lock(new LockAccountCommand(
                "acc-1", "", null, "idemp-3", operator())))
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
                .lock(anyString(), anyString(), anyString(), any(), anyString());

        assertThatExceptionOfType(CallNotPermittedException.class)
                .isThrownBy(() -> useCase.lock(new LockAccountCommand(
                        "acc-1", "fraud", null, "idemp-cb-1", operator())));

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
                .unlock(anyString(), anyString(), anyString(), any(), anyString());

        assertThatExceptionOfType(CallNotPermittedException.class)
                .isThrownBy(() -> useCase.unlock(new UnlockAccountCommand(
                        "acc-1", "restore", null, "idemp-cb-2", operator())));

        var captor = forClass(AdminActionAuditor.CompletionRecord.class);
        verify(auditor, times(1)).recordCompletion(captor.capture());
        assertThat(captor.getValue().outcome()).isEqualTo(Outcome.FAILURE);
        assertThat(captor.getValue().downstreamDetail()).contains("CIRCUIT_OPEN");
    }

    @Test
    void lock_audit_start_failure_aborts_before_downstream() {
        when(auditor.newAuditId()).thenReturn("audit-5");
        doThrow(new AuditFailureException("db down", new RuntimeException()))
                .when(auditor).recordStart(any());

        assertThatThrownBy(() -> useCase.lock(new LockAccountCommand(
                "acc-1", "fraud", null, "idemp-5", operator())))
                .isInstanceOf(AuditFailureException.class);

        // A10 fail-closed: downstream must NOT be called when audit INSERT fails.
        verify(accountServiceClient, never()).lock(anyString(), anyString(), anyString(), any(), anyString());
    }
}
