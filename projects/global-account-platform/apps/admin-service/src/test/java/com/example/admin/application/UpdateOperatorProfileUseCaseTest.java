package com.example.admin.application;

import com.example.admin.application.exception.OperatorNotFoundException;
import com.example.admin.application.exception.SelfProfileUpdateForbiddenException;
import com.example.admin.application.exception.TenantScopeDeniedException;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-307 — unit tests for the admin-on-behalf-of operator profile
 * mutation use case (the cross-operator counterpart of
 * {@link UpdateOwnOperatorProfileUseCase}).
 *
 * <p>Verifies the 5 canonical cases from the spec § Scope > Tests:
 * <ol>
 *   <li>cross-tenant target by platform-scope caller → save + audit (with
 *       {@code target_id != caller.operator_id},
 *       {@code permission_used=operator.manage}, caller-typed reason);</li>
 *   <li>same-tenant target by non-platform caller → save + audit;</li>
 *   <li>cross-tenant target by non-platform caller → {@code TenantScopeDeniedException},
 *       no save, no audit;</li>
 *   <li>target not found → {@code OperatorNotFoundException}, no save, no audit;</li>
 *   <li>self via admin path → {@code SelfProfileUpdateForbiddenException}, no
 *       target lookup, no save, no audit (cheapest check first).</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class UpdateOperatorProfileUseCaseTest {

    @Mock AdminOperatorPort operatorPort;
    @Mock AdminActionAuditor auditor;

    @InjectMocks UpdateOperatorProfileUseCase useCase;

    private static final String CALLER_UUID  = "00000000-0000-7000-8000-0000000000aa";
    private static final String TARGET_UUID  = "00000000-0000-7000-8000-0000000000bb";
    private static final OperatorContext CALLER =
            new OperatorContext(CALLER_UUID, "jti-test");
    private static final String REASON = "onboarding bulk-provision";

    @Test
    void update_platform_scope_caller_cross_tenant_target_writes_column_and_audit_row() {
        AdminOperatorPort.OperatorView caller = operator(10L, CALLER_UUID, "platform@example.com", "ACTIVE", "*");
        AdminOperatorPort.OperatorView target = operator(20L, TARGET_UUID, "u@example.com", "ACTIVE", "wms");
        when(operatorPort.findByOperatorId(TARGET_UUID)).thenReturn(Optional.of(target));
        when(operatorPort.findByOperatorId(CALLER_UUID)).thenReturn(Optional.of(caller));

        String newValue = "01928c4a-7e9f-7c00-9a40-d2b1f5e8a000";
        useCase.update(TARGET_UUID, newValue, CALLER, REASON);

        verify(operatorPort, times(1))
                .changeFinanceDefaultAccountId(eq(20L), eq(newValue), any(Instant.class));

        ArgumentCaptor<AdminActionAuditor.AuditRecord> captor =
                ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        ArgumentCaptor<String> permCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditor, times(1)).recordWithPermission(captor.capture(), permCaptor.capture());

        AdminActionAuditor.AuditRecord rec = captor.getValue();
        assertThat(rec.actionCode()).isEqualTo(ActionCode.OPERATOR_PROFILE_UPDATE);
        assertThat(rec.operator().operatorId()).isEqualTo(CALLER_UUID);
        assertThat(rec.targetType()).isEqualTo("OPERATOR");
        assertThat(rec.targetId())
                .as("audit target_id is the TARGET operator's public UUID, NOT the caller's")
                .isEqualTo(TARGET_UUID)
                .isNotEqualTo(CALLER_UUID);
        assertThat(rec.targetTenantId())
                .as("audit target_tenant_id is the TARGET operator's tenant, NOT the caller's '*'")
                .isEqualTo("wms");
        assertThat(rec.reason())
                .as("admin path uses caller-typed X-Operator-Reason, NOT the <self_profile_update> constant")
                .isEqualTo(REASON)
                .isNotEqualTo(AdminActionAuditor.REASON_SELF_PROFILE_UPDATE);
        assertThat(rec.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(rec.downstreamDetail())
                .as("audit detail MUST be null — the new value is NOT logged into the audit detail column (R4/A3)")
                .isNull();
        assertThat(permCaptor.getValue())
                .as("admin path stamps the concrete grantable operator.manage permission, NOT the <self_action> sentinel")
                .isEqualTo(Permission.OPERATOR_MANAGE)
                .isNotEqualTo(AdminActionAuditor.PERMISSION_SELF_ACTION);
    }

    @Test
    void update_same_tenant_caller_target_writes_column_and_audit_row() {
        AdminOperatorPort.OperatorView caller = operator(10L, CALLER_UUID, "wms-admin@example.com", "ACTIVE", "wms");
        AdminOperatorPort.OperatorView target = operator(20L, TARGET_UUID, "u@example.com", "ACTIVE", "wms");
        when(operatorPort.findByOperatorId(TARGET_UUID)).thenReturn(Optional.of(target));
        when(operatorPort.findByOperatorId(CALLER_UUID)).thenReturn(Optional.of(caller));

        useCase.update(TARGET_UUID, "any-uuid", CALLER, REASON);

        verify(operatorPort, times(1))
                .changeFinanceDefaultAccountId(eq(20L), anyString(), any(Instant.class));
        verify(auditor, times(1)).recordWithPermission(any(AdminActionAuditor.AuditRecord.class), anyString());
    }

    @Test
    void update_cross_tenant_non_platform_caller_throws_tenant_scope_denied() {
        AdminOperatorPort.OperatorView caller = operator(10L, CALLER_UUID, "wms-admin@example.com", "ACTIVE", "wms");
        AdminOperatorPort.OperatorView target = operator(20L, TARGET_UUID, "scm-user@example.com", "ACTIVE", "scm");
        when(operatorPort.findByOperatorId(TARGET_UUID)).thenReturn(Optional.of(target));
        when(operatorPort.findByOperatorId(CALLER_UUID)).thenReturn(Optional.of(caller));

        assertThatThrownBy(() -> useCase.update(TARGET_UUID, "any-uuid", CALLER, REASON))
                .isInstanceOf(TenantScopeDeniedException.class);

        verify(operatorPort, never())
                .changeFinanceDefaultAccountId(anyLong(), anyString(), any(Instant.class));
        verify(auditor, never()).recordWithPermission(any(AdminActionAuditor.AuditRecord.class), anyString());
        verify(auditor, never()).record(any(AdminActionAuditor.AuditRecord.class));
    }

    @Test
    void update_target_not_found_throws_operator_not_found_and_writes_nothing() {
        when(operatorPort.findByOperatorId(TARGET_UUID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.update(TARGET_UUID, "any-uuid", CALLER, REASON))
                .isInstanceOf(OperatorNotFoundException.class);

        verify(operatorPort, never())
                .changeFinanceDefaultAccountId(anyLong(), anyString(), any(Instant.class));
        verify(auditor, never()).recordWithPermission(any(AdminActionAuditor.AuditRecord.class), anyString());
    }

    @Test
    void update_self_via_admin_path_throws_self_forbidden_without_lookup() {
        // Caller targets their own operatorId
        assertThatThrownBy(() -> useCase.update(CALLER_UUID, "any-uuid", CALLER, REASON))
                .isInstanceOf(SelfProfileUpdateForbiddenException.class);

        // CRITICAL: self-check must happen BEFORE any lookup — no port call.
        verify(operatorPort, never()).findByOperatorId(anyString());
        verify(operatorPort, never())
                .changeFinanceDefaultAccountId(anyLong(), anyString(), any(Instant.class));
        verify(auditor, never()).recordWithPermission(any(AdminActionAuditor.AuditRecord.class), anyString());
    }
}
