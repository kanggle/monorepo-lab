package com.example.admin.infrastructure.persistence.rbac;

import com.example.admin.domain.rbac.Permission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ADR-MONO-024 D2 (TASK-BE-345) — unit coverage for the effective admin-grant
 * scope computation: the {@code tenant_id}s of the operator's
 * {@code admin_operator_roles} rows that grant a permission, with the {@code '*'}
 * platform sentinel preserved (net-zero for SUPER_ADMIN).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminGrantScopeEvaluatorTest {

    @Mock AdminOperatorJpaRepository operators;
    @Mock AdminOperatorRoleJpaRepository operatorRoles;
    @Mock AdminRolePermissionJpaRepository rolePermissions;

    @InjectMocks AdminGrantScopeEvaluator evaluator;

    private static final String OP = "00000000-0000-7000-8000-000000000001";

    private AdminOperatorJpaEntity activeOperator(long internalId) {
        AdminOperatorJpaEntity e = mock(AdminOperatorJpaEntity.class);
        when(e.getId()).thenReturn(internalId);
        when(e.getStatus()).thenReturn("ACTIVE");
        return e;
    }

    private AdminOperatorRoleJpaEntity roleRow(long roleId, String tenantId) {
        AdminOperatorRoleJpaEntity r = mock(AdminOperatorRoleJpaEntity.class);
        when(r.getRoleId()).thenReturn(roleId);
        when(r.getTenantId()).thenReturn(tenantId);
        return r;
    }

    @Test
    @DisplayName("platform grant ('*') → scope {'*'}; in-scope for every target (net-zero)")
    void platformGrant_scopeIsStar_allowsAnyTarget() {
        // Build mocks BEFORE the outer when(...) — calling a when()-using helper
        // inside a thenReturn(...) argument is a nested-stubbing error.
        AdminOperatorJpaEntity op = activeOperator(10L);
        List<AdminOperatorRoleJpaEntity> rows = List.of(roleRow(1L, "*"));
        when(operators.findByOperatorId(OP)).thenReturn(Optional.of(op));
        when(operatorRoles.findByOperatorId(10L)).thenReturn(rows);
        when(rolePermissions.findRoleIdsGrantingPermission(eq(Permission.OPERATOR_MANAGE), eq(List.of(1L))))
                .thenReturn(List.of(1L));

        assertThat(evaluator.effectiveAdminScope(OP, Permission.OPERATOR_MANAGE)).containsExactly("*");
        assertThat(evaluator.isTenantInAdminScope(OP, Permission.OPERATOR_MANAGE, "acme")).isTrue();
        assertThat(evaluator.isTenantInAdminScope(OP, Permission.OPERATOR_MANAGE, "globex")).isTrue();
        // '*' short-circuit applies even when the target is null.
        assertThat(evaluator.isTenantInAdminScope(OP, Permission.OPERATOR_MANAGE, null)).isTrue();
    }

    @Test
    @DisplayName("tenant-scoped grant ('acme') → scope {'acme'}; allows acme, denies globex + null")
    void tenantScopedGrant_confinedToThatTenant() {
        AdminOperatorJpaEntity op = activeOperator(10L);
        List<AdminOperatorRoleJpaEntity> rows = List.of(roleRow(1L, "acme"));
        when(operators.findByOperatorId(OP)).thenReturn(Optional.of(op));
        when(operatorRoles.findByOperatorId(10L)).thenReturn(rows);
        when(rolePermissions.findRoleIdsGrantingPermission(eq(Permission.OPERATOR_MANAGE), eq(List.of(1L))))
                .thenReturn(List.of(1L));

        assertThat(evaluator.effectiveAdminScope(OP, Permission.OPERATOR_MANAGE)).containsExactly("acme");
        assertThat(evaluator.isTenantInAdminScope(OP, Permission.OPERATOR_MANAGE, "acme")).isTrue();
        assertThat(evaluator.isTenantInAdminScope(OP, Permission.OPERATOR_MANAGE, "globex")).isFalse();
        assertThat(evaluator.isTenantInAdminScope(OP, Permission.OPERATOR_MANAGE, null)).isFalse();
    }

    @Test
    @DisplayName("only the granting role's tenant counts — a non-granting role's tenant is excluded")
    void onlyGrantingRoleTenantContributes() {
        AdminOperatorJpaEntity op = activeOperator(10L);
        // role 1 @ acme grants operator.manage; role 2 @ globex does NOT.
        List<AdminOperatorRoleJpaEntity> rows = List.of(roleRow(1L, "acme"), roleRow(2L, "globex"));
        when(operators.findByOperatorId(OP)).thenReturn(Optional.of(op));
        when(operatorRoles.findByOperatorId(10L)).thenReturn(rows);
        when(rolePermissions.findRoleIdsGrantingPermission(eq(Permission.OPERATOR_MANAGE), eq(List.of(1L, 2L))))
                .thenReturn(List.of(1L));

        assertThat(evaluator.effectiveAdminScope(OP, Permission.OPERATOR_MANAGE)).containsExactly("acme");
        assertThat(evaluator.isTenantInAdminScope(OP, Permission.OPERATOR_MANAGE, "globex")).isFalse();
    }

    @Test
    @DisplayName("operator holds roles but none grant the permission → empty scope")
    void noRoleGrantsPermission_emptyScope() {
        AdminOperatorJpaEntity op = activeOperator(10L);
        List<AdminOperatorRoleJpaEntity> rows = List.of(roleRow(2L, "acme"));
        when(operators.findByOperatorId(OP)).thenReturn(Optional.of(op));
        when(operatorRoles.findByOperatorId(10L)).thenReturn(rows);
        when(rolePermissions.findRoleIdsGrantingPermission(eq(Permission.OPERATOR_MANAGE), eq(List.of(2L))))
                .thenReturn(List.of());

        assertThat(evaluator.effectiveAdminScope(OP, Permission.OPERATOR_MANAGE)).isEmpty();
        assertThat(evaluator.isTenantInAdminScope(OP, Permission.OPERATOR_MANAGE, "acme")).isFalse();
    }

    @Test
    @DisplayName("unknown operator → empty scope (fail-closed)")
    void unknownOperator_emptyScope() {
        when(operators.findByOperatorId(anyString())).thenReturn(Optional.empty());
        assertThat(evaluator.effectiveAdminScope(OP, Permission.OPERATOR_MANAGE)).isEmpty();
        assertThat(evaluator.isTenantInAdminScope(OP, Permission.OPERATOR_MANAGE, "acme")).isFalse();
    }

    @Test
    @DisplayName("inactive operator → empty scope (fail-closed)")
    void inactiveOperator_emptyScope() {
        AdminOperatorJpaEntity suspended = mock(AdminOperatorJpaEntity.class);
        when(suspended.getStatus()).thenReturn("SUSPENDED");
        when(operators.findByOperatorId(OP)).thenReturn(Optional.of(suspended));

        assertThat(evaluator.effectiveAdminScope(OP, Permission.OPERATOR_MANAGE)).isEmpty();
    }

    @Test
    @DisplayName("null operatorId / permission → empty scope")
    void nullArgs_emptyScope() {
        assertThat(evaluator.effectiveAdminScope(null, Permission.OPERATOR_MANAGE)).isEqualTo(Set.of());
        assertThat(evaluator.effectiveAdminScope(OP, null)).isEqualTo(Set.of());
    }
}
