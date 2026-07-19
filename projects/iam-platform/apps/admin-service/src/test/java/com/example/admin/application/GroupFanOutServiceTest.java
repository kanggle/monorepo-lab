package com.example.admin.application;

import com.example.admin.infrastructure.persistence.rbac.AdminOperatorRoleJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorRoleJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.CachingPermissionEvaluator;
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
    @DisplayName("grant-to-group ROLE: materialises a group_origin-tagged row for each member without one; skips existing")
    void fanOutRole_materialisesTaggedRows_idempotentSkip() {
        // member 1 already holds (operator=1, role=7) → skip; member 2 does not → create.
        when(operatorRoles.existsById(new AdminOperatorRoleJpaEntity.PK(1L, 7L))).thenReturn(true);
        when(operatorRoles.existsById(new AdminOperatorRoleJpaEntity.PK(2L, 7L))).thenReturn(false);

        int created = service.fanOutGrantToMembers(
                GROUP, roleGrant(7L), List.of(member(1L), member(2L)), 99L, now);

        assertThat(created).isEqualTo(1);
        ArgumentCaptor<AdminOperatorRoleJpaEntity> saved = ArgumentCaptor.forClass(AdminOperatorRoleJpaEntity.class);
        verify(operatorRoles, times(1)).save(saved.capture());
        assertThat(saved.getValue().getOperatorId()).isEqualTo(2L);
        assertThat(saved.getValue().getRoleId()).isEqualTo(7L);
        assertThat(saved.getValue().getGroupOrigin()).isEqualTo(GROUP);
        assertThat(saved.getValue().getTenantId()).isEqualTo("acme"); // member's own tenant
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
        service.revokeMemberFanOut(GROUP, member(5L));

        verify(operatorRoles).deleteByGroupOriginAndOperatorId(GROUP, 5L);
        verify(assignments).deleteByGroupOriginAndOperatorId(GROUP, 5L);
        // never a broad delete that could hit a direct grant.
        verify(operatorRoles, never()).deleteByGroupOrigin(any());
        verify(assignments, never()).deleteByGroupOrigin(any());
    }

    @Test
    @DisplayName("delete-group: revokes ALL of the group's group_origin rows (both substrates)")
    void revokeGroupFanOut_deletesAllTagged() {
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
    }

    @Test
    @DisplayName("revoke TENANT_ASSIGNMENT grant: deletes only the assignment group_origin rows, not the role substrate")
    void revokeGrantFanOut_tenant() {
        service.revokeGrantFanOut(GROUP, tenantGrant("globex"), List.of(member(1L)));

        verify(assignments).deleteByGroupOriginAndTenantId(GROUP, "globex");
        verify(operatorRoles, never()).deleteByGroupOriginAndRoleId(any(), any());
    }
}
