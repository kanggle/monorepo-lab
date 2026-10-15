package com.example.admin.application.tenant;

import com.example.admin.application.ActionCode;
import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.OperatorContext;
import com.example.admin.application.OperatorInvitationResult;
import com.example.admin.application.OperatorInvitationUseCase;
import com.example.admin.application.OperatorInvitationWriter;
import com.example.admin.application.Outcome;
import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.TenantAlreadyExistsException;
import com.example.admin.application.exception.TenantIdReservedException;
import com.example.admin.application.port.OperatorInvitationPort.InvitationView;
import com.example.admin.application.port.TenantProvisioningPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CreateTenantUseCase} — TASK-MONO-773 S4: «tenant (PENDING) + first-admin invitation» as one bundle, with the
 * SUSPEND compensation (admin-api.md § POST /api/admin/tenants). The DB half (one transaction, V0055, the mail) is
 * {@code TenantFirstAdminIntegrationTest}'s.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class CreateTenantUseCaseTest {

    @Mock TenantProvisioningPort provisioningPort;
    @Mock AdminActionAuditor auditor;
    @Mock CreateTenantUseCase.Bundle bundle;
    @Mock OperatorInvitationUseCase invitationUseCase;

    @InjectMocks CreateTenantUseCase useCase;

    private static final OperatorContext SUPER_ADMIN = new OperatorContext("op-super", "jti-1");

    private static TenantSummary pending(String tenantId) {
        return new TenantSummary(tenantId, "Display " + tenantId,
                "B2B_ENTERPRISE", "PENDING", Instant.now(), Instant.now());
    }

    private static CreateTenantCommand cmd(String tenantId, String displayName, String type) {
        return new CreateTenantCommand(tenantId, displayName, type, " Owner@Acme.Example ", " 홍길동 ",
                SUPER_ADMIN, "init", "idemp-1");
    }

    private static OperatorInvitationWriter.Issued issued(String tenantId) {
        InvitationView v = new InvitationView(1L, "inv-1", tenantId, "owner@acme.example", "홍길동",
                List.of("TENANT_ADMIN", "TENANT_BILLING_ADMIN"), "PENDING", Instant.now().plusSeconds(60),
                9L, "op-super", null, null, null, null, null, Instant.now(), 0, "h".repeat(64), null, true);
        return new OperatorInvitationWriter.Issued(v, "raw-token", "audit-inv");
    }

    private static OperatorInvitationResult item(String tenantId) {
        return new OperatorInvitationResult("inv-1", tenantId, "owner@acme.example", "홍길동",
                List.of("TENANT_ADMIN", "TENANT_BILLING_ADMIN"), "PENDING", false, Instant.now().plusSeconds(60),
                Instant.now(), "op-super", "SENT", Instant.now(), null, null, null, "audit-inv", true);
    }

    @Test
    void happy_path_creates_pending_tenant_then_first_admin_bundle_then_mail() {
        when(auditor.newAuditId()).thenReturn("audit-1");
        when(provisioningPort.create("wms", "WMS Platform", "B2B_ENTERPRISE", "PENDING")).thenReturn(pending("wms"));
        OperatorInvitationWriter.Issued issued = issued("wms");
        when(bundle.write(eq(SUPER_ADMIN), any(), eq("wms"), eq("owner@acme.example"), eq("홍길동"),
                eq("idemp-1"), eq("init"))).thenReturn(issued);
        when(invitationUseCase.deliverCommitted(issued, SUPER_ADMIN)).thenReturn(item("wms"));

        CreateTenantResult result = useCase.execute(cmd("wms", "WMS Platform", "B2B_ENTERPRISE"));

        assertThat(result.tenant().status()).isEqualTo("PENDING");
        assertThat(result.firstAdminInvitation().firstAdmin()).isTrue();

        // IN_PROGRESS → account (PENDING) → bundle → SUCCESS → mail, in that order; never an ACTIVE request.
        InOrder order = inOrder(auditor, provisioningPort, bundle, invitationUseCase);
        order.verify(auditor).recordStart(any());
        order.verify(provisioningPort).create("wms", "WMS Platform", "B2B_ENTERPRISE", "PENDING");
        order.verify(bundle).write(any(), any(), eq("wms"), anyString(), anyString(), any(), any());
        ArgumentCaptor<AdminActionAuditor.CompletionRecord> comp =
                ArgumentCaptor.forClass(AdminActionAuditor.CompletionRecord.class);
        order.verify(auditor).recordCompletion(comp.capture());
        order.verify(invitationUseCase).deliverCommitted(issued, SUPER_ADMIN);
        assertThat(comp.getValue().actionCode()).isEqualTo(ActionCode.TENANT_CREATE);
        assertThat(comp.getValue().outcome()).isEqualTo(Outcome.SUCCESS);
        verify(provisioningPort, never()).update(any(), any(), any());
    }

    @Test
    void bundle_failure_compensates_pending_to_suspended_and_rethrows() {
        when(auditor.newAuditId()).thenReturn("audit-2");
        when(provisioningPort.create("wms", "WMS", "B2B_ENTERPRISE", "PENDING")).thenReturn(pending("wms"));
        when(bundle.write(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> useCase.execute(cmd("wms", "WMS", "B2B_ENTERPRISE")))
                .isInstanceOf(IllegalStateException.class);

        verify(provisioningPort).update("wms", null, "SUSPENDED");
        verify(provisioningPort, never()).update(any(), any(), eq("ACTIVE"));
        ArgumentCaptor<AdminActionAuditor.CompletionRecord> cap =
                ArgumentCaptor.forClass(AdminActionAuditor.CompletionRecord.class);
        verify(auditor).recordCompletion(cap.capture());
        assertThat(cap.getValue().outcome()).isEqualTo(Outcome.FAILURE);
        verify(invitationUseCase, never()).deliverCommitted(any(), any());
    }

    @Test
    void compensation_failure_does_not_mask_the_original_error() {
        when(auditor.newAuditId()).thenReturn("audit-3");
        when(provisioningPort.create("wms", "WMS", "B2B_ENTERPRISE", "PENDING")).thenReturn(pending("wms"));
        when(bundle.write(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("db down"));
        when(provisioningPort.update("wms", null, "SUSPENDED")).thenThrow(new DownstreamFailureException("CB open"));

        assertThatThrownBy(() -> useCase.execute(cmd("wms", "WMS", "B2B_ENTERPRISE")))
                .isInstanceOf(IllegalStateException.class).hasMessage("db down");
    }

    @Test
    void missing_or_malformed_first_admin_is_refused_before_anything() {
        for (String[] bad : new String[][]{{null, "이름"}, {"not-an-email", "이름"}, {"a@b.example", " "},
                {"a@b.example", "가".repeat(65)}, {"a@b.example", null}}) {
            CreateTenantCommand c = new CreateTenantCommand("wms", "WMS", "B2C_CONSUMER", bad[0], bad[1],
                    SUPER_ADMIN, "r", "k");
            assertThatThrownBy(() -> useCase.execute(c)).isInstanceOf(IllegalArgumentException.class);
        }
        verify(provisioningPort, never()).create(any(), any(), any(), any());
        verify(auditor, never()).recordStart(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"WMS", "1wms", "wms!", "", "a", "this-is-a-very-long-tenant-id-that-exceeds-limits"})
    void invalid_slug_throws_illegal_argument_port_not_called(String badSlug) {
        assertThatThrownBy(() -> useCase.execute(cmd(badSlug, "Display", "B2B_ENTERPRISE")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(provisioningPort, never()).create(any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "internal", "system", "null", "default",
            "public", "gap", "iam", "auth", "oauth", "me"})
    void reserved_word_throws_tenant_id_reserved_port_not_called(String reserved) {
        assertThatThrownBy(() -> useCase.execute(cmd(reserved, "Display", "B2B_ENTERPRISE")))
                .isInstanceOf(TenantIdReservedException.class);
        verify(provisioningPort, never()).create(any(), any(), any(), any());
    }

    @Test
    void port_throws_tenant_already_exists_is_propagated_no_bundle() {
        when(auditor.newAuditId()).thenReturn("audit-4");
        when(provisioningPort.create(eq("wms"), any(), any(), eq("PENDING")))
                .thenThrow(new TenantAlreadyExistsException("wms"));

        assertThatThrownBy(() -> useCase.execute(cmd("wms", "WMS", "B2B_ENTERPRISE")))
                .isInstanceOf(TenantAlreadyExistsException.class);

        ArgumentCaptor<AdminActionAuditor.CompletionRecord> cap =
                ArgumentCaptor.forClass(AdminActionAuditor.CompletionRecord.class);
        verify(auditor).recordCompletion(cap.capture());
        assertThat(cap.getValue().outcome()).isEqualTo(Outcome.FAILURE);
        verify(bundle, never()).write(any(), any(), any(), any(), any(), any(), any());
        verify(provisioningPort, never()).update(any(), isNull(), any());
    }

    @Test
    void port_throws_downstream_failure_audit_failure_and_no_bundle() {
        when(auditor.newAuditId()).thenReturn("audit-5");
        when(provisioningPort.create(eq("wms"), any(), any(), eq("PENDING")))
                .thenThrow(new DownstreamFailureException("CB open"));

        assertThatThrownBy(() -> useCase.execute(cmd("wms", "WMS", "B2B_ENTERPRISE")))
                .isInstanceOf(DownstreamFailureException.class);

        ArgumentCaptor<AdminActionAuditor.CompletionRecord> cap =
                ArgumentCaptor.forClass(AdminActionAuditor.CompletionRecord.class);
        verify(auditor).recordCompletion(cap.capture());
        assertThat(cap.getValue().outcome()).isEqualTo(Outcome.FAILURE);
        verify(bundle, never()).write(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void invalid_tenant_type_throws_before_port_called() {
        assertThatThrownBy(() -> useCase.execute(cmd("wms", "WMS", "INVALID_TYPE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("B2C_CONSUMER or B2B_ENTERPRISE");
        verify(provisioningPort, never()).create(any(), any(), any(), any());
    }

    @Test
    void display_name_too_long_throws_before_port_called() {
        String tooLong = "x".repeat(101);
        assertThatThrownBy(() -> useCase.execute(cmd("wms", tooLong, "B2B_ENTERPRISE")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(provisioningPort, never()).create(any(), any(), any(), any());
    }
}
