package com.example.admin.application;

import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.TenantNotFoundException;
import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.TenantEntryPolicyManagementPort;
import com.example.admin.application.port.TenantEntryPolicyManagementPort.EntryPolicyView;
import com.example.admin.application.port.TenantProvisioningPort;
import com.example.admin.application.tenant.TenantSummary;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.persistence.rbac.AdminGrantScopeEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-771 S5 — {@link TenantEntryPolicyUseCase} (admin-api.md § Tenant Entry Policy).
 *
 * <p>The scope check runs through the REAL {@link TenantScopeGuard} over a mocked
 * {@link AdminGrantScopeEvaluator} — so «a TENANT_ADMIN of tenant-x may not touch tenant-y» is decided by
 * the production decision site, not by a stub that throws on cue.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("TenantEntryPolicyUseCase (unit)")
class TenantEntryPolicyUseCaseTest {

    @Mock TenantEntryPolicyManagementPort entryPolicyPort;
    @Mock TenantProvisioningPort provisioningPort;
    @Mock AdminOperatorPort operatorPort;
    @Mock AdminGrantScopeEvaluator grantScopeEvaluator;
    @Mock AdminActionAuditor auditor;

    TenantEntryPolicyUseCase useCase;

    private static final String TENANT_ADMIN_X = "00000000-0000-7000-8000-0000000c0001";
    private static final OperatorContext ADMIN_X = new OperatorContext(TENANT_ADMIN_X, "jti-1");
    private static final Instant T0 = Instant.parse("2026-10-09T00:00:00Z");

    @BeforeEach
    void setUp() {
        useCase = new TenantEntryPolicyUseCase(entryPolicyPort, provisioningPort, operatorPort,
                new TenantScopeGuard(grantScopeEvaluator, auditor), auditor);
    }

    /**
     * In-scope is a PRECONDITION of the positive cases (lenient — those tests are about what happens after
     * the gate); out-of-scope is the ASSERTION of the denial cases (strict). So removing the scope check
     * from the use-case turns exactly the denial test red, not every test that set the gate up.
     */
    private void inScope(String tenantId, boolean in) {
        if (in) {
            lenient().when(grantScopeEvaluator.isTenantInAdminScope(
                    TENANT_ADMIN_X, Permission.TENANT_SECURITY_MANAGE, tenantId)).thenReturn(true);
        } else {
            when(grantScopeEvaluator.isTenantInAdminScope(
                    TENANT_ADMIN_X, Permission.TENANT_SECURITY_MANAGE, tenantId)).thenReturn(false);
        }
    }

    private void writerExists() {
        when(operatorPort.findByOperatorId(TENANT_ADMIN_X)).thenReturn(Optional.of(new AdminOperatorPort.OperatorView(
                7L, TENANT_ADMIN_X, "tenant-x", "a@x.com", null, "A", "ACTIVE",
                null, null, T0, T0, null, null)));
    }

    private void tenantExists(String tenantId) {
        when(provisioningPort.get(tenantId))
                .thenReturn(new TenantSummary(tenantId, tenantId, "B2B_ENTERPRISE", "ACTIVE", T0, T0));
    }

    // ---- PUT -------------------------------------------------------------------------

    @Test
    @DisplayName("🔴 TENANT_ADMIN of tenant-x PUT on tenant-y → TENANT_SCOPE_DENIED · DENIED row · nothing read or written")
    void otherTenant_denied() {
        inScope("tenant-y", false);

        assertThatExceptionOfType(TenantScopeDeniedException.class)
                .isThrownBy(() -> useCase.set(ADMIN_X, "tenant-y", true, "turn on"));

        verify(auditor).recordCrossTenantDenied(ADMIN_X, null, ActionCode.TENANT_ENTRY_POLICY_SET,
                Permission.TENANT_SECURITY_MANAGE, "tenant-y");
        verifyNoInteractions(provisioningPort, entryPolicyPort);
        verify(auditor, never()).recordWithPermission(any(), any());
    }

    @Test
    @DisplayName("TENANT_ADMIN of tenant-x PUT on tenant-x (first time) → written · audit «requireMfa none→true»")
    void ownTenant_firstWrite_auditsNoneToTrue() {
        inScope("tenant-x", true);
        tenantExists("tenant-x");
        writerExists();
        when(entryPolicyPort.find("tenant-x")).thenReturn(Optional.empty());
        when(entryPolicyPort.save(eq("tenant-x"), eq(true), eq(7L), any()))
                .thenReturn(new EntryPolicyView("tenant-x", true, T0, TENANT_ADMIN_X));

        EntryPolicyView out = useCase.set(ADMIN_X, "tenant-x", true, "회사 정책");

        assertThat(out.requireMfa()).isTrue();
        assertThat(out.updatedByOperatorId()).isEqualTo(TENANT_ADMIN_X);
        ArgumentCaptor<AdminActionAuditor.AuditRecord> rec = ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        verify(auditor).recordWithPermission(rec.capture(), eq(Permission.TENANT_SECURITY_MANAGE));
        AdminActionAuditor.AuditRecord r = rec.getValue();
        assertThat(r.actionCode()).isEqualTo(ActionCode.TENANT_ENTRY_POLICY_SET);
        assertThat(r.targetType()).isEqualTo("TENANT");
        assertThat(r.targetId()).isEqualTo("tenant-x");
        assertThat(r.targetTenantId()).isEqualTo("tenant-x");
        assertThat(r.reason()).isEqualTo("회사 정책");
        assertThat(r.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(r.downstreamDetail()).isEqualTo("requireMfa none→true");
    }

    @Test
    @DisplayName("turning OFF an existing ON policy → audit «requireMfa true→false» (the row is kept, not deleted)")
    void turnOff_auditsTrueToFalse() {
        inScope("tenant-x", true);
        tenantExists("tenant-x");
        writerExists();
        when(entryPolicyPort.find("tenant-x"))
                .thenReturn(Optional.of(new EntryPolicyView("tenant-x", true, T0, "someone")));
        when(entryPolicyPort.save(eq("tenant-x"), eq(false), eq(7L), any()))
                .thenReturn(new EntryPolicyView("tenant-x", false, T0, TENANT_ADMIN_X));

        useCase.set(ADMIN_X, "tenant-x", false, "rollback");

        ArgumentCaptor<AdminActionAuditor.AuditRecord> rec = ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        verify(auditor).recordWithPermission(rec.capture(), eq(Permission.TENANT_SECURITY_MANAGE));
        assertThat(rec.getValue().downstreamDetail()).isEqualTo("requireMfa true→false");
    }

    @Test
    @DisplayName("same-value PUT (no-op) still writes one audit row (admin-api.md «no-op 포함»)")
    void noOp_stillAudited() {
        inScope("tenant-x", true);
        tenantExists("tenant-x");
        writerExists();
        when(entryPolicyPort.find("tenant-x"))
                .thenReturn(Optional.of(new EntryPolicyView("tenant-x", true, T0, "someone")));
        when(entryPolicyPort.save(eq("tenant-x"), eq(true), eq(7L), any()))
                .thenReturn(new EntryPolicyView("tenant-x", true, T0, TENANT_ADMIN_X));

        useCase.set(ADMIN_X, "tenant-x", true, "again");

        ArgumentCaptor<AdminActionAuditor.AuditRecord> rec = ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        verify(auditor).recordWithPermission(rec.capture(), eq(Permission.TENANT_SECURITY_MANAGE));
        assertThat(rec.getValue().downstreamDetail()).isEqualTo("requireMfa true→true");
    }

    @Test
    @DisplayName("🔴 tenantId '*' → 400 (IllegalArgumentException) before any scope / authority / write")
    void platformSentinel_rejected() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> useCase.set(ADMIN_X, "*", true, "r"))
                .withMessageContaining("'*'");
        verifyNoInteractions(grantScopeEvaluator, provisioningPort, entryPolicyPort, auditor);
    }

    @Test
    @DisplayName("tenantId outside the id shape → 400")
    void malformedTenantId_rejected() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> useCase.set(ADMIN_X, "Bad_Tenant", true, "r"));
        verifyNoInteractions(grantScopeEvaluator, provisioningPort, entryPolicyPort, auditor);
    }

    @Test
    @DisplayName("requireMfa null → 400, nothing written")
    void nullValue_rejected() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> useCase.set(ADMIN_X, "tenant-x", null, "r"));
        verifyNoInteractions(provisioningPort, entryPolicyPort, auditor);
    }

    @Test
    @DisplayName("tenant unknown at the authority → TENANT_NOT_FOUND, no orphan row, no audit")
    void unknownTenant_notWritten() {
        inScope("ghost", true);
        when(provisioningPort.get("ghost")).thenThrow(new TenantNotFoundException("ghost"));

        assertThatExceptionOfType(TenantNotFoundException.class)
                .isThrownBy(() -> useCase.set(ADMIN_X, "ghost", true, "r"));
        verify(entryPolicyPort, never()).save(anyString(), anyBoolean(), any(), any());
        verify(auditor, never()).recordWithPermission(any(), any());
    }

    @Test
    @DisplayName("authority down → DownstreamFailure (503), fail-closed: nothing written")
    void authorityDown_notWritten() {
        inScope("tenant-x", true);
        when(provisioningPort.get("tenant-x")).thenThrow(new DownstreamFailureException("down", null));

        assertThatExceptionOfType(DownstreamFailureException.class)
                .isThrownBy(() -> useCase.set(ADMIN_X, "tenant-x", true, "r"));
        verify(entryPolicyPort, never()).save(anyString(), anyBoolean(), any(), any());
    }

    // ---- GET -------------------------------------------------------------------------

    @Test
    @DisplayName("GET with no row → «off» (requireMfa=false, updatedAt/updatedBy null) — not a 404")
    void get_absent_isOff() {
        inScope("tenant-x", true);
        when(entryPolicyPort.find("tenant-x")).thenReturn(Optional.empty());

        EntryPolicyView v = useCase.get(ADMIN_X, "tenant-x");

        assertThat(v).isEqualTo(new EntryPolicyView("tenant-x", false, null, null));
        verifyNoInteractions(provisioningPort, auditor);
    }

    @Test
    @DisplayName("🔴 GET of another tenant → TENANT_SCOPE_DENIED · read path writes NO DENIED row · nothing read")
    void get_otherTenant_denied() {
        inScope("tenant-y", false);

        assertThatExceptionOfType(TenantScopeDeniedException.class)
                .isThrownBy(() -> useCase.get(ADMIN_X, "tenant-y"));
        verifyNoInteractions(entryPolicyPort, auditor);
    }

    @Test
    @DisplayName("GET '*' → 400")
    void get_platformSentinel_rejected() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> useCase.get(ADMIN_X, "*"));
        verifyNoInteractions(grantScopeEvaluator, entryPolicyPort);
    }
}
