package com.example.admin.application;

import com.example.admin.application.exception.OperatorUnauthorizedException;
import com.example.admin.application.port.AdminOperatorPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Instant;
import java.util.Optional;

import static com.example.admin.application.OperatorUseCaseTestSupport.operator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-306 — unit tests for the self-serve operator profile mutation use
 * case. Verifies the 4 canonical cases from the spec § Scope > Tests:
 * <ol>
 *   <li>set to UUID → column UPDATE + audit row INSERT (both observed via
 *       mock verifications);</li>
 *   <li>set to {@code null} → column UPDATE (with null) + audit row INSERT;</li>
 *   <li>operator row not resolvable → {@link OperatorUnauthorizedException},
 *       no column UPDATE, no audit row;</li>
 *   <li>optimistic-lock conflict on the column UPDATE → exception propagates
 *       up; audit row inserted only if the column UPDATE returned (unit
 *       level — transaction rollback is integration-level concern).</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class UpdateOwnOperatorProfileUseCaseTest {

    @Mock AdminOperatorPort operatorPort;
    @Mock AdminActionAuditor auditor;

    @InjectMocks UpdateOwnOperatorProfileUseCase useCase;

    private static final String SELF_UUID = "00000000-0000-7000-8000-000000000099";
    private static final OperatorContext CALLER =
            new OperatorContext(SELF_UUID, "jti-test");

    @Test
    void update_set_to_uuid_writes_column_and_audit_row() {
        AdminOperatorPort.OperatorView op = operator(10L, SELF_UUID, "self@example.com", "ACTIVE");
        when(operatorPort.findByOperatorId(SELF_UUID)).thenReturn(Optional.of(op));

        String newValue = "01928c4a-7e9f-7c00-9a40-d2b1f5e8a000";
        useCase.update(CALLER, newValue);

        // Column UPDATE was invoked with the new value
        verify(operatorPort, times(1))
                .changeFinanceDefaultAccountId(eq(10L), eq(newValue), any(Instant.class));

        // Audit row was recorded with the canonical shape
        ArgumentCaptor<AdminActionAuditor.AuditRecord> captor =
                ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        verify(auditor, times(1)).record(captor.capture());
        AdminActionAuditor.AuditRecord rec = captor.getValue();
        assertThat(rec.actionCode()).isEqualTo(ActionCode.OPERATOR_PROFILE_UPDATE);
        assertThat(rec.operator().operatorId()).isEqualTo(SELF_UUID);
        assertThat(rec.targetType()).isEqualTo("OPERATOR");
        assertThat(rec.targetId()).isEqualTo(SELF_UUID);
        assertThat(rec.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(rec.reason()).isEqualTo(AdminActionAuditor.REASON_SELF_PROFILE_UPDATE);
        assertThat(rec.downstreamDetail())
                .as("audit detail MUST be null — the new value is NOT logged into the audit detail column (R4/A3)")
                .isNull();
    }

    @Test
    void update_set_to_null_clears_column_and_writes_audit_row() {
        AdminOperatorPort.OperatorView op = operator(10L, SELF_UUID, "self@example.com", "ACTIVE");
        when(operatorPort.findByOperatorId(SELF_UUID)).thenReturn(Optional.of(op));

        useCase.update(CALLER, null);

        verify(operatorPort, times(1))
                .changeFinanceDefaultAccountId(eq(10L), isNull(), any(Instant.class));

        ArgumentCaptor<AdminActionAuditor.AuditRecord> captor =
                ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        verify(auditor, times(1)).record(captor.capture());
        AdminActionAuditor.AuditRecord rec = captor.getValue();
        assertThat(rec.actionCode()).isEqualTo(ActionCode.OPERATOR_PROFILE_UPDATE);
        assertThat(rec.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(rec.reason()).isEqualTo(AdminActionAuditor.REASON_SELF_PROFILE_UPDATE);
    }

    @Test
    void update_operator_not_found_throws_unauthorized_and_writes_nothing() {
        when(operatorPort.findByOperatorId(SELF_UUID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.update(CALLER, "any-value"))
                .isInstanceOf(OperatorUnauthorizedException.class);

        verify(operatorPort, never())
                .changeFinanceDefaultAccountId(anyLong(), anyString(), any(Instant.class));
        verify(auditor, never()).record(any(AdminActionAuditor.AuditRecord.class));
    }

    @Test
    void update_optimistic_lock_on_save_propagates_and_skips_audit() {
        AdminOperatorPort.OperatorView op = operator(10L, SELF_UUID, "self@example.com", "ACTIVE");
        when(operatorPort.findByOperatorId(SELF_UUID)).thenReturn(Optional.of(op));
        doThrow(new ObjectOptimisticLockingFailureException(
                "admin_operators", 10L))
                .when(operatorPort).changeFinanceDefaultAccountId(eq(10L), anyString(), any(Instant.class));

        assertThatThrownBy(() -> useCase.update(CALLER, "stale-value"))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // Audit row MUST NOT have been written: the column UPDATE threw
        // before reaching the auditor call.
        verify(auditor, never()).record(any(AdminActionAuditor.AuditRecord.class));
    }
}
