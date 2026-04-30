package com.example.admin.application;

import com.example.admin.infrastructure.client.AuthServiceClient;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class SessionAdminUseCaseTest {

    @Mock
    AuthServiceClient authServiceClient;

    @Mock
    AdminActionAuditor auditor;

    @InjectMocks
    SessionAdminUseCase useCase;

    private OperatorContext operator() {
        return new OperatorContext("op-1", "jti-1");
    }

    @Test
    void revoke_circuit_open_records_failure_completion_and_rethrows() {
        // TASK-BE-033-fix: CB OPEN on auth-service force-logout must produce an
        // outcome=FAILURE admin_actions row before re-throwing so the handler
        // maps to 503 CIRCUIT_OPEN.
        when(auditor.newAuditId()).thenReturn("audit-cb-sess");
        CallNotPermittedException cbEx = CallNotPermittedException.createCallNotPermittedException(
                CircuitBreaker.of("authService", CircuitBreakerConfig.ofDefaults()));
        doThrow(cbEx).when(authServiceClient)
                .forceLogout(anyString(), anyString(), anyString(), any());

        assertThatExceptionOfType(CallNotPermittedException.class)
                .isThrownBy(() -> useCase.revoke(new RevokeSessionCommand(
                        "acc-1", "suspicious activity", "idemp-cb-s", operator())));

        ArgumentCaptor<AdminActionAuditor.CompletionRecord> captor =
                ArgumentCaptor.forClass(AdminActionAuditor.CompletionRecord.class);
        verify(auditor, times(1)).recordStart(any());
        verify(auditor, times(1)).recordCompletion(captor.capture());
        assertThat(captor.getValue().outcome()).isEqualTo(Outcome.FAILURE);
        assertThat(captor.getValue().downstreamDetail()).contains("CIRCUIT_OPEN");
    }
}
