package com.example.admin.application;

import com.example.admin.application.exception.OrgAdminGrantOutOfCeilingException;
import com.example.admin.application.exception.OrgNodeNotFoundException;
import com.example.admin.application.exception.RoleGrantForbiddenException;
import com.example.admin.application.orgnode.CeilingView;
import com.example.admin.application.orgnode.OrgNodeView;
import com.example.admin.application.port.OrgNodePort;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.persistence.rbac.AdminGrantScopeEvaluator;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorRoleJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorRoleJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.AdminRoleJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminRoleJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.OrgNodeSubtreeResolver;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-492 / ADR-MONO-047 D5 — the grant path's three composed caps, and the fail-closed
 * ceiling.
 *
 * <p>Tree: {@code root ── biz}. The actor is {@code ORG_ADMIN @ root}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrgNodeAdminUseCaseTest {

    @Mock OrgNodePort orgNodePort;
    @Mock RoleGrantGuard roleGrantGuard;
    @Mock OrgNodeSubtreeResolver subtreeResolver;
    @Mock AdminActionAuditor auditor;
    @Mock AdminOperatorJpaRepository operators;
    @Mock AdminRoleJpaRepository roles;
    @Mock AdminOperatorRoleJpaRepository operatorRoles;
    @Mock AdminGrantScopeEvaluator grantScopeEvaluator;

    private OrgNodeAdminUseCase useCase;

    private static final OperatorContext ACTOR = new OperatorContext("actor-1", "jti");
    private static final String TARGET_OP = "target-1";

    @BeforeEach
    void setUp() {
        OrgNodeScopeGuard scopeGuard = new OrgNodeScopeGuard(grantScopeEvaluator, auditor);
        useCase = new OrgNodeAdminUseCase(orgNodePort, scopeGuard, roleGrantGuard, subtreeResolver,
                auditor, operators, roles, operatorRoles);

        when(orgNodePort.list()).thenReturn(List.of(
                node("root", null), node("biz", "root")));
        // The actor is ORG_ADMIN @ root → administers root and biz.
        when(grantScopeEvaluator.isPlatformScope("actor-1", Permission.ORG_MANAGE)).thenReturn(false);
        when(grantScopeEvaluator.grantedOrgNodeIds("actor-1", Permission.ORG_MANAGE)).thenReturn(Set.of("root"));

        // Build the stubbed mocks BEFORE the outer when(...) — calling a when()-using helper
        // inside a thenReturn(...) argument is a nested-stubbing error (UnfinishedStubbing).
        AdminOperatorJpaEntity actorEntity = operator(1L, "actor-1", "hq");
        AdminOperatorJpaEntity targetEntity = operator(2L, TARGET_OP, "hq");
        AdminRoleJpaEntity orgAdminRole = role(7L, "ORG_ADMIN");
        when(operators.findByOperatorId("actor-1")).thenReturn(Optional.of(actorEntity));
        when(operators.findByOperatorId(TARGET_OP)).thenReturn(Optional.of(targetEntity));
        when(roles.findByName("ORG_ADMIN")).thenReturn(Optional.of(orgAdminRole));
        when(auditor.newAuditId()).thenReturn("audit-1");
    }

    private static OrgNodeView node(String id, String parentId) {
        return new OrgNodeView(id, parentId, "n-" + id, 1, CeilingView.unbounded(), Instant.EPOCH, Instant.EPOCH);
    }

    private static AdminOperatorJpaEntity operator(long id, String externalId, String tenantId) {
        AdminOperatorJpaEntity e = mock(AdminOperatorJpaEntity.class);
        when(e.getId()).thenReturn(id);
        when(e.getOperatorId()).thenReturn(externalId);
        when(e.getTenantId()).thenReturn(tenantId);
        return e;
    }

    private static AdminRoleJpaEntity role(long id, String name) {
        AdminRoleJpaEntity r = mock(AdminRoleJpaEntity.class);
        when(r.getId()).thenReturn(id);
        when(r.getName()).thenReturn(name);
        return r;
    }

    @Test
    @DisplayName("grant at an administered node persists a node-scoped row and audits ORG_ADMIN_GRANT")
    void grant_persistsNodeScopedRow() {
        when(subtreeResolver.effectiveCeilingFailClosed("biz")).thenReturn(CeilingView.bounded(List.of("wms")));

        useCase.grantNodeAdmin(ACTOR, "biz", TARGET_OP, "ORG_ADMIN", "why");

        ArgumentCaptor<AdminOperatorRoleJpaEntity> row = ArgumentCaptor.forClass(AdminOperatorRoleJpaEntity.class);
        verify(operatorRoles).save(row.capture());
        assertThat(row.getValue().getOrgNodeId()).isEqualTo("biz");
        // BE-289 WI-2: tenant_id mirrors the TARGET operator's own tenant, it is not the scope.
        assertThat(row.getValue().getTenantId()).isEqualTo("hq");
        assertThat(row.getValue().getRoleId()).isEqualTo(7L);

        ArgumentCaptor<AdminActionAuditor.AuditRecord> audit =
                ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        verify(auditor).record(audit.capture());
        assertThat(audit.getValue().actionCode()).isEqualTo(ActionCode.ORG_ADMIN_GRANT);
        assertThat(audit.getValue().targetType()).isEqualTo("ORG_NODE");
        assertThat(audit.getValue().targetId()).isEqualTo("biz");
    }

    @Test
    @DisplayName("cap 1 — a node outside the actor's reach → 404, and nothing is written")
    void grant_outOfReachNode_is404() {
        when(grantScopeEvaluator.grantedOrgNodeIds("actor-1", Permission.ORG_MANAGE)).thenReturn(Set.of("biz"));
        // ORG_ADMIN @ biz does not administer root.
        assertThatThrownBy(() -> useCase.grantNodeAdmin(ACTOR, "root", TARGET_OP, "ORG_ADMIN", "why"))
                .isInstanceOf(OrgNodeNotFoundException.class);
        verify(operatorRoles, never()).save(any());
    }

    @Test
    @DisplayName("cap 2 — RoleGrantGuard (ADR-024 D3) is reused, not reinvented: its 403 propagates")
    void grant_noEscalation_delegatesToRoleGrantGuard() {
        when(subtreeResolver.effectiveCeilingFailClosed("biz")).thenReturn(CeilingView.unbounded());
        doThrow(new RoleGrantForbiddenException("may not grant SUPER_ADMIN"))
                .when(roleGrantGuard).requireGrantable(any(), any(), any());

        assertThatThrownBy(() -> useCase.grantNodeAdmin(ACTOR, "biz", TARGET_OP, "ORG_ADMIN", "why"))
                .isInstanceOf(RoleGrantForbiddenException.class);
        verify(operatorRoles, never()).save(any());
    }

    @Test
    @DisplayName("cap 2 runs BEFORE cap 3 — a forbidden role never costs a ceiling round-trip")
    void grant_roleGuardPrecedesCeiling() {
        doThrow(new RoleGrantForbiddenException("nope"))
                .when(roleGrantGuard).requireGrantable(any(), any(), any());

        assertThatThrownBy(() -> useCase.grantNodeAdmin(ACTOR, "biz", TARGET_OP, "ORG_ADMIN", "why"))
                .isInstanceOf(RoleGrantForbiddenException.class);
        verify(subtreeResolver, never()).effectiveCeilingFailClosed(anyString());
    }

    @Test
    @DisplayName("cap 3 — a ceiling that permits nothing (BOUNDED([])) refuses the grant → 422")
    void grant_emptyCeiling_is422() {
        when(subtreeResolver.effectiveCeilingFailClosed("biz")).thenReturn(CeilingView.bounded(List.of()));

        assertThatThrownBy(() -> useCase.grantNodeAdmin(ACTOR, "biz", TARGET_OP, "ORG_ADMIN", "why"))
                .isInstanceOf(OrgAdminGrantOutOfCeilingException.class);
        verify(operatorRoles, never()).save(any());
    }

    @Test
    @DisplayName("cap 3 fail-closed — account-service down resolves to BOUNDED([]) and DENIES; never UNBOUNDED")
    void grant_ceilingUnresolvable_deniesRatherThanFallingBackToUnbounded() {
        // This is exactly what OrgNodeSubtreeResolver returns when the authority is unreachable.
        when(subtreeResolver.effectiveCeilingFailClosed("biz")).thenReturn(CeilingView.failClosed());

        assertThatThrownBy(() -> useCase.grantNodeAdmin(ACTOR, "biz", TARGET_OP, "ORG_ADMIN", "why"))
                .isInstanceOf(OrgAdminGrantOutOfCeilingException.class);
        verify(operatorRoles, never()).save(any());
    }

    @Test
    @DisplayName("UNBOUNDED is not 'empty' — an unbounded node accepts the grant")
    void grant_unboundedCeiling_isPermitted() {
        when(subtreeResolver.effectiveCeilingFailClosed("biz")).thenReturn(CeilingView.unbounded());
        useCase.grantNodeAdmin(ACTOR, "biz", TARGET_OP, "ORG_ADMIN", "why");
        verify(operatorRoles).save(any());
    }

    @Test
    @DisplayName("an unknown grant target is 404 ORG_NODE_NOT_FOUND (enumeration-safe), not OPERATOR_NOT_FOUND")
    void grant_unknownOperator_is404OrgNode() {
        when(operators.findByOperatorId("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.grantNodeAdmin(ACTOR, "biz", "ghost", "ORG_ADMIN", "why"))
                .isInstanceOf(OrgNodeNotFoundException.class);
    }

    @Test
    @DisplayName("revoking a grant that does not exist → 404, no audit row")
    void revoke_missingGrant_is404() {
        when(operatorRoles.findByOperatorIdAndRoleIdAndOrgNodeId(2L, 7L, "biz")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.revokeNodeAdmin(ACTOR, "biz", TARGET_OP, "why"))
                .isInstanceOf(OrgNodeNotFoundException.class);
        verify(auditor, never()).record(any());
    }

    @Test
    @DisplayName("revoke deletes the row, invalidates the subtree cache, and audits ORG_ADMIN_REVOKE")
    void revoke_deletesRowAndInvalidatesCache() {
        AdminOperatorRoleJpaEntity row = mock(AdminOperatorRoleJpaEntity.class);
        when(operatorRoles.findByOperatorIdAndRoleIdAndOrgNodeId(2L, 7L, "biz")).thenReturn(Optional.of(row));

        useCase.revokeNodeAdmin(ACTOR, "biz", TARGET_OP, "why");

        verify(operatorRoles).delete(row);
        // A revoked grant must stop conferring reach immediately — a cached subtree would keep it alive.
        verify(subtreeResolver).invalidateAll();
        ArgumentCaptor<AdminActionAuditor.AuditRecord> audit =
                ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        verify(auditor).record(audit.capture());
        assertThat(audit.getValue().actionCode()).isEqualTo(ActionCode.ORG_ADMIN_REVOKE);
    }

    @Test
    @DisplayName("createNode: a ROOT (parentId=null) is refused for a non-platform actor")
    void createRoot_requiresPlatform() {
        assertThatThrownBy(() -> useCase.createNode(ACTOR, "New Corp", null, CeilingView.unbounded(), "why"))
                .isInstanceOf(com.example.admin.application.exception.PermissionDeniedException.class);
        verify(orgNodePort, never()).create(any(), any(), any());
    }

    @Test
    @DisplayName("setCeiling on the actor's OWN node → 403 self-ceiling; on a descendant → allowed")
    void setCeiling_selfIsDenied_descendantIsAllowed() {
        when(grantScopeEvaluator.grantedOrgNodeIds("actor-1", Permission.ORG_MANAGE)).thenReturn(Set.of("biz"));
        assertThatThrownBy(() -> useCase.setCeiling(ACTOR, "biz", CeilingView.unbounded(), "why"))
                .isInstanceOf(com.example.admin.application.exception.OrgNodeSelfCeilingDeniedException.class);
        verify(orgNodePort, never()).setCeiling(anyString(), any());

        // ORG_ADMIN @ root may set biz's ceiling (a strict descendant).
        when(grantScopeEvaluator.grantedOrgNodeIds("actor-1", Permission.ORG_MANAGE)).thenReturn(Set.of("root"));
        when(orgNodePort.setCeiling("biz", CeilingView.unbounded())).thenReturn(node("biz", "root"));
        useCase.setCeiling(ACTOR, "biz", CeilingView.unbounded(), "why");
        verify(orgNodePort).setCeiling("biz", CeilingView.unbounded());
    }
}
