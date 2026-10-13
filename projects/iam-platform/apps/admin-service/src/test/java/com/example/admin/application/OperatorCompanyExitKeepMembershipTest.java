package com.example.admin.application;

import com.example.admin.application.port.TenantPartnershipPort;
import com.example.admin.infrastructure.persistence.rbac.CachingPermissionEvaluator;
import com.example.admin.infrastructure.persistence.rbac.OperatorGroupMemberJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.OperatorGroupMemberJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static com.example.admin.application.OperatorUseCaseTestSupport.actor;
import static com.example.admin.application.OperatorUseCaseTestSupport.operator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-784 S4 — owner decision 2026-10-13 UTC «U 소속을 직접 소속으로 남김»: when «이 회사에서 내보내기» removes
 * a leaving group's fan-out assignment into another company U, the assignment is KEPT as a direct membership if the
 * person still holds a role row in U that the leaving group did not produce. Role rows and assignment rows both live
 * in in-memory fakes (they really delete / convert), so «kept, now direct, unchanged otherwise» is a fact about data.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class OperatorCompanyExitKeepMembershipTest {

    @Mock TenantScopeGuard tenantScopeGuard;
    @Mock AdminActionAuditor auditor;
    @Mock CachingPermissionEvaluator cachingPermissionEvaluator;
    @Mock OperatorGroupMemberJpaRepository groupMembers;
    @Mock TenantPartnershipPort partnershipPort;

    InMemoryRoleRowsPort roles;
    InMemoryAssignmentPort assignments;
    ManageOperatorAssignmentUseCase useCase;

    static final long PERSON = 77L;
    static final long ADMIN_A = 88L;
    static final long G = 9L; // a group of alpha that also fans out into uu

    @BeforeEach
    void init() {
        roles = new InMemoryRoleRowsPort()
                .role(2L, "SUPPORT_READONLY").role(4L, "SECURITY_ANALYST").role(5L, "TENANT_ADMIN")
                .operator(operator(PERSON, "person-uuid", "p@ex.com", "ACTIVE", "alpha"))
                .operator(operator(ADMIN_A, "admin-a-uuid", "a@ex.com", "ACTIVE", "alpha"))
                .row(ADMIN_A, "TENANT_ADMIN", "alpha", null, null)
                .row(PERSON, "SUPPORT_READONLY", "alpha", null, null)
                .row(PERSON, "SECURITY_ANALYST", "uu", G, null);   // G's own uu role — always goes (decision 1)
        assignments = new InMemoryAssignmentPort()
                .row(PERSON, "alpha", null, null)
                .row(PERSON, "uu", G, List.of("dept-ops"));       // the person's ONLY uu membership — from G
        TenantLastAdminGuard lastAdminGuard = new TenantLastAdminGuard(roles);
        OperatorCompanyExitCascade cascade = new OperatorCompanyExitCascade(
                roles, groupMembers, assignments, partnershipPort, lastAdminGuard);
        useCase = new ManageOperatorAssignmentUseCase(roles, assignments, tenantScopeGuard, auditor,
                lastAdminGuard, cascade, cachingPermissionEvaluator);
        when(groupMembers.findByOperatorIdAndGroupTenantId(PERSON, "alpha"))
                .thenReturn(List.of(OperatorGroupMemberJpaEntity.create(G, PERSON, Instant.EPOCH, null)));
    }

    @Test
    @DisplayName("🔴 (a) a direct uu role remains → G's uu assignment KEPT as a direct membership (granted_at · org_scope unchanged); direct uu role byte-unchanged; G's uu role gone")
    void remainingRoleInUu_keepsTheMembershipAsDirect() {
        roles.row(PERSON, "SUPPORT_READONLY", "uu", null, null);   // a deliberate, direct uu grant
        List<InMemoryRoleRowsPort.Row> directUu = roles.rowsIn(PERSON, "uu").stream()
                .filter(r -> r.groupOrigin() == null).toList();
        InMemoryAssignmentPort.Row before = assignments.find(PERSON, "uu").orElseThrow();

        useCase.unassignOperator("person-uuid", "alpha", actor(), "left alpha");

        InMemoryAssignmentPort.Row after = assignments.find(PERSON, "uu").orElseThrow();
        assertThat(after.groupOrigin()).as("now a direct membership").isNull();
        assertThat(after.grantedAt()).isEqualTo(before.grantedAt());
        assertThat(after.grantedBy()).as("granted_by kept (not re-stamped with the export's actor)")
                .isEqualTo(before.grantedBy());
        assertThat(after.orgScope()).isEqualTo(before.orgScope());
        assertThat(roles.rowsIn(PERSON, "uu")).as("direct uu role byte-unchanged; G's uu role gone").isEqualTo(directUu);
        assertThat(assignments.find(PERSON, "alpha")).as("alpha membership removed").isEmpty();
    }

    @Test
    @DisplayName("(b) control: no uu role left besides G's → G's uu assignment deleted")
    void noRemainingRoleInUu_deletesTheMembership() {
        useCase.unassignOperator("person-uuid", "alpha", actor(), "left alpha");

        assertThat(assignments.find(PERSON, "uu")).isEmpty();
        assertThat(roles.rowsIn(PERSON, "uu")).isEmpty();
    }

    @Test
    @DisplayName("a uu role from ANOTHER group (not leaving) also keeps the membership")
    void otherGroupsUuRole_alsoKeeps() {
        roles.row(PERSON, "SUPPORT_READONLY", "uu", 12L, null);

        useCase.unassignOperator("person-uuid", "alpha", actor(), "left alpha");

        assertThat(assignments.find(PERSON, "uu")).get().extracting(InMemoryAssignmentPort.Row::groupOrigin).isNull();
    }
}
