package com.example.admin.application.tenant;

import com.example.admin.application.ActionCode;
import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.OperatorContext;
import com.example.admin.application.Outcome;
import com.example.admin.application.event.TenantEventPublisher;
import com.example.admin.application.port.TenantProvisioningPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class UpdateTenantUseCaseTest {

    @Mock TenantProvisioningPort provisioningPort;
    @Mock AdminActionAuditor auditor;
    @Mock TenantEventPublisher tenantEventPublisher;
    // TASK-MONO-773 S4 — the first-admin rows · «정리» cancel · the recovery activation.
    @Mock com.example.admin.application.port.OperatorInvitationPort invitationPort;
    @Mock com.example.admin.application.OperatorInvitationWriter invitationWriter;
    @Mock TenantActivation tenantActivation;

    @InjectMocks UpdateTenantUseCase useCase;

    private static final OperatorContext OP = new OperatorContext("op-super", "jti-1");

    private static TenantSummary active(String displayName) {
        return new TenantSummary("wms", displayName, "B2B_ENTERPRISE", "ACTIVE",
                Instant.now(), Instant.now());
    }

    private static TenantSummary suspended(String displayName) {
        return new TenantSummary("wms", displayName, "B2B_ENTERPRISE", "SUSPENDED",
                Instant.now(), Instant.now());
    }

    @Test
    void display_name_change_emits_tenant_updated_audit() {
        when(auditor.newAuditId()).thenReturn("audit-1");
        when(provisioningPort.get("wms")).thenReturn(active("Old Name"));
        when(provisioningPort.update("wms", "New Name", null)).thenReturn(active("New Name"));

        TenantSummary result = useCase.execute("wms", "New Name", null, OP, "reason", "k");

        assertThat(result.displayName()).isEqualTo("New Name");

        ArgumentCaptor<AdminActionAuditor.CompletionRecord> cap =
                ArgumentCaptor.forClass(AdminActionAuditor.CompletionRecord.class);
        verify(auditor).recordCompletion(cap.capture());
        assertThat(cap.getValue().actionCode()).isEqualTo(ActionCode.TENANT_UPDATE);
        assertThat(cap.getValue().outcome()).isEqualTo(Outcome.SUCCESS);

        verify(tenantEventPublisher).publishTenantUpdated(
                eq("wms"), eq("Old Name"), eq("New Name"), eq("op-super"), any());
    }

    @Test
    void active_to_suspended_emits_tenant_suspended_audit() {
        when(auditor.newAuditId()).thenReturn("audit-2");
        when(provisioningPort.get("wms")).thenReturn(active("WMS"));
        when(provisioningPort.update("wms", null, "SUSPENDED")).thenReturn(suspended("WMS"));

        TenantSummary result = useCase.execute("wms", null, "SUSPENDED", OP, "reason", "k");

        assertThat(result.status()).isEqualTo("SUSPENDED");

        ArgumentCaptor<AdminActionAuditor.CompletionRecord> cap =
                ArgumentCaptor.forClass(AdminActionAuditor.CompletionRecord.class);
        verify(auditor).recordCompletion(cap.capture());
        assertThat(cap.getValue().actionCode()).isEqualTo(ActionCode.TENANT_SUSPEND);
        assertThat(cap.getValue().outcome()).isEqualTo(Outcome.SUCCESS);

        // TASK-MONO-773 S4 — previousStatus is the status the tenant really left
        verify(tenantEventPublisher).publishTenantSuspended(
                eq("wms"), eq("ACTIVE"), eq("op-super"), anyString(), any());
    }

    @Test
    void suspended_to_active_emits_tenant_reactivated_audit() {
        when(auditor.newAuditId()).thenReturn("audit-3");
        when(provisioningPort.get("wms")).thenReturn(suspended("WMS"));
        when(provisioningPort.update("wms", null, "ACTIVE")).thenReturn(active("WMS"));

        TenantSummary result = useCase.execute("wms", null, "ACTIVE", OP, "reason", "k");

        assertThat(result.status()).isEqualTo("ACTIVE");

        ArgumentCaptor<AdminActionAuditor.CompletionRecord> cap =
                ArgumentCaptor.forClass(AdminActionAuditor.CompletionRecord.class);
        verify(auditor).recordCompletion(cap.capture());
        assertThat(cap.getValue().actionCode()).isEqualTo(ActionCode.TENANT_REACTIVATE);

        verify(tenantEventPublisher).publishTenantReactivated(
                eq("wms"), eq("op-super"), anyString(), any());
    }

    @Test
    void same_status_no_op_returns_200_no_audit_no_event() {
        when(provisioningPort.get("wms")).thenReturn(active("WMS"));

        TenantSummary result = useCase.execute("wms", null, "ACTIVE", OP, "r", "k");

        assertThat(result.status()).isEqualTo("ACTIVE");
        verify(auditor, never()).newAuditId();
        verify(auditor, never()).recordStart(any());
        verify(auditor, never()).recordCompletion(any());
        verify(tenantEventPublisher, never()).publishTenantSuspended(any(), any(), any(), any(), any());
        verify(tenantEventPublisher, never()).publishTenantReactivated(any(), any(), any(), any());
        verify(tenantEventPublisher, never()).publishTenantUpdated(any(), any(), any(), any(), any());
    }

    @Test
    void both_fields_changed_emits_status_event_then_display_name_event() {
        when(auditor.newAuditId()).thenReturn("audit-s", "audit-dn");
        when(provisioningPort.get("wms")).thenReturn(active("Old Name"));
        when(provisioningPort.update("wms", "New Name", "SUSPENDED")).thenReturn(suspended("New Name"));

        TenantSummary result = useCase.execute("wms", "New Name", "SUSPENDED", OP, "r", "k");

        assertThat(result.status()).isEqualTo("SUSPENDED");

        // Two audit completions: status first, then displayName
        ArgumentCaptor<AdminActionAuditor.CompletionRecord> cap =
                ArgumentCaptor.forClass(AdminActionAuditor.CompletionRecord.class);
        verify(auditor, times(2)).recordCompletion(cap.capture());
        List<AdminActionAuditor.CompletionRecord> records = cap.getAllValues();
        assertThat(records.get(0).actionCode()).isEqualTo(ActionCode.TENANT_SUSPEND);
        assertThat(records.get(1).actionCode()).isEqualTo(ActionCode.TENANT_UPDATE);

        // Both events published
        verify(tenantEventPublisher).publishTenantSuspended(eq("wms"), eq("ACTIVE"), any(), any(), any());
        verify(tenantEventPublisher).publishTenantUpdated(
                eq("wms"), eq("Old Name"), eq("New Name"), any(), any());
    }

    @Test
    void both_fields_null_throws_validation_error() {
        assertThatThrownBy(() ->
                useCase.execute("wms", null, null, OP, "r", "k"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one");
    }

    // ── TASK-MONO-773 S4 — PENDING («관리자 대기») ─────────────────────────

    private static TenantSummary pending() {
        return new TenantSummary("wms", "WMS", "B2B_ENTERPRISE", "PENDING", Instant.now(), Instant.now());
    }

    private static com.example.admin.application.port.OperatorInvitationPort.InvitationView firstAdmin(String status) {
        return new com.example.admin.application.port.OperatorInvitationPort.InvitationView(
                7L, "inv-7", "wms", "owner@wms.example", "Owner",
                List.of("TENANT_ADMIN", "TENANT_BILLING_ADMIN"), status, Instant.now().plusSeconds(60),
                1L, "op-super", null, null, null, null, null, Instant.now(), 0, "h".repeat(64), null, true);
    }

    @Test
    void pending_to_active_without_accepted_first_admin_is_409_and_writes_nothing() {
        when(provisioningPort.get("wms")).thenReturn(pending());
        when(invitationPort.findFirstAdminInvitations(List.of("wms"))).thenReturn(List.of(firstAdmin("PENDING")));

        assertThatThrownBy(() -> useCase.execute("wms", null, "ACTIVE", OP, "r", "k"))
                .isInstanceOf(com.example.admin.application.exception.TenantStatusConflictException.class)
                .extracting("code").isEqualTo("TENANT_FIRST_ADMIN_PENDING");
        verify(provisioningPort, never()).update(any(), any(), any());
        verify(auditor, never()).recordStart(any());
        verify(tenantActivation, never()).activate(any(), any(), any(), any(), any(), any());
    }

    @Test
    void pending_to_active_with_accepted_first_admin_is_the_recovery_activation() {
        when(provisioningPort.get("wms")).thenReturn(pending());
        when(invitationPort.findFirstAdminInvitations(List.of("wms"))).thenReturn(List.of(firstAdmin("ACCEPTED")));
        when(tenantActivation.activate(eq("wms"), eq(OP), eq(TenantActivation.TRIGGER_OPERATOR_COMPLETED),
                eq("tenant.manage"), eq("r"), eq("k:status"))).thenReturn(active("WMS"));

        TenantSummary result = useCase.execute("wms", null, "ACTIVE", OP, "r", "k");

        assertThat(result.status()).isEqualTo("ACTIVE");
        verify(tenantEventPublisher, never()).publishTenantReactivated(any(), any(), any(), any());
    }

    @Test
    void cleanup_cancels_the_pending_first_admin_invitation_before_suspending() {
        when(auditor.newAuditId()).thenReturn("audit-c");
        when(provisioningPort.get("wms")).thenReturn(pending());
        var inv = firstAdmin("PENDING");
        when(invitationPort.findFirstAdminInvitations(List.of("wms"))).thenReturn(List.of(inv));
        when(provisioningPort.update("wms", null, "SUSPENDED")).thenReturn(suspended("WMS"));

        useCase.execute("wms", null, "SUSPENDED", OP, "r", "k");

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(invitationWriter, provisioningPort, tenantEventPublisher);
        order.verify(invitationWriter).cancel(OP, inv, "r");
        order.verify(provisioningPort).update("wms", null, "SUSPENDED");
        order.verify(tenantEventPublisher).publishTenantSuspended(eq("wms"), eq("PENDING"), eq("op-super"), eq("r"), any());
    }

    @Test
    void cleaned_up_suspended_tenant_cannot_be_reactivated() {
        when(provisioningPort.get("wms")).thenReturn(suspended("WMS"));
        when(invitationPort.findFirstAdminInvitations(List.of("wms"))).thenReturn(List.of(firstAdmin("CANCELLED")));

        assertThatThrownBy(() -> useCase.execute("wms", null, "ACTIVE", OP, "r", "k"))
                .isInstanceOf(com.example.admin.application.exception.TenantStatusConflictException.class);
        verify(provisioningPort, never()).update(any(), any(), any());
    }

    @Test
    void same_display_name_no_op() {
        when(provisioningPort.get("wms")).thenReturn(active("Same Name"));

        // Same displayName, no status → no-op
        TenantSummary result = useCase.execute("wms", "Same Name", null, OP, "r", "k");
        assertThat(result.displayName()).isEqualTo("Same Name");
        verify(auditor, never()).newAuditId();
    }
}
