package com.example.admin.application;

import com.example.admin.application.exception.OperatorNotFoundException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.domain.rbac.Permission;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static com.example.admin.application.OperatorUseCaseTestSupport.operator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-373 / ADR-MONO-034 U3 — unit tests for the operator↔identity UNLINK
 * use case (Docker-free, Mockito STRICT_STUBS).
 *
 * <p>Covers: unlink clears identity_id + audits; idempotent unlink-when-already-
 * unlinked → no-op success (still audited); operator not found → reject.
 */
@ExtendWith(MockitoExtension.class)
class UnlinkOperatorIdentityUseCaseTest {

    @Mock AdminOperatorPort operatorPort;
    @Mock AdminActionAuditor auditor;
    @Mock TenantScopeGuard tenantScopeGuard;

    @InjectMocks UnlinkOperatorIdentityUseCase useCase;

    private static final String OP_UUID = "00000000-0000-7000-8000-0000000000aa";
    private static final String TENANT = "wms";
    private static final String IDENTITY_ID = "idy-7777";
    private static final OperatorContext ACTOR = new OperatorContext("actor-uuid", "jti-1");
    private static final String REASON = "revoke dual-role link";

    @Test
    void unlink_clears_identity_and_audits() {
        AdminOperatorPort.OperatorView op =
                operator(10L, OP_UUID, "md@example.com", "ACTIVE", TENANT, null, IDENTITY_ID);
        when(operatorPort.findByOperatorId(OP_UUID)).thenReturn(Optional.of(op));

        UnlinkOperatorIdentityUseCase.UnlinkResult result =
                useCase.unlink(OP_UUID, ACTOR, REASON);

        assertThat(result.alreadyUnlinked()).isFalse();
        assertThat(result.previousIdentityId()).isEqualTo(IDENTITY_ID);

        verify(tenantScopeGuard, times(1)).requireTenantInScope(
                eq(ACTOR), eq(Permission.OPERATOR_MANAGE), eq(TENANT),
                eq(ActionCode.OPERATOR_IDENTITY_UNLINK));
        verify(operatorPort, times(1)).unlinkIdentity(eq(10L), any(Instant.class));

        ArgumentCaptor<AdminActionAuditor.AuditRecord> rec =
                ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        verify(auditor, times(1)).recordWithPermission(rec.capture(), eq(Permission.OPERATOR_MANAGE));
        assertThat(rec.getValue().actionCode()).isEqualTo(ActionCode.OPERATOR_IDENTITY_UNLINK);
        assertThat(rec.getValue().targetId()).isEqualTo(OP_UUID);
        assertThat(rec.getValue().outcome()).isEqualTo(Outcome.SUCCESS);
    }

    @Test
    void unlink_already_unlinked_is_noop_success_and_audits() {
        AdminOperatorPort.OperatorView op =
                operator(10L, OP_UUID, "md@example.com", "ACTIVE", TENANT, null, null);
        when(operatorPort.findByOperatorId(OP_UUID)).thenReturn(Optional.of(op));

        UnlinkOperatorIdentityUseCase.UnlinkResult result =
                useCase.unlink(OP_UUID, ACTOR, REASON);

        assertThat(result.alreadyUnlinked()).isTrue();
        assertThat(result.previousIdentityId()).isNull();
        // no-op: NO persistence write
        verify(operatorPort, never()).unlinkIdentity(anyLong(), any(Instant.class));
        // idempotent success still audited
        verify(auditor, times(1)).recordWithPermission(any(), eq(Permission.OPERATOR_MANAGE));
    }

    @Test
    void unlink_operator_not_found_rejects() {
        when(operatorPort.findByOperatorId(OP_UUID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.unlink(OP_UUID, ACTOR, REASON))
                .isInstanceOf(OperatorNotFoundException.class);

        verify(operatorPort, never()).unlinkIdentity(anyLong(), any(Instant.class));
        verify(auditor, never()).recordWithPermission(any(), anyString());
    }
}
