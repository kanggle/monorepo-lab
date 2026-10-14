package com.example.admin.application;

import com.example.admin.infrastructure.persistence.rbac.AdminOperatorRoleJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorRoleJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.CachingPermissionEvaluator;
import com.example.admin.infrastructure.persistence.rbac.OperatorDomainSetGrantJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.OperatorGroupGrantJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.OperatorTenantAssignmentJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.OperatorTenantAssignmentJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-520 / ADR-MONO-046 D5 — unit coverage for the fan-out / cascade engine: idempotent
 * materialise (skip on equal existing PK row), and STRICT {@code group_origin} cascade-revoke
 * (never touching direct grants, which live in the same tables).
 */
@ExtendWith(MockitoExtension.class)
class GroupFanOutServiceTest {

    @Mock AdminOperatorRoleJpaRepository operatorRoles;
    @Mock OperatorTenantAssignmentJpaRepository assignments;
    @Mock OperatorDomainSetGrantJpaRepository domainSetGrants;
    @Mock CachingPermissionEvaluator cachingPermissionEvaluator;

    @InjectMocks GroupFanOutService service;

    private static final Long GROUP = 42L;
    private final Instant now = Instant.parse("2026-07-19T09:00:00Z");

    private GroupFanOutService.Member member(long id) {
        return new GroupFanOutService.Member(id, "op-" + id, "acme");
    }

    private static OperatorGroupGrantJpaEntity roleGrant(long roleId) {
        return OperatorGroupGrantJpaEntity.role("g-role", GROUP, roleId, null, Instant.EPOCH);
    }

    private static OperatorGroupGrantJpaEntity tenantGrant(String tenantId) {
        return OperatorGroupGrantJpaEntity.tenantAssignment("g-ta", GROUP, tenantId, null, Instant.EPOCH);
    }

    @Test
    @DisplayName("grant-to-group ROLE: writes this group's own source row for each member without one; skips its own existing row")
    void fanOutRole_materialisesTaggedRows_idempotentSkip() {
        // member 1 already holds THIS group's row (operator=1, role=7, tenant=acme, GROUP:42) → skip; member 2 does
        // not → create. TASK-BE-633 (V0053): the key is 4 columns and names the same tenant AND source the row is
        // written with.
        when(operatorRoles.existsById(new AdminOperatorRoleJpaEntity.PK(1L, 7L, "acme", "GROUP:42"))).thenReturn(true);
        when(operatorRoles.existsById(new AdminOperatorRoleJpaEntity.PK(2L, 7L, "acme", "GROUP:42"))).thenReturn(false);
        when(operatorRoles.existsByOperatorIdAndRoleIdAndTenantId(2L, 7L, "acme")).thenReturn(false);

        int created = service.fanOutGrantToMembers(
                GROUP, roleGrant(7L), List.of(member(1L), member(2L)), 99L, now);

        assertThat(created).isEqualTo(1);
        ArgumentCaptor<AdminOperatorRoleJpaEntity> saved = ArgumentCaptor.forClass(AdminOperatorRoleJpaEntity.class);
        verify(operatorRoles, times(1)).save(saved.capture());
        assertThat(saved.getValue().getOperatorId()).isEqualTo(2L);
        assertThat(saved.getValue().getRoleId()).isEqualTo(7L);
        assertThat(saved.getValue().getGroupOrigin()).isEqualTo(GROUP);
        assertThat(saved.getValue().getSourceKey()).isEqualTo("GROUP:42");
        assertThat(saved.getValue().getTenantId()).isEqualTo("acme"); // the group's tenant
    }

    @Test
    @DisplayName("🔴 TASK-BE-633: a member holding the role from ANOTHER source still gets this group's row — not counted as new")
    void fanOutRole_otherSourceHeld_writesOwnRow_notCounted() {
        // member 3 holds (3, 7, acme) directly or from another group: v1 skipped here and G's revoke later took the
        // other source's role away. Now G's own row is written; the response count says «nothing new».
        when(operatorRoles.existsById(new AdminOperatorRoleJpaEntity.PK(3L, 7L, "acme", "GROUP:42"))).thenReturn(false);
        when(operatorRoles.existsByOperatorIdAndRoleIdAndTenantId(3L, 7L, "acme")).thenReturn(true);

        int created = service.fanOutGrantToMembers(GROUP, roleGrant(7L), List.of(member(3L)), 99L, now);

        assertThat(created).isZero();
        ArgumentCaptor<AdminOperatorRoleJpaEntity> saved = ArgumentCaptor.forClass(AdminOperatorRoleJpaEntity.class);
        verify(operatorRoles).save(saved.capture());
        assertThat(saved.getValue().getSourceKey()).isEqualTo("GROUP:42");
        assertThat(saved.getValue().getGroupOrigin()).isEqualTo(GROUP);
    }

    @Test
    @DisplayName("grant-to-group TENANT_ASSIGNMENT: materialises a group_origin-tagged assignment; skips existing direct")
    void fanOutTenant_materialisesTaggedRows_idempotentSkip() {
        when(assignments.existsById(new OperatorTenantAssignmentJpaEntity.PK(1L, "globex"))).thenReturn(true);
        when(assignments.existsById(new OperatorTenantAssignmentJpaEntity.PK(2L, "globex"))).thenReturn(false);

        int created = service.fanOutGrantToMembers(
                GROUP, tenantGrant("globex"), List.of(member(1L), member(2L)), 99L, now);

        assertThat(created).isEqualTo(1);
        ArgumentCaptor<OperatorTenantAssignmentJpaEntity> saved =
                ArgumentCaptor.forClass(OperatorTenantAssignmentJpaEntity.class);
        verify(assignments, times(1)).save(saved.capture());
        assertThat(saved.getValue().getOperatorId()).isEqualTo(2L);
        assertThat(saved.getValue().getTenantId()).isEqualTo("globex");
        assertThat(saved.getValue().getGroupOrigin()).isEqualTo(GROUP);
    }

    @Test
    @DisplayName("add-member: fans out all of the group's current grants onto the new member")
    void fanOutGrantsToMember_materialisesAll() {
        when(operatorRoles.existsById(any())).thenReturn(false);
        when(assignments.existsById(any())).thenReturn(false);

        int created = service.fanOutGrantsToMember(
                GROUP, List.of(roleGrant(7L), tenantGrant("globex")), member(2L), 99L, now);

        assertThat(created).isEqualTo(2);
        verify(operatorRoles, times(1)).save(any());
        verify(assignments, times(1)).save(any());
    }

    @Test
    @DisplayName("remove-member: revokes ONLY this member's group_origin rows (both substrates), spares direct grants")
    void revokeMemberFanOut_strictFilter() {
        when(assignments.findByGroupOrigin(GROUP)).thenReturn(List.of());

        GroupFanOutService.RevokeOutcome outcome = service.revokeMemberFanOut(GROUP, member(5L));

        assertThat(outcome).isEqualTo(GroupFanOutService.RevokeOutcome.NONE);
        assertThat(outcome.auditDetail()).as("nothing appended to the usual audit detail").isNull();
        verify(operatorRoles).deleteByGroupOriginAndOperatorId(GROUP, 5L);
        verify(assignments).deleteByGroupOriginAndOperatorId(GROUP, 5L);
        // never a broad delete that could hit a direct grant.
        verify(operatorRoles, never()).deleteByGroupOrigin(any());
        verify(assignments, never()).deleteByGroupOrigin(any());
    }

    @Test
    @DisplayName("delete-group: revokes ALL of the group's group_origin rows (both substrates)")
    void revokeGroupFanOut_deletesAllTagged() {
        when(assignments.findByGroupOrigin(GROUP)).thenReturn(List.of());

        service.revokeGroupFanOut(GROUP, List.of(member(1L), member(2L)));

        verify(operatorRoles).deleteByGroupOrigin(GROUP);
        verify(assignments).deleteByGroupOrigin(GROUP);
    }

    @Test
    @DisplayName("revoke ROLE grant: deletes only the ROLE group_origin rows, not the assignment substrate")
    void revokeGrantFanOut_role() {
        service.revokeGrantFanOut(GROUP, roleGrant(7L), List.of(member(1L)));

        verify(operatorRoles).deleteByGroupOriginAndRoleId(GROUP, 7L);
        verify(assignments, never()).deleteByGroupOriginAndTenantId(any(), any());
        verify(assignments, never()).findByGroupOrigin(any());
    }

    @Test
    @DisplayName("revoke TENANT_ASSIGNMENT grant: deletes only the assignment group_origin rows, not the role substrate")
    void revokeGrantFanOut_tenant() {
        when(assignments.findByGroupOrigin(GROUP)).thenReturn(List.of());

        service.revokeGrantFanOut(GROUP, tenantGrant("globex"), List.of(member(1L)));

        verify(assignments).deleteByGroupOriginAndTenantId(GROUP, "globex");
        verify(operatorRoles, never()).deleteByGroupOriginAndRoleId(any(), any());
    }

    @Test
    @DisplayName("🔴 TASK-BE-633 AC-3 (784 D-1): a fan-out membership backing a role row is kept as DIRECT; one that backs none is deleted")
    void revokeGrantFanOut_tenant_keepsMembershipBackingRoles() {
        OperatorTenantAssignmentJpaEntity backsRole = OperatorTenantAssignmentJpaEntity.createGroupScoped(
                1L, "globex", Instant.EPOCH, null, GROUP);
        OperatorTenantAssignmentJpaEntity backsNothing = OperatorTenantAssignmentJpaEntity.createGroupScoped(
                2L, "globex", Instant.EPOCH, null, GROUP);
        OperatorTenantAssignmentJpaEntity otherTenant = OperatorTenantAssignmentJpaEntity.createGroupScoped(
                1L, "initech", Instant.EPOCH, null, GROUP);
        when(assignments.findByGroupOrigin(GROUP)).thenReturn(List.of(backsRole, backsNothing, otherTenant));
        when(operatorRoles.existsByOperatorIdAndTenantId(1L, "globex")).thenReturn(true);
        when(operatorRoles.existsByOperatorIdAndTenantId(2L, "globex")).thenReturn(false);

        int kept = service.revokeGrantFanOut(GROUP, tenantGrant("globex"), List.of(member(1L), member(2L)))
                .keptAsDirect();

        assertThat(kept).isEqualTo(1);
        assertThat(backsRole.getGroupOrigin()).as("kept, now a direct membership").isNull();
        assertThat(backsNothing.getGroupOrigin()).as("left for the delete").isEqualTo(GROUP);
        assertThat(otherTenant.getGroupOrigin()).as("not this grant's tenant — untouched").isEqualTo(GROUP);
        verify(assignments).saveAndFlush(backsRole);
        verify(assignments, never()).saveAndFlush(backsNothing);
        verify(assignments).deleteByGroupOriginAndTenantId(GROUP, "globex");
    }

    @Test
    @DisplayName("🔴 TASK-BE-633 AC-3: remove-member deletes the member's role rows of this group FIRST, then keeps only memberships still backing a role")
    void revokeMemberFanOut_keepsMembershipBackingRoles_afterOwnRoleRowsGo() {
        OperatorTenantAssignmentJpaEntity mine = OperatorTenantAssignmentJpaEntity.createGroupScoped(
                5L, "globex", Instant.EPOCH, null, GROUP);
        OperatorTenantAssignmentJpaEntity someoneElse = OperatorTenantAssignmentJpaEntity.createGroupScoped(
                6L, "globex", Instant.EPOCH, null, GROUP);
        when(assignments.findByGroupOrigin(GROUP)).thenReturn(List.of(mine, someoneElse));
        when(operatorRoles.existsByOperatorIdAndTenantId(5L, "globex")).thenReturn(true);

        int kept = service.revokeMemberFanOut(GROUP, member(5L)).keptAsDirect();

        assertThat(kept).isEqualTo(1);
        assertThat(mine.getGroupOrigin()).isNull();
        assertThat(someoneElse.getGroupOrigin()).as("another member's row is not this call's").isEqualTo(GROUP);
        var order = org.mockito.Mockito.inOrder(operatorRoles, assignments);
        order.verify(operatorRoles).deleteByGroupOriginAndOperatorId(GROUP, 5L);
        order.verify(operatorRoles).existsByOperatorIdAndTenantId(5L, "globex");
        order.verify(assignments).deleteByGroupOriginAndOperatorId(GROUP, 5L);
    }

    // ── TASK-BE-636 ① — re-ownership · ② — set rows back a membership ──────────

    @Test
    @DisplayName("🔴 BE-636 ①: another group the member stays in gives the tenant → the membership is RE-OWNED (only group_origin changes), not kept-as-direct, not deleted")
    void revokeGrantFanOut_tenant_reownedToAnotherGroup() {
        OperatorTenantAssignmentJpaEntity row = OperatorTenantAssignmentJpaEntity.create(
                1L, "globex", Instant.EPOCH, 99L, null, List.of("dept-ops"));
        row.setGroupOrigin(GROUP);
        when(assignments.findByGroupOrigin(GROUP)).thenReturn(List.of(row));
        when(assignments.findLowestOtherGroupGivingTenant(1L, "globex", List.of(GROUP)))
                .thenReturn(java.util.Optional.of(7L));

        GroupFanOutService.RevokeOutcome outcome =
                service.revokeGrantFanOut(GROUP, tenantGrant("globex"), List.of(member(1L)));

        assertThat(outcome).isEqualTo(new GroupFanOutService.RevokeOutcome(1, 0));
        assertThat(outcome.auditDetail()).isEqualTo("reownedToGroup=1");
        assertThat(row.getGroupOrigin()).as("handed to G'").isEqualTo(7L);
        assertThat(row.getOrgScope()).as("the membership's own data scope is kept — never widened")
                .containsExactly("dept-ops");
        assertThat(row.getGrantedAt()).isEqualTo(Instant.EPOCH);
        assertThat(row.getGrantedBy()).isEqualTo(99L);
        // re-ownership comes first: the backing checks are not even asked
        verify(operatorRoles, never()).existsByOperatorIdAndTenantId(any(), any());
        verify(domainSetGrants, never()).existsByOperatorIdAndTenantIdAndSourceKeyNot(any(), any(), any());
        var order = org.mockito.Mockito.inOrder(assignments);
        order.verify(assignments).saveAndFlush(row);
        order.verify(assignments).deleteByGroupOriginAndTenantId(GROUP, "globex");
    }

    @Test
    @DisplayName("🔴 BE-636 ②: no group takes over, no role row, but a domain set row in the tenant → kept as DIRECT")
    void revokeGrantFanOut_tenant_setRowOnly_keptAsDirect() {
        OperatorTenantAssignmentJpaEntity row = OperatorTenantAssignmentJpaEntity.createGroupScoped(
                1L, "globex", Instant.EPOCH, null, GROUP);
        when(assignments.findByGroupOrigin(GROUP)).thenReturn(List.of(row));
        when(operatorRoles.existsByOperatorIdAndTenantId(1L, "globex")).thenReturn(false);
        // TENANT_ASSIGNMENT revoke: the group's own set rows stay, so every source counts («-» excludes none)
        when(domainSetGrants.existsByOperatorIdAndTenantIdAndSourceKeyNot(1L, "globex", "-")).thenReturn(true);

        GroupFanOutService.RevokeOutcome outcome =
                service.revokeGrantFanOut(GROUP, tenantGrant("globex"), List.of(member(1L)));

        assertThat(outcome).isEqualTo(new GroupFanOutService.RevokeOutcome(0, 1));
        assertThat(outcome.auditDetail()).isEqualTo("keptAsDirect=1");
        assertThat(row.getGroupOrigin()).isNull();
    }

    @Test
    @DisplayName("BE-636 ②: member removal / group deletion do not let the group's OWN GROUP:<id> set rows keep its membership")
    void revokeMemberAndGroup_ownGroupSetRowsDoNotBack() {
        OperatorTenantAssignmentJpaEntity row = OperatorTenantAssignmentJpaEntity.createGroupScoped(
                5L, "globex", Instant.EPOCH, null, GROUP);
        when(assignments.findByGroupOrigin(GROUP)).thenReturn(List.of(row));

        service.revokeMemberFanOut(GROUP, member(5L));
        service.revokeGroupFanOut(GROUP, List.of(member(5L)));

        verify(domainSetGrants, times(2)).existsByOperatorIdAndTenantIdAndSourceKeyNot(5L, "globex", "GROUP:42");
        assertThat(row.getGroupOrigin()).as("nothing backs it — left for the delete").isEqualTo(GROUP);
    }

    @Test
    @DisplayName("BE-636 audit detail: both counts, re-owned first; null when neither")
    void revokeOutcome_auditDetail() {
        assertThat(new GroupFanOutService.RevokeOutcome(2, 1).auditDetail()).isEqualTo("reownedToGroup=2,keptAsDirect=1");
        assertThat(GroupFanOutService.RevokeOutcome.NONE.auditDetail()).isNull();
    }
}
