package com.example.admin.application.tenant;

import com.example.admin.application.ActionCode;
import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.OperatorContext;
import com.example.admin.application.Outcome;
import com.example.admin.application.event.TenantEventPublisher;
import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.TenantAlreadyExistsException;
import com.example.admin.application.exception.TenantIdReservedException;
import com.example.admin.application.port.TenantProvisioningPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class CreateTenantUseCaseTest {

    @Mock TenantProvisioningPort provisioningPort;
    @Mock AdminActionAuditor auditor;
    @Mock TenantEventPublisher tenantEventPublisher;

    @InjectMocks CreateTenantUseCase useCase;

    private static final OperatorContext SUPER_ADMIN = new OperatorContext("op-super", "jti-1");

    private static TenantSummary stubSummary(String tenantId) {
        return new TenantSummary(tenantId, "Display " + tenantId,
                "B2B_ENTERPRISE", "ACTIVE", Instant.now(), Instant.now());
    }

    @Test
    void happy_path_creates_tenant_records_audit_and_publishes_event() {
        when(auditor.newAuditId()).thenReturn("audit-1");
        when(provisioningPort.create("wms", "WMS Platform", "B2B_ENTERPRISE"))
                .thenReturn(stubSummary("wms"));

        TenantSummary result = useCase.execute(
                "wms", "WMS Platform", "B2B_ENTERPRISE", SUPER_ADMIN, "init", "idemp-1");

        assertThat(result.tenantId()).isEqualTo("wms");
        assertThat(result.status()).isEqualTo("ACTIVE");

        // Audit: IN_PROGRESS then SUCCESS
        ArgumentCaptor<AdminActionAuditor.StartRecord> startCap =
                ArgumentCaptor.forClass(AdminActionAuditor.StartRecord.class);
        verify(auditor).recordStart(startCap.capture());
        assertThat(startCap.getValue().actionCode()).isEqualTo(ActionCode.TENANT_CREATE);
        assertThat(startCap.getValue().targetTenantId()).isEqualTo("wms");

        ArgumentCaptor<AdminActionAuditor.CompletionRecord> compCap =
                ArgumentCaptor.forClass(AdminActionAuditor.CompletionRecord.class);
        verify(auditor).recordCompletion(compCap.capture());
        assertThat(compCap.getValue().outcome()).isEqualTo(Outcome.SUCCESS);

        // Outbox event
        verify(tenantEventPublisher).publishTenantCreated(
                eq("wms"), anyString(), eq("B2B_ENTERPRISE"), eq("op-super"), any(Instant.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"WMS", "1wms", "wms!", "", "a", "this-is-a-very-long-tenant-id-that-exceeds-limits"})
    void invalid_slug_throws_illegal_argument_port_not_called(String badSlug) {
        assertThatThrownBy(() ->
                useCase.execute(badSlug, "Display", "B2B_ENTERPRISE", SUPER_ADMIN, "r", "k"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(provisioningPort, never()).create(any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "internal", "system", "null", "default",
            "public", "gap", "auth", "oauth", "me"})
    void reserved_word_throws_tenant_id_reserved_port_not_called(String reserved) {
        assertThatThrownBy(() ->
                useCase.execute(reserved, "Display", "B2B_ENTERPRISE", SUPER_ADMIN, "r", "k"))
                .isInstanceOf(TenantIdReservedException.class);
        verify(provisioningPort, never()).create(any(), any(), any());
    }

    @Test
    void port_throws_tenant_already_exists_is_propagated() {
        when(auditor.newAuditId()).thenReturn("audit-2");
        when(provisioningPort.create(eq("wms"), any(), any()))
                .thenThrow(new TenantAlreadyExistsException("wms"));

        assertThatThrownBy(() ->
                useCase.execute("wms", "WMS", "B2B_ENTERPRISE", SUPER_ADMIN, "r", "k"))
                .isInstanceOf(TenantAlreadyExistsException.class);

        // audit recorded as FAILURE
        ArgumentCaptor<AdminActionAuditor.CompletionRecord> cap =
                ArgumentCaptor.forClass(AdminActionAuditor.CompletionRecord.class);
        verify(auditor).recordCompletion(cap.capture());
        assertThat(cap.getValue().outcome()).isEqualTo(Outcome.FAILURE);

        // No outbox event
        verify(tenantEventPublisher, never()).publishTenantCreated(any(), any(), any(), any(), any());
    }

    @Test
    void port_throws_downstream_failure_audit_failure_and_no_event() {
        when(auditor.newAuditId()).thenReturn("audit-3");
        when(provisioningPort.create(eq("wms"), any(), any()))
                .thenThrow(new DownstreamFailureException("CB open"));

        assertThatThrownBy(() ->
                useCase.execute("wms", "WMS", "B2B_ENTERPRISE", SUPER_ADMIN, "r", "k"))
                .isInstanceOf(DownstreamFailureException.class);

        ArgumentCaptor<AdminActionAuditor.CompletionRecord> cap =
                ArgumentCaptor.forClass(AdminActionAuditor.CompletionRecord.class);
        verify(auditor).recordCompletion(cap.capture());
        assertThat(cap.getValue().outcome()).isEqualTo(Outcome.FAILURE);

        verify(tenantEventPublisher, never()).publishTenantCreated(any(), any(), any(), any(), any());
    }

    @Test
    void invalid_tenant_type_throws_before_port_called() {
        assertThatThrownBy(() ->
                useCase.execute("wms", "WMS", "INVALID_TYPE", SUPER_ADMIN, "r", "k"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("B2C_CONSUMER or B2B_ENTERPRISE");
        verify(provisioningPort, never()).create(any(), any(), any());
    }

    @Test
    void display_name_too_long_throws_before_port_called() {
        String tooLong = "x".repeat(101);
        assertThatThrownBy(() ->
                useCase.execute("wms", tooLong, "B2B_ENTERPRISE", SUPER_ADMIN, "r", "k"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(provisioningPort, never()).create(any(), any(), any());
    }
}
