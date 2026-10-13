package com.example.admin.application;

import com.example.admin.application.exception.AssignmentNotFoundException;
import com.example.admin.application.exception.TenantLastAdminRequiredException;
import com.example.admin.application.port.DomainPermissionSetPort;
import com.example.admin.application.port.OperatorTenantAssignmentPort;
import com.example.admin.application.port.TenantPartnershipPort;
import com.example.admin.infrastructure.persistence.rbac.CachingPermissionEvaluator;
import com.example.admin.infrastructure.persistence.rbac.OperatorGroupMemberJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.OperatorGroupMemberJpaRepository;
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

import static com.example.admin.application.OperatorUseCaseTestSupport.actor;
import static com.example.admin.application.OperatorUseCaseTestSupport.operator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-784 S4 — «이 회사에서 내보내기» ({@link ManageOperatorAssignmentUseCase#unassignOperator} +
 * {@link OperatorCompanyExitCascade} + {@link TenantLastAdminGuard}), unit half of AC-2.
 *
 * <p>Role rows live in {@link InMemoryRoleRowsPort} (it really deletes), so «the other company's rows are
 * byte-unchanged» and «nothing was written» are facts about data. The IT half (assume · admin API · tokens) is
 * {@code integration.OperatorMembershipWriteAxisIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class OperatorCompanyExitUseCaseTest {

    @Mock OperatorTenantAssignmentPort assignmentPort;
    @Mock TenantScopeGuard tenantScopeGuard;
    @Mock AdminActionAuditor auditor;
    @Mock CachingPermissionEvaluator cachingPermissionEvaluator;
    @Mock OperatorGroupMemberJpaRepository groupMembers;
    @Mock TenantPartnershipPort partnershipPort;
    @Mock DomainPermissionSetPort domainSetPort;

    InMemoryRoleRowsPort port;
    ManageOperatorAssignmentUseCase useCase;

    static final long PERSON = 77L;
    static final long ADMIN_A = 88L;

    @BeforeEach
    void init() {
        port = new InMemoryRoleRowsPort()
                .role(2L, "SUPPORT_READONLY").role(4L, "SECURITY_ANALYST").role(5L, "TENANT_ADMIN")
                .role(6L, "ORG_ADMIN")
                .operator(operator(PERSON, "person-uuid", "p@ex.com", "ACTIVE", "alpha"))
                .operator(operator(ADMIN_A, "admin-a-uuid", "a@ex.com", "ACTIVE", "alpha"));
        TenantLastAdminGuard lastAdminGuard = new TenantLastAdminGuard(port);
        OperatorCompanyExitCascade cascade = new OperatorCompanyExitCascade(
                port, groupMembers, assignmentPort, partnershipPort, lastAdminGuard, domainSetPort);
        useCase = new ManageOperatorAssignmentUseCase(port, assignmentPort, tenantScopeGuard, auditor,
                lastAdminGuard, cascade, cachingPermissionEvaluator);
    }

    @Test
    @DisplayName("🔴 AC-2 (D3 · D-9): leaving alpha takes alpha's role rows of every origin, alpha group memberships, partner=alpha participations and the membership — beta byte-unchanged")
    void exitAlpha_takesEverySideOfAlpha_leavesBeta() {
        port.row(PERSON, "SUPPORT_READONLY", "alpha", null, null)
                .row(PERSON, "SECURITY_ANALYST", "alpha", 9L, null)
                .row(PERSON, "ORG_ADMIN", "alpha", null, "node-1")
                .row(PERSON, "SUPPORT_READONLY", "beta", null, null)
                .row(PERSON, "TENANT_ADMIN", "beta", null, null)
                .row(ADMIN_A, "TENANT_ADMIN", "alpha", null, null);
        List<InMemoryRoleRowsPort.Row> betaBefore = List.copyOf(port.rowsOutside(PERSON, "alpha"));
        when(assignmentPort.assignmentExists(PERSON, "alpha")).thenReturn(true);
        OperatorGroupMemberJpaEntity membership = OperatorGroupMemberJpaEntity.create(9L, PERSON, Instant.EPOCH, null);
        when(groupMembers.findByOperatorIdAndGroupTenantId(PERSON, "alpha")).thenReturn(List.of(membership));
        when(partnershipPort.removeParticipationsWithPartner(PERSON, "alpha")).thenReturn(1);
        // TASK-BE-631 (ADR-083 D4): alpha's set rows of every source go (here: a MIGRATION and a GROUP:9 row)
        when(domainSetPort.deleteAllGrantsInTenant(PERSON, "alpha")).thenReturn(2);

        useCase.unassignOperator("person-uuid", "alpha", actor(), "left the company");

        assertThat(port.rowsIn(PERSON, "alpha")).as("every alpha row, any origin").isEmpty();
        assertThat(port.rowsOutside(PERSON, "alpha")).as("beta rows byte-unchanged").isEqualTo(betaBefore);
        assertThat(port.rowsIn(ADMIN_A, "alpha")).as("another person's rows untouched").hasSize(1);

        verify(assignmentPort).deleteGroupOriginAssignments(PERSON, 9L);
        verify(groupMembers).delete(membership);
        verify(partnershipPort).removeParticipationsWithPartner(PERSON, "alpha");
        verify(assignmentPort).deleteAssignment(PERSON, "alpha");
        verify(assignmentPort, never()).deleteAssignment(PERSON, "beta");
        verify(domainSetPort).deleteAllGrantsInTenant(PERSON, "alpha");
        verify(domainSetPort, never()).deleteAllGrantsInTenant(PERSON, "beta");
        verify(cachingPermissionEvaluator).invalidate("person-uuid");

        ArgumentCaptor<AdminActionAuditor.AuditRecord> rec = ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        verify(auditor).recordWithPermission(rec.capture(), anyString());
        assertThat(rec.getValue().actionCode()).isEqualTo(ActionCode.OPERATOR_ASSIGNMENT_DELETE);
        assertThat(rec.getValue().targetTenantId()).isEqualTo("alpha");
        // 1 group-9 row (by origin) + the 2 remaining alpha rows (direct · node)
        assertThat(rec.getValue().downstreamDetail())
                .isEqualTo("roleRows=3,groupMemberships=1,participations=1,groupAssignments=0,keptAsDirect=0"
                        + ",domainSetGrants=2");
        // the operator row is not written (status · home · identity — D-10, R1): the fake throws on any of them
    }

    @Test
    @DisplayName("🔴 owner decision 2026-10-13 «U 역할 행도 함께 지움»: leaving alpha's group 9 removes what group 9 produced in uu too; the person's own uu / vv rows byte-unchanged")
    void exitAlpha_removesTheGroupsFanOutInOtherCompanies() {
        port.row(PERSON, "SUPPORT_READONLY", "alpha", null, null)
                .row(PERSON, "SECURITY_ANALYST", "uu", 9L, null)     // group 9 (alpha) fanned out into uu
                .row(PERSON, "SUPPORT_READONLY", "uu", null, null)   // the person's own uu role — stays
                .row(PERSON, "SUPPORT_READONLY", "vv", null, null)   // another company, direct — stays
                .row(PERSON, "SECURITY_ANALYST", "vv", 12L, null)    // a vv group's row — stays
                .row(ADMIN_A, "TENANT_ADMIN", "alpha", null, null);
        List<InMemoryRoleRowsPort.Row> ownUu = port.rowsIn(PERSON, "uu").stream()
                .filter(r -> r.groupOrigin() == null).toList();
        List<InMemoryRoleRowsPort.Row> vvBefore = List.copyOf(port.rowsIn(PERSON, "vv"));
        when(assignmentPort.assignmentExists(PERSON, "alpha")).thenReturn(true);
        OperatorGroupMemberJpaEntity membership = OperatorGroupMemberJpaEntity.create(9L, PERSON, Instant.EPOCH, null);
        when(groupMembers.findByOperatorIdAndGroupTenantId(PERSON, "alpha")).thenReturn(List.of(membership));
        when(assignmentPort.deleteGroupOriginAssignments(PERSON, 9L)).thenReturn(1); // its uu assignment

        useCase.unassignOperator("person-uuid", "alpha", actor(), "left the company");

        assertThat(port.rowsIn(PERSON, "uu")).as("group 9's uu row gone, the person's own uu row byte-unchanged")
                .isEqualTo(ownUu);
        assertThat(port.rowsIn(PERSON, "vv")).as("vv byte-unchanged (incl. a vv group's row)").isEqualTo(vvBefore);
        verify(assignmentPort).deleteGroupOriginAssignments(PERSON, 9L);
        verify(assignmentPort, never()).deleteAssignment(eq(PERSON), eq("uu"));
        verify(assignmentPort, never()).deleteAssignment(eq(PERSON), eq("vv"));
    }

    @Test
    @DisplayName("🔴 owner decision 2026-10-13: group 9's fan-out held the sole TENANT_ADMIN@uu → 409, nothing written / control: another uu admin → OK")
    void exitAlpha_groupFanOutWasLastAdminOfUu_refused() {
        port.row(PERSON, "SUPPORT_READONLY", "alpha", null, null)
                .row(PERSON, "TENANT_ADMIN", "uu", 9L, null)
                .row(ADMIN_A, "TENANT_ADMIN", "alpha", null, null);
        when(assignmentPort.assignmentExists(PERSON, "alpha")).thenReturn(true);
        OperatorGroupMemberJpaEntity membership = OperatorGroupMemberJpaEntity.create(9L, PERSON, Instant.EPOCH, null);
        when(groupMembers.findByOperatorIdAndGroupTenantId(PERSON, "alpha")).thenReturn(List.of(membership));

        assertThatThrownBy(() -> useCase.unassignOperator("person-uuid", "alpha", actor(), "r"))
                .isInstanceOf(TenantLastAdminRequiredException.class);
        assertThat(port.rows).hasSize(3);
        verify(assignmentPort, never()).deleteGroupOriginAssignments(anyLong(), anyLong());
        verify(groupMembers, never()).delete(any());
        verify(assignmentPort, never()).deleteAssignment(anyLong(), anyString());
        verify(domainSetPort, never()).deleteAllGrantsInTenant(anyLong(), anyString());

        // control: uu has another ACTIVE administrator
        port.row(ADMIN_A, "TENANT_ADMIN", "uu", null, null);
        useCase.unassignOperator("person-uuid", "alpha", actor(), "r");
        assertThat(port.rowsIn(PERSON, "uu")).isEmpty();
    }

    @Test
    @DisplayName("🔴 last admin (owner decision 2026-10-11 UTC): the sole ACTIVE TENANT_ADMIN@alpha removing themself → 409, nothing written")
    void exitSoleAdmin_refused_nothingWritten() {
        port.row(ADMIN_A, "TENANT_ADMIN", "alpha", null, null);
        when(assignmentPort.assignmentExists(ADMIN_A, "alpha")).thenReturn(true);

        assertThatThrownBy(() -> useCase.unassignOperator("admin-a-uuid", "alpha",
                new OperatorContext("admin-a-uuid", "jti"), "leaving"))
                .isInstanceOf(TenantLastAdminRequiredException.class);

        assertThat(port.rowsIn(ADMIN_A, "alpha")).hasSize(1);
        verifyNoInteractions(groupMembers, partnershipPort, cachingPermissionEvaluator, domainSetPort);
        verify(assignmentPort, never()).deleteAssignment(anyLong(), anyString());
        verify(auditor, never()).recordWithPermission(any(), anyString());
    }

    @Test
    @DisplayName("control: a TENANT_ADMIN group row of the target counts as administrator too (any origin) — and another ACTIVE admin lets it go")
    void exitAdmin_anyOrigin_allowedWhenAnotherAdminRemains() {
        port.row(PERSON, "TENANT_ADMIN", "alpha", 9L, null);
        when(assignmentPort.assignmentExists(PERSON, "alpha")).thenReturn(true);
        assertThatThrownBy(() -> useCase.unassignOperator("person-uuid", "alpha", actor(), "r"))
                .isInstanceOf(TenantLastAdminRequiredException.class);

        port.row(ADMIN_A, "TENANT_ADMIN", "alpha", null, "node-1");
        when(groupMembers.findByOperatorIdAndGroupTenantId(PERSON, "alpha")).thenReturn(List.of());
        useCase.unassignOperator("person-uuid", "alpha", actor(), "r");
        assertThat(port.rowsIn(PERSON, "alpha")).isEmpty();
    }

    @Test
    @DisplayName("no membership in alpha → 404 ASSIGNMENT_NOT_FOUND, nothing written")
    void exitWithoutMembership_notFound() {
        when(assignmentPort.assignmentExists(PERSON, "alpha")).thenReturn(false);
        assertThatThrownBy(() -> useCase.unassignOperator("person-uuid", "alpha", actor(), "r"))
                .isInstanceOf(AssignmentNotFoundException.class);
        verifyNoInteractions(groupMembers, partnershipPort);
    }

    @Test
    @DisplayName("D2 (D-14): assigning a '*' operator to a company → 400; a company operator's assignment invalidates its cache")
    void assign_platformRefused_companyInvalidates() {
        port.operator(operator(55L, "platform-uuid", "x@ex.com", "ACTIVE", "*"));
        assertThatThrownBy(() -> useCase.assignOperator("platform-uuid", "alpha", actor(), "r"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(assignmentPort, never()).createAssignment(anyLong(), anyString(), any());

        when(assignmentPort.assignmentExists(PERSON, "beta")).thenReturn(false);
        useCase.assignOperator("person-uuid", "beta", actor(), "r");
        verify(assignmentPort).createAssignment(eq(PERSON), eq("beta"), any());
        verify(cachingPermissionEvaluator).invalidate("person-uuid");
    }
}
