package com.example.admin.application;

import com.example.admin.application.exception.OperatorNotFoundException;
import com.example.admin.application.exception.RoleNotFoundException;
import com.example.admin.application.exception.TenantLastAdminRequiredException;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.persistence.rbac.CachingPermissionEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Set;

import static com.example.admin.application.OperatorUseCaseTestSupport.actor;
import static com.example.admin.application.OperatorUseCaseTestSupport.operator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PatchOperatorRoleUseCase} — TASK-MONO-784 S4: a role edit is on the company axis (D-7).
 *
 * <p>Role rows live in {@link InMemoryRoleRowsPort} (a fake that really deletes), so «another company's rows are
 * byte-unchanged» is asserted on data, not on which mock method was called: reverting the edit to «delete every
 * row of the operator» turns the 🔴 cells red. The «last administrator» predicate is the REAL
 * {@link TenantLastAdminGuard} over the same rows.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class PatchOperatorRoleUseCaseTest {

    @Mock AdminActionAuditor auditor;
    @Mock CachingPermissionEvaluator cachingPermissionEvaluator;
    @Mock TenantScopeGuard tenantScopeGuard;
    @Mock RoleGrantGuard roleGrantGuard;
    @Mock TenantScopeResolver tenantScopeResolver;

    InMemoryRoleRowsPort port;
    PatchOperatorRoleUseCase useCase;

    static final long TARGET = 77L;
    static final long OTHER_ADMIN = 88L;

    @BeforeEach
    void init() {
        port = new InMemoryRoleRowsPort()
                .role(1L, "SUPER_ADMIN").role(2L, "SUPPORT_READONLY").role(3L, "SUPPORT_LOCK")
                .role(4L, "SECURITY_ANALYST").role(5L, "TENANT_ADMIN").role(6L, "ORG_ADMIN")
                .operator(operator(TARGET, "target-uuid", "t@ex.com", "ACTIVE", "alpha"))
                .operator(operator(99L, "actor-uuid", "a@ex.com", "ACTIVE", "alpha"));
        useCase = new PatchOperatorRoleUseCase(port, auditor, cachingPermissionEvaluator, tenantScopeGuard,
                roleGrantGuard, tenantScopeResolver, new TenantLastAdminGuard(port));
    }

    private void memberships(String... tenants) {
        when(tenantScopeResolver.resolveEffectiveTenantScope(TARGET, "alpha")).thenReturn(Set.of(tenants));
    }

    @Test
    @DisplayName("🔴 AC-4 · F6 · Failure 3: edit in alpha replaces ONLY alpha's direct rows — beta row, alpha group row, alpha node row byte-unchanged")
    void editInAlpha_leavesOtherCompanyAndNonDirectRowsByteUnchanged() {
        memberships("alpha", "beta");
        port.row(TARGET, "SUPPORT_LOCK", "alpha", null, null)          // replaced
                .row(TARGET, "SUPPORT_READONLY", "beta", null, null)   // other company
                .row(TARGET, "SECURITY_ANALYST", "alpha", 9L, null)    // group fan-out in alpha
                .row(TARGET, "ORG_ADMIN", "alpha", null, "node-1");    // org-node grant recorded in alpha
        List<InMemoryRoleRowsPort.Row> betaBefore = List.copyOf(port.rowsOutside(TARGET, "alpha"));
        List<InMemoryRoleRowsPort.Row> alphaNonDirectBefore = port.rowsIn(TARGET, "alpha").stream()
                .filter(r -> !r.isDirect()).toList();

        PatchOperatorRoleUseCase.PatchRolesResult result =
                useCase.patchRoles("target-uuid", List.of("SUPPORT_READONLY"), "alpha", actor(), "rotation");

        assertThat(port.rowsOutside(TARGET, "alpha")).as("beta rows byte-unchanged").isEqualTo(betaBefore);
        assertThat(port.rowsIn(TARGET, "alpha").stream().filter(r -> !r.isDirect()).toList())
                .as("alpha's group / node rows byte-unchanged (F6)").isEqualTo(alphaNonDirectBefore);
        assertThat(port.rowsIn(TARGET, "alpha").stream().filter(InMemoryRoleRowsPort.Row::isDirect)
                .map(InMemoryRoleRowsPort.Row::roleName).toList())
                .containsExactly("SUPPORT_READONLY");
        // response = alpha's roles after the edit: requested first, then alpha's group / node roles; never beta's
        assertThat(result.roles()).containsExactly("SUPPORT_READONLY", "ORG_ADMIN", "SECURITY_ANALYST");

        verify(tenantScopeGuard).requireTenantInScope(any(), eq(Permission.OPERATOR_MANAGE), eq("alpha"),
                eq(ActionCode.OPERATOR_ROLE_CHANGE));
        verify(roleGrantGuard).requireGrantable(any(), any(), eq(ActionCode.OPERATOR_ROLE_CHANGE), eq("alpha"));
        verify(cachingPermissionEvaluator).invalidate("target-uuid");
        ArgumentCaptor<AdminActionAuditor.AuditRecord> rec = ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        verify(auditor).record(rec.capture());
        assertThat(rec.getValue().targetTenantId()).as("audit target_tenant_id = T").isEqualTo("alpha");
    }

    @Test
    @DisplayName("control: single-company target, no header → its one membership (same as the pre-784 «home» edit)")
    void noHeader_singleMembership_isThatCompany() {
        memberships("alpha");
        port.row(TARGET, "SUPPORT_LOCK", "alpha", null, null);

        PatchOperatorRoleUseCase.PatchRolesResult result =
                useCase.patchRoles("target-uuid", List.of("SUPPORT_READONLY", "SECURITY_ANALYST"), null, actor(), "r");

        assertThat(result.roles()).containsExactly("SUPPORT_READONLY", "SECURITY_ANALYST");
        assertThat(port.rowsIn(TARGET, "alpha")).extracting(InMemoryRoleRowsPort.Row::roleName)
                .containsExactlyInAnyOrder("SUPPORT_READONLY", "SECURITY_ANALYST");
    }

    @Test
    @DisplayName("no header + two memberships → 400 (IllegalArgumentException) · no header + none → 404; nothing written")
    void noHeader_ambiguousOrNone_refusedBeforeAnyWrite() {
        memberships("alpha", "beta");
        port.row(TARGET, "SUPPORT_LOCK", "alpha", null, null);
        assertThatThrownBy(() -> useCase.patchRoles("target-uuid", List.of(), null, actor(), "r"))
                .isInstanceOf(IllegalArgumentException.class);

        memberships();
        assertThatThrownBy(() -> useCase.patchRoles("target-uuid", List.of(), null, actor(), "r"))
                .isInstanceOf(OperatorNotFoundException.class);

        assertThat(port.rows).hasSize(1);
        verify(tenantScopeGuard, never()).requireTenantInScope(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("header names a company the target does not belong to → 404 (enumeration safe), nothing written")
    void headerOutsideMemberships_notFound() {
        memberships("alpha");
        port.row(TARGET, "SUPPORT_LOCK", "alpha", null, null);

        assertThatThrownBy(() -> useCase.patchRoles("target-uuid", List.of("SUPPORT_READONLY"), "gamma", actor(), "r"))
                .isInstanceOf(OperatorNotFoundException.class);
        assertThat(port.rows).hasSize(1);
    }

    @Test
    @DisplayName("D2 (D-14): a company operator never gets a '*' row (header '*') → 400; a '*' target ignores the header → T='*'")
    void platformAxis() {
        memberships("alpha");
        assertThatThrownBy(() -> useCase.patchRoles("target-uuid", List.of("SUPPORT_READONLY"), "*", actor(), "r"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(port.rows).isEmpty();

        port.operator(operator(55L, "platform-uuid", "p@ex.com", "ACTIVE", "*"));
        port.row(55L, "SUPPORT_LOCK", "*", null, null);
        useCase.patchRoles("platform-uuid", List.of("SUPPORT_READONLY"), "alpha", actor(), "r");
        assertThat(port.rowsIn(55L, "*")).extracting(InMemoryRoleRowsPort.Row::roleName)
                .containsExactly("SUPPORT_READONLY");
        // twice with '*': the refused company-target call (the scope row runs before the D2 row — contract table
        // order) and the platform target's edit
        verify(tenantScopeGuard, org.mockito.Mockito.times(2))
                .requireTenantInScope(any(), eq(Permission.OPERATOR_MANAGE), eq("*"), any());
    }

    @Test
    @DisplayName("🔴 last admin: removing the sole ACTIVE TENANT_ADMIN@alpha → 409, nothing written / control: another admin → OK")
    void lastAdmin_refused_thenAllowedWithAnotherAdmin() {
        memberships("alpha");
        port.row(TARGET, "TENANT_ADMIN", "alpha", null, null);

        assertThatThrownBy(() -> useCase.patchRoles("target-uuid", List.of(), "alpha", actor(), "r"))
                .isInstanceOf(TenantLastAdminRequiredException.class);
        assertThat(port.rowsIn(TARGET, "alpha")).extracting(InMemoryRoleRowsPort.Row::roleName)
                .containsExactly("TENANT_ADMIN");

        // a SUSPENDED second admin does not count
        port.operator(operator(OTHER_ADMIN, "other-uuid", "o@ex.com", "SUSPENDED", "alpha"));
        port.row(OTHER_ADMIN, "TENANT_ADMIN", "alpha", null, null);
        assertThatThrownBy(() -> useCase.patchRoles("target-uuid", List.of(), "alpha", actor(), "r"))
                .isInstanceOf(TenantLastAdminRequiredException.class);

        // control: the second admin is ACTIVE → the edit goes through
        port.operator(operator(OTHER_ADMIN, "other-uuid", "o@ex.com", "ACTIVE", "alpha"));
        useCase.patchRoles("target-uuid", List.of(), "alpha", actor(), "r");
        assertThat(port.rowsIn(TARGET, "alpha")).isEmpty();
    }

    @Test
    @DisplayName("last admin keeps TENANT_ADMIN through a group row, or the edit re-grants it → no 409")
    void lastAdmin_notTriggeredWhenAdminSurvives() {
        memberships("alpha");
        port.row(TARGET, "TENANT_ADMIN", "alpha", null, null);
        useCase.patchRoles("target-uuid", List.of("TENANT_ADMIN", "SUPPORT_READONLY"), "alpha", actor(), "r");
        assertThat(port.rowsIn(TARGET, "alpha")).extracting(InMemoryRoleRowsPort.Row::roleName)
                .containsExactlyInAnyOrder("TENANT_ADMIN", "SUPPORT_READONLY");
    }

    @Test
    @DisplayName("unknown role → RoleNotFoundException before any delete; cache untouched")
    void unknownRole_throwsBeforeDelete() {
        memberships("alpha");
        port.row(TARGET, "SUPPORT_LOCK", "alpha", null, null);

        assertThatThrownBy(() -> useCase.patchRoles("target-uuid", List.of("GHOST"), "alpha", actor(), "reason"))
                .isInstanceOf(RoleNotFoundException.class);

        assertThat(port.rows).hasSize(1);
        verify(cachingPermissionEvaluator, never()).invalidate(anyString());
    }

    @Test
    @DisplayName("missing operator → OperatorNotFoundException")
    void missingOperator_notFound() {
        assertThatThrownBy(() -> useCase.patchRoles("ghost", List.of(), null, actor(), "reason"))
                .isInstanceOf(OperatorNotFoundException.class);
    }
}
