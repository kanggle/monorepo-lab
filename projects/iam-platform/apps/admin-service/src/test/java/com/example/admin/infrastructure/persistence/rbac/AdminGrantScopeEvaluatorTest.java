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
import static org.mockito.Mockito.verifyNoInteractions;
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
    @Mock OrgNodeSubtreeResolver subtreeResolver;

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

    /** ADR-MONO-047 D5 — a node-scoped grant row: {@code tenant_id} mirrors the operator's own tenant. */
    private AdminOperatorRoleJpaEntity nodeScopedRow(long roleId, String ownTenantId, String orgNodeId) {
        AdminOperatorRoleJpaEntity r = roleRow(roleId, ownTenantId);
        when(r.getOrgNodeId()).thenReturn(orgNodeId);
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

    // ── ADR-MONO-047 D5 — the org-node subtree driver (TASK-BE-492) ──────────────

    @Test
    @DisplayName("D5: node-scoped grant → scope is the node's subtree tenants (NOT the row's tenant_id)")
    void nodeScopedGrant_scopeIsSubtree() {
        AdminOperatorJpaEntity op = activeOperator(10L);
        // The row's tenant_id mirrors the operator's OWN tenant (BE-289 WI-2 audit column).
        // It must not leak into the scope; org_node_id drives it.
        List<AdminOperatorRoleJpaEntity> rows = List.of(nodeScopedRow(1L, "acme-hq", "node-acme"));
        when(operators.findByOperatorId(OP)).thenReturn(Optional.of(op));
        when(operatorRoles.findByOperatorId(10L)).thenReturn(rows);
        when(rolePermissions.findRoleIdsGrantingPermission(eq(Permission.OPERATOR_MANAGE), eq(List.of(1L))))
                .thenReturn(List.of(1L));
        when(subtreeResolver.subtreeTenantIdsFailClosed("node-acme"))
                .thenReturn(Set.of("acme-wms", "acme-erp"));

        assertThat(evaluator.effectiveAdminScope(OP, Permission.OPERATOR_MANAGE))
                .containsExactlyInAnyOrder("acme-wms", "acme-erp")
                .doesNotContain("acme-hq");
        assertThat(evaluator.isTenantInAdminScope(OP, Permission.OPERATOR_MANAGE, "acme-wms")).isTrue();
        assertThat(evaluator.isTenantInAdminScope(OP, Permission.OPERATOR_MANAGE, "globex")).isFalse();
    }

    @Test
    @DisplayName("D5 fail-closed: subtree resolves empty → NO reach; never '*', never all-tenants")
    void nodeScopedGrant_subtreeUnresolvable_failsClosed() {
        AdminOperatorJpaEntity op = activeOperator(10L);
        List<AdminOperatorRoleJpaEntity> rows = List.of(nodeScopedRow(1L, "acme-hq", "node-acme"));
        when(operators.findByOperatorId(OP)).thenReturn(Optional.of(op));
        when(operatorRoles.findByOperatorId(10L)).thenReturn(rows);
        when(rolePermissions.findRoleIdsGrantingPermission(eq(Permission.OPERATOR_MANAGE), eq(List.of(1L))))
                .thenReturn(List.of(1L));
        // The resolver already swallowed the DownstreamFailure and returned the EMPTY set.
        when(subtreeResolver.subtreeTenantIdsFailClosed("node-acme")).thenReturn(Set.of());

        Set<String> scope = evaluator.effectiveAdminScope(OP, Permission.OPERATOR_MANAGE);
        assertThat(scope).isEmpty();
        assertThat(scope).doesNotContain("*");
        assertThat(evaluator.isTenantInAdminScope(OP, Permission.OPERATOR_MANAGE, "acme-wms")).isFalse();
        // The company admin loses reach; it is never silently promoted to platform-wide.
        assertThat(evaluator.isTenantInAdminScope(OP, Permission.OPERATOR_MANAGE, "anything")).isFalse();
    }

    @Test
    @DisplayName("D5 order invariant: '*' is pre-scanned FIRST — a SUPER_ADMIN never pays a subtree round-trip")
    void platformPreScan_precedesSubtreeExpansion_regardlessOfRowOrder() {
        AdminOperatorJpaEntity op = activeOperator(10L);
        // The node-scoped row comes FIRST. An in-loop short-circuit would call the resolver
        // before reaching the '*' row — and an account-service outage would then strip the
        // SUPER_ADMIN of platform reach. The pre-scan makes row order irrelevant.
        List<AdminOperatorRoleJpaEntity> rows =
                List.of(nodeScopedRow(1L, "acme-hq", "node-acme"), roleRow(2L, "*"));
        when(operators.findByOperatorId(OP)).thenReturn(Optional.of(op));
        when(operatorRoles.findByOperatorId(10L)).thenReturn(rows);
        when(rolePermissions.findRoleIdsGrantingPermission(eq(Permission.OPERATOR_MANAGE), eq(List.of(1L, 2L))))
                .thenReturn(List.of(1L, 2L));

        assertThat(evaluator.effectiveAdminScope(OP, Permission.OPERATOR_MANAGE)).containsExactly("*");
        assertThat(evaluator.isTenantInAdminScope(OP, Permission.OPERATOR_MANAGE, "anything")).isTrue();
        verifyNoInteractions(subtreeResolver);
    }

    @Test
    @DisplayName("D5: nested grants union — parent-node subtree ∪ tenant-scoped grant")
    void nodeAndTenantGrants_union() {
        AdminOperatorJpaEntity op = activeOperator(10L);
        List<AdminOperatorRoleJpaEntity> rows =
                List.of(nodeScopedRow(1L, "acme-hq", "node-acme"), roleRow(2L, "globex"));
        when(operators.findByOperatorId(OP)).thenReturn(Optional.of(op));
        when(operatorRoles.findByOperatorId(10L)).thenReturn(rows);
        when(rolePermissions.findRoleIdsGrantingPermission(eq(Permission.OPERATOR_MANAGE), eq(List.of(1L, 2L))))
                .thenReturn(List.of(1L, 2L));
        when(subtreeResolver.subtreeTenantIdsFailClosed("node-acme")).thenReturn(Set.of("acme-wms"));

        assertThat(evaluator.effectiveAdminScope(OP, Permission.OPERATOR_MANAGE))
                .containsExactlyInAnyOrder("acme-wms", "globex");
    }

    @Test
    @DisplayName("D5: grantedOrgNodeIds / isPlatformScope are pure DB — no subtree round-trip")
    void reachPredicateInputs_neverRoundTrip() {
        AdminOperatorJpaEntity op = activeOperator(10L);
        List<AdminOperatorRoleJpaEntity> rows = List.of(nodeScopedRow(1L, "acme-hq", "node-acme"));
        when(operators.findByOperatorId(OP)).thenReturn(Optional.of(op));
        when(operatorRoles.findByOperatorId(10L)).thenReturn(rows);
        when(rolePermissions.findRoleIdsGrantingPermission(eq(Permission.ORG_MANAGE), eq(List.of(1L))))
                .thenReturn(List.of(1L));

        assertThat(evaluator.grantedOrgNodeIds(OP, Permission.ORG_MANAGE)).containsExactly("node-acme");
        assertThat(evaluator.isPlatformScope(OP, Permission.ORG_MANAGE)).isFalse();
        verifyNoInteractions(subtreeResolver);
    }

    @Test
    @DisplayName("D5: a platform actor reports isPlatformScope=true and holds no org-node grant")
    void platformActor_hasNoNodeGrant() {
        AdminOperatorJpaEntity op = activeOperator(10L);
        List<AdminOperatorRoleJpaEntity> rows = List.of(roleRow(1L, "*"));
        when(operators.findByOperatorId(OP)).thenReturn(Optional.of(op));
        when(operatorRoles.findByOperatorId(10L)).thenReturn(rows);
        when(rolePermissions.findRoleIdsGrantingPermission(eq(Permission.ORG_MANAGE), eq(List.of(1L))))
                .thenReturn(List.of(1L));

        assertThat(evaluator.isPlatformScope(OP, Permission.ORG_MANAGE)).isTrue();
        assertThat(evaluator.grantedOrgNodeIds(OP, Permission.ORG_MANAGE)).isEmpty();
    }
}
