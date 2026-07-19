package com.example.admin.application;

import com.example.admin.application.exception.GroupGrantAlreadyExistsException;
import com.example.admin.application.exception.GroupGrantNoEscalationException;
import com.example.admin.application.exception.GroupMemberAlreadyExistsException;
import com.example.admin.application.exception.GroupMemberNotFoundException;
import com.example.admin.application.exception.GroupMemberTenantMismatchException;
import com.example.admin.application.exception.GroupNameConflictException;
import com.example.admin.application.exception.RoleGrantForbiddenException;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.persistence.rbac.AdminGrantScopeEvaluator;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.AdminRoleJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminRoleJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.OperatorGroupGrantJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.OperatorGroupGrantJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.OperatorGroupJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.OperatorGroupJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.OperatorGroupMemberJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.OperatorGroupMemberJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-520 / ADR-MONO-046 — unit coverage for the operator-group command gateway: guard
 * composition (D3 tenant confinement, D4 no-escalation at grant AND add-member), and the
 * fan-out/cascade delegation (D5). Mirrors {@code RoleGrantGuardTest} (LENIENT — the gateway
 * fans a common seed graph across many collaborators).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GroupAdminUseCaseTest {

    @Mock OperatorGroupJpaRepository operatorGroups;
    @Mock OperatorGroupMemberJpaRepository groupMembers;
    @Mock OperatorGroupGrantJpaRepository groupGrants;
    @Mock AdminOperatorJpaRepository adminOperators;
    @Mock AdminRoleJpaRepository adminRoles;
    @Mock TenantScopeGuard tenantScopeGuard;
    @Mock RoleGrantGuard roleGrantGuard;
    @Mock AdminGrantScopeEvaluator grantScopeEvaluator;
    @Mock GroupFanOutService fanOut;
    @Mock AdminActionAuditor auditor;

    @InjectMocks GroupAdminUseCase useCase;

    private final OperatorContext actor = new OperatorContext("actor-uuid", "jti-1");

    private OperatorGroupJpaEntity group(String tenant) {
        OperatorGroupJpaEntity g = OperatorGroupJpaEntity.create(
                "grp-1", tenant, "물류 지원팀", null, null, Instant.EPOCH);
        return g;
    }

    private AdminOperatorJpaEntity operator(String uuid, String tenant) {
        // Mock (not the factory) so getId() is a non-null surrogate — an unpersisted entity
        // would have a null id and the fan-out Member(long) would NPE on unbox.
        AdminOperatorJpaEntity op = mock(AdminOperatorJpaEntity.class);
        when(op.getId()).thenReturn(100L);
        when(op.getOperatorId()).thenReturn(uuid);
        when(op.getTenantId()).thenReturn(tenant);
        when(op.getDisplayName()).thenReturn("김운영");
        return op;
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create: D3 TenantScopeGuard runs first; duplicate (tenant,name) → 409 GROUP_NAME_CONFLICT")
    void create_nameConflict() {
        when(operatorGroups.existsByTenantIdAndName("acme", "물류 지원팀")).thenReturn(true);

        assertThatThrownBy(() -> useCase.createGroup(actor, "acme", "물류 지원팀", null, "r"))
                .isInstanceOf(GroupNameConflictException.class);

        verify(tenantScopeGuard).requireTenantInScope(
                eq(actor), eq(Permission.GROUP_MANAGE), eq("acme"), eq(ActionCode.GROUP_CREATE));
        verify(operatorGroups, never()).save(any());
    }

    @Test
    @DisplayName("create: platform sentinel tenantId '*' → VALIDATION_ERROR (IllegalArgumentException)")
    void create_platformTenantRejected() {
        assertThatThrownBy(() -> useCase.createGroup(actor, "*", "x", null, "r"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("create: happy path saves + audits SUCCESS")
    void create_happy() {
        when(operatorGroups.existsByTenantIdAndName(any(), any())).thenReturn(false);
        when(operatorGroups.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GroupAdminUseCase.GroupView view = useCase.createGroup(actor, "acme", "물류 지원팀", "squad", "r");

        assertThat(view.tenantId()).isEqualTo("acme");
        assertThat(view.name()).isEqualTo("물류 지원팀");
        verify(operatorGroups).save(any());
        verify(auditor).record(any());
    }

    // ── grants: no-escalation (D4, grant-time) ─────────────────────────────────

    @Test
    @DisplayName("addGrants ROLE: RoleGrantGuard.requireGrantable denial propagates (403 ROLE_GRANT_FORBIDDEN)")
    void addGrants_roleNoEscalation() {
        when(operatorGroups.findByGroupId("grp-1")).thenReturn(Optional.of(group("acme")));
        AdminRoleJpaEntity role = mock(AdminRoleJpaEntity.class);
        when(role.getId()).thenReturn(5L);
        when(role.getName()).thenReturn("SUPER_ADMIN");
        when(adminRoles.findByName("SUPER_ADMIN")).thenReturn(Optional.of(role));
        doThrow(new RoleGrantForbiddenException("no")).when(roleGrantGuard)
                .requireGrantable(any(), any(), eq(ActionCode.GROUP_GRANT_ADD));

        assertThatThrownBy(() -> useCase.addGrants(actor, "grp-1", List.of("SUPER_ADMIN"), List.of(), "r"))
                .isInstanceOf(RoleGrantForbiddenException.class);

        verify(groupGrants, never()).save(any());
    }

    @Test
    @DisplayName("addGrants TENANT_ASSIGNMENT: target outside actor operator.manage scope → 422 GROUP_GRANT_NO_ESCALATION")
    void addGrants_tenantNoEscalation() {
        when(operatorGroups.findByGroupId("grp-1")).thenReturn(Optional.of(group("acme")));
        when(grantScopeEvaluator.isTenantInAdminScope("actor-uuid", Permission.OPERATOR_MANAGE, "globex"))
                .thenReturn(false);

        assertThatThrownBy(() -> useCase.addGrants(actor, "grp-1", List.of(), List.of("globex"), "r"))
                .isInstanceOf(GroupGrantNoEscalationException.class);

        verify(groupGrants, never()).save(any());
    }

    @Test
    @DisplayName("addGrants: empty roles + empty tenantAssignments → VALIDATION_ERROR")
    void addGrants_emptyBody() {
        when(operatorGroups.findByGroupId("grp-1")).thenReturn(Optional.of(group("acme")));
        assertThatThrownBy(() -> useCase.addGrants(actor, "grp-1", List.of(), List.of(), "r"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("addGrants TENANT_ASSIGNMENT happy: within scope → grant template saved + fanned out")
    void addGrants_tenantHappy() {
        when(operatorGroups.findByGroupId("grp-1")).thenReturn(Optional.of(group("acme")));
        when(grantScopeEvaluator.isTenantInAdminScope("actor-uuid", Permission.OPERATOR_MANAGE, "acme"))
                .thenReturn(true);
        when(groupGrants.existsByGroupIdAndGrantTypeAndTenantId(any(), any(), any())).thenReturn(false);
        when(groupGrants.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(groupMembers.findByGroupId(any())).thenReturn(List.of());
        when(fanOut.fanOutGrantToMembers(any(), any(), any(), any(), any())).thenReturn(3);

        GroupAdminUseCase.GrantAddResult result =
                useCase.addGrants(actor, "grp-1", List.of(), List.of("acme"), "r");

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).type()).isEqualTo("TENANT_ASSIGNMENT");
        assertThat(result.fannedOutRows()).isEqualTo(3);
        verify(auditor).record(any());
    }

    @Test
    @DisplayName("addGrants: duplicate grant template → 409 GROUP_GRANT_ALREADY_EXISTS")
    void addGrants_duplicate() {
        when(operatorGroups.findByGroupId("grp-1")).thenReturn(Optional.of(group("acme")));
        when(grantScopeEvaluator.isTenantInAdminScope("actor-uuid", Permission.OPERATOR_MANAGE, "acme"))
                .thenReturn(true);
        when(groupGrants.existsByGroupIdAndGrantTypeAndTenantId(any(), any(), eq("acme"))).thenReturn(true);

        assertThatThrownBy(() -> useCase.addGrants(actor, "grp-1", List.of(), List.of("acme"), "r"))
                .isInstanceOf(GroupGrantAlreadyExistsException.class);
    }

    // ── members: tenant match, dup, no-escalation re-check (D4, add-member) ─────

    @Test
    @DisplayName("addMember: operator home tenant ≠ group tenant → 422 GROUP_MEMBER_TENANT_MISMATCH")
    void addMember_tenantMismatch() {
        when(operatorGroups.findByGroupId("grp-1")).thenReturn(Optional.of(group("acme")));
        AdminOperatorJpaEntity target = operator("op-9", "globex");
        when(adminOperators.findByOperatorId("op-9")).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> useCase.addMember(actor, "grp-1", "op-9", "r"))
                .isInstanceOf(GroupMemberTenantMismatchException.class);
    }

    @Test
    @DisplayName("addMember: already a member → 409 GROUP_MEMBER_ALREADY_EXISTS")
    void addMember_alreadyExists() {
        when(operatorGroups.findByGroupId("grp-1")).thenReturn(Optional.of(group("acme")));
        AdminOperatorJpaEntity target = operator("op-9", "acme");
        when(adminOperators.findByOperatorId("op-9")).thenReturn(Optional.of(target));
        when(groupMembers.existsById(any())).thenReturn(true);

        assertThatThrownBy(() -> useCase.addMember(actor, "grp-1", "op-9", "r"))
                .isInstanceOf(GroupMemberAlreadyExistsException.class);
    }

    @Test
    @DisplayName("addMember: fan-out no-escalation re-check — a group TENANT grant outside actor scope → 422")
    void addMember_fanoutNoEscalation() {
        when(operatorGroups.findByGroupId("grp-1")).thenReturn(Optional.of(group("acme")));
        AdminOperatorJpaEntity target = operator("op-9", "acme");
        when(adminOperators.findByOperatorId("op-9")).thenReturn(Optional.of(target));
        when(groupMembers.existsById(any())).thenReturn(false);
        when(groupGrants.findByGroupId(any())).thenReturn(List.of(
                OperatorGroupGrantJpaEntity.tenantAssignment("g-ta", 1L, "globex", null, Instant.EPOCH)));
        when(grantScopeEvaluator.isTenantInAdminScope("actor-uuid", Permission.OPERATOR_MANAGE, "globex"))
                .thenReturn(false);

        assertThatThrownBy(() -> useCase.addMember(actor, "grp-1", "op-9", "r"))
                .isInstanceOf(GroupGrantNoEscalationException.class);

        verify(groupMembers, never()).save(any());
        verify(fanOut, never()).fanOutGrantsToMember(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("addMember happy: membership saved, group's current grants fanned onto the new member")
    void addMember_happy() {
        when(operatorGroups.findByGroupId("grp-1")).thenReturn(Optional.of(group("acme")));
        AdminOperatorJpaEntity target = operator("op-9", "acme");
        when(adminOperators.findByOperatorId("op-9")).thenReturn(Optional.of(target));
        when(groupMembers.existsById(any())).thenReturn(false);
        when(groupGrants.findByGroupId(any())).thenReturn(List.of());
        when(fanOut.fanOutGrantsToMember(any(), any(), any(), any(), any())).thenReturn(0);

        GroupAdminUseCase.MemberAddResult result = useCase.addMember(actor, "grp-1", "op-9", "r");

        assertThat(result.operatorId()).isEqualTo("op-9");
        verify(groupMembers).save(any());
        verify(roleGrantGuard).requireGrantable(eq(actor), eq(List.of()), eq(ActionCode.GROUP_MEMBER_ADD));
        verify(auditor).record(any());
    }

    // ── members: remove revokes only group_origin rows (direct grants survive) ──

    @Test
    @DisplayName("removeMember: not a member → 404 GROUP_MEMBER_NOT_FOUND")
    void removeMember_notMember() {
        when(operatorGroups.findByGroupId("grp-1")).thenReturn(Optional.of(group("acme")));
        AdminOperatorJpaEntity target = operator("op-9", "acme");
        when(adminOperators.findByOperatorId("op-9")).thenReturn(Optional.of(target));
        when(groupMembers.existsById(any())).thenReturn(false);

        assertThatThrownBy(() -> useCase.removeMember(actor, "grp-1", "op-9", "r"))
                .isInstanceOf(GroupMemberNotFoundException.class);
    }

    @Test
    @DisplayName("removeMember happy: revokes ONLY the member's group_origin rows, then deletes membership")
    void removeMember_happy() {
        when(operatorGroups.findByGroupId("grp-1")).thenReturn(Optional.of(group("acme")));
        AdminOperatorJpaEntity target = operator("op-9", "acme");
        when(adminOperators.findByOperatorId("op-9")).thenReturn(Optional.of(target));
        when(groupMembers.existsById(any())).thenReturn(true);

        useCase.removeMember(actor, "grp-1", "op-9", "r");

        verify(fanOut).revokeMemberFanOut(any(), any());
        verify(groupMembers).deleteById(any());
        verify(auditor).record(any());
    }

    // ── delete: cascade-revoke all group_origin rows ───────────────────────────

    @Test
    @DisplayName("deleteGroup: cascade-revokes all group_origin rows then deletes the group")
    void deleteGroup_cascade() {
        when(operatorGroups.findByGroupId("grp-1")).thenReturn(Optional.of(group("acme")));
        when(groupMembers.findByGroupId(any())).thenReturn(List.of());

        useCase.deleteGroup(actor, "grp-1", "r");

        verify(fanOut).revokeGroupFanOut(any(), any());
        verify(operatorGroups).delete(any());
        verify(tenantScopeGuard).requireTenantInScope(
                eq(actor), eq(Permission.GROUP_MANAGE), eq("acme"), eq(ActionCode.GROUP_DELETE));
        verify(auditor).record(any());
    }

    @Test
    @DisplayName("update: rename to a name that already exists in the tenant → 409 GROUP_NAME_CONFLICT")
    void update_renameConflict() {
        when(operatorGroups.findByGroupId("grp-1")).thenReturn(Optional.of(group("acme")));
        when(operatorGroups.existsByTenantIdAndName("acme", "새 이름")).thenReturn(true);

        assertThatThrownBy(() -> useCase.updateGroup(actor, "grp-1", "새 이름", null, "r"))
                .isInstanceOf(GroupNameConflictException.class);
    }

    @Test
    @DisplayName("getGroup / list confinement uses the group.manage effective admin scope, not operator.manage")
    void reads_useGroupManageScope() {
        // sanity: getGroup resolves scope via GROUP_MANAGE (enumeration-safe path).
        when(operatorGroups.findByGroupId("grp-1")).thenReturn(Optional.of(group("acme")));
        when(grantScopeEvaluator.isTenantInAdminScope("actor-uuid", Permission.GROUP_MANAGE, "acme"))
                .thenReturn(true);
        when(groupMembers.countByGroupId(anyLong())).thenReturn(0L);
        when(groupGrants.countByGroupId(anyLong())).thenReturn(0L);

        assertThatCode(() -> useCase.getGroup(actor, "grp-1")).doesNotThrowAnyException();
        verify(grantScopeEvaluator).isTenantInAdminScope("actor-uuid", Permission.GROUP_MANAGE, "acme");
    }
}
