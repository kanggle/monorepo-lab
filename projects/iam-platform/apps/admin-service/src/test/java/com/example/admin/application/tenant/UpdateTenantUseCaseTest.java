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

        verify(tenantEventPublisher).publishTenantSuspended(
                eq("wms"), eq("op-super"), anyString(), any());
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
        verify(tenantEventPublisher, never()).publishTenantSuspended(any(), any(), any(), any());
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
        verify(tenantEventPublisher).publishTenantSuspended(eq("wms"), any(), any(), any());
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

    @Test
    void same_display_name_no_op() {
        when(provisioningPort.get("wms")).thenReturn(active("Same Name"));

        // Same displayName, no status → no-op
        TenantSummary result = useCase.execute("wms", "Same Name", null, OP, "r", "k");
        assertThat(result.displayName()).isEqualTo("Same Name");
        verify(auditor, never()).newAuditId();
    }
}
