package com.example.admin.application;

import com.example.admin.application.exception.RoleGrantForbiddenException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.persistence.rbac.AdminGrantScopeEvaluator;
import com.example.admin.infrastructure.persistence.rbac.AdminRolePermissionJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ADR-MONO-024 D3 (TASK-BE-347) — unit coverage for the grant-menu no-escalation
 * rule: platform-scope unconstrained (net-zero); non-platform denies SUPER_ADMIN
 * and any role carrying a permission the actor does not hold (≤-own), and admits
 * roles whose permissions ⊆ the actor's own.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoleGrantGuardTest {

    @Mock AdminGrantScopeEvaluator grantScopeEvaluator;
    @Mock com.example.admin.domain.rbac.PermissionEvaluator permissionEvaluator;
    @Mock AdminRolePermissionJpaRepository rolePermissions;
    @Mock AdminActionAuditor auditor;

    @InjectMocks RoleGrantGuard guard;

    private final OperatorContext actor = new OperatorContext("actor-uuid", "jti-1");

    private static AdminOperatorPort.RoleView role(long id, String name) {
        return new AdminOperatorPort.RoleView(id, name, name, false);
    }

    @Test
    @DisplayName("platform-scope actor ('*') → unconstrained menu, no deny audit (net-zero)")
    void platformScope_unconstrained() {
        when(grantScopeEvaluator.effectiveAdminScope("actor-uuid", Permission.OPERATOR_MANAGE))
                .thenReturn(Set.of("*"));

        assertThatCode(() -> guard.requireGrantable(
                actor, List.of(role(1L, "SUPER_ADMIN"), role(2L, "TENANT_ADMIN")),
                ActionCode.OPERATOR_ROLE_CHANGE))
                .doesNotThrowAnyException();

        verify(auditor, never()).recordRoleGrantForbidden(any(), any(), anyString());
    }

    @Test
    @DisplayName("non-platform actor granting SUPER_ADMIN → 403 ROLE_GRANT_FORBIDDEN + audit")
    void nonPlatform_grantSuperAdmin_denied() {
        when(grantScopeEvaluator.effectiveAdminScope("actor-uuid", Permission.OPERATOR_MANAGE))
                .thenReturn(Set.of("acme"));

        assertThatThrownBy(() -> guard.requireGrantable(
                actor, List.of(role(1L, "SUPER_ADMIN")), ActionCode.OPERATOR_ROLE_CHANGE))
                .isInstanceOf(RoleGrantForbiddenException.class);

        verify(auditor).recordRoleGrantForbidden(eq(actor), eq(ActionCode.OPERATOR_ROLE_CHANGE), eq("SUPER_ADMIN"));
    }

    @Test
    @DisplayName("non-platform actor granting a role with a permission it lacks → 403 (≤-own)")
    void nonPlatform_grantRoleExceedingOwn_denied() {
        when(grantScopeEvaluator.effectiveAdminScope("actor-uuid", Permission.OPERATOR_MANAGE))
                .thenReturn(Set.of("acme"));
        when(rolePermissions.findPermissionKeysByRoleIds(List.of(10L)))
                .thenReturn(List.of("subscription.manage"));
        when(permissionEvaluator.hasAllPermissions("actor-uuid", List.of("subscription.manage")))
                .thenReturn(false);

        assertThatThrownBy(() -> guard.requireGrantable(
                actor, List.of(role(10L, "TENANT_BILLING_ADMIN")), ActionCode.OPERATOR_ROLE_CHANGE))
                .isInstanceOf(RoleGrantForbiddenException.class);

        verify(auditor).recordRoleGrantForbidden(eq(actor), any(), eq("TENANT_BILLING_ADMIN"));
    }

    @Test
    @DisplayName("non-platform actor granting a role whose permissions ⊆ own → allowed (sub-delegation)")
    void nonPlatform_grantRoleWithinOwn_allowed() {
        when(grantScopeEvaluator.effectiveAdminScope("actor-uuid", Permission.OPERATOR_MANAGE))
                .thenReturn(Set.of("acme"));
        when(rolePermissions.findPermissionKeysByRoleIds(List.of(11L)))
                .thenReturn(List.of("operator.manage", "tenant.admin.delegate"));
        when(permissionEvaluator.hasAllPermissions(
                "actor-uuid", List.of("operator.manage", "tenant.admin.delegate")))
                .thenReturn(true);

        assertThatCode(() -> guard.requireGrantable(
                actor, List.of(role(11L, "TENANT_ADMIN")), ActionCode.OPERATOR_ROLE_CHANGE))
                .doesNotThrowAnyException();

        verify(auditor, never()).recordRoleGrantForbidden(any(), any(), anyString());
    }

    @Test
    @DisplayName("empty roles → no-op")
    void emptyRoles_noop() {
        assertThatCode(() -> guard.requireGrantable(actor, List.of(), ActionCode.OPERATOR_CREATE))
                .doesNotThrowAnyException();
        verify(auditor, never()).recordRoleGrantForbidden(any(), any(), anyString());
    }

    // ── grantableRoleNames (ADR-024 D3 read mirror, TASK-BE-388) ────────────────

    @Test
    @DisplayName("grantableRoleNames: platform-scope actor ('*') → every input role, no audit")
    void grantableRoleNames_platformScope_returnsAll() {
        when(grantScopeEvaluator.effectiveAdminScope("actor-uuid", Permission.OPERATOR_MANAGE))
                .thenReturn(Set.of("*"));

        List<String> result = guard.grantableRoleNames(actor, List.of(
                role(1L, "SUPER_ADMIN"), role(2L, "TENANT_ADMIN"), role(3L, "SUPPORT_LOCK")));

        assertThat(result).containsExactly("SUPER_ADMIN", "TENANT_ADMIN", "SUPPORT_LOCK");
        verify(auditor, never()).recordRoleGrantForbidden(any(), any(), anyString());
    }

    @Test
    @DisplayName("grantableRoleNames: non-platform actor → excludes SUPER_ADMIN + roles exceeding own (≤-own)")
    void grantableRoleNames_nonPlatform_filtersToSubset() {
        when(grantScopeEvaluator.effectiveAdminScope("actor-uuid", Permission.OPERATOR_MANAGE))
                .thenReturn(Set.of("acme"));
        // TENANT_ADMIN (id 20) permissions ⊆ own → grantable.
        when(rolePermissions.findPermissionKeysByRoleIds(List.of(20L)))
                .thenReturn(List.of("operator.manage", "tenant.admin.delegate"));
        when(permissionEvaluator.hasAllPermissions(
                "actor-uuid", List.of("operator.manage", "tenant.admin.delegate")))
                .thenReturn(true);
        // TENANT_BILLING_ADMIN (id 21) carries a permission the actor lacks → excluded.
        when(rolePermissions.findPermissionKeysByRoleIds(List.of(21L)))
                .thenReturn(List.of("subscription.manage"));
        when(permissionEvaluator.hasAllPermissions("actor-uuid", List.of("subscription.manage")))
                .thenReturn(false);

        List<String> result = guard.grantableRoleNames(actor, List.of(
                role(1L, "SUPER_ADMIN"),          // (a) platform/privileged → excluded
                role(20L, "TENANT_ADMIN"),        // ≤-own → included
                role(21L, "TENANT_BILLING_ADMIN") // exceeds own → excluded
        ));

        assertThat(result).containsExactly("TENANT_ADMIN");
        verify(auditor, never()).recordRoleGrantForbidden(any(), any(), anyString());
    }

    @Test
    @DisplayName("grantableRoleNames: non-platform actor → empty-permission role is grantable")
    void grantableRoleNames_emptyPermissionRole_isGrantable() {
        when(grantScopeEvaluator.effectiveAdminScope("actor-uuid", Permission.OPERATOR_MANAGE))
                .thenReturn(Set.of("acme"));
        when(rolePermissions.findPermissionKeysByRoleIds(List.of(30L)))
                .thenReturn(List.of());

        List<String> result = guard.grantableRoleNames(actor, List.of(role(30L, "EMPTY_ROLE")));

        assertThat(result).containsExactly("EMPTY_ROLE");
        verify(auditor, never()).recordRoleGrantForbidden(any(), any(), anyString());
    }

    @Test
    @DisplayName("grantableRoleNames: null/empty role set → empty list")
    void grantableRoleNames_emptyInput_returnsEmpty() {
        assertThat(guard.grantableRoleNames(actor, List.of())).isEmpty();
        assertThat(guard.grantableRoleNames(actor, null)).isEmpty();
    }
}
