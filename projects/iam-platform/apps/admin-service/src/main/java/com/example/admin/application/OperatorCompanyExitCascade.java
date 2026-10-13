package com.example.admin.application;

import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.OperatorTenantAssignmentPort;
import com.example.admin.application.port.TenantPartnershipPort;
import com.example.admin.infrastructure.persistence.rbac.OperatorGroupMemberJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.OperatorGroupMemberJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * TASK-MONO-784 S4 (ADR-MONO-082 b · D3 · decision D-9) — what «이 회사에서 내보내기» takes away besides the
 * membership row itself: every side of the person that lives in company T (admin-api.md § DELETE
 * …/assignments/{tenantId}).
 *
 * <ol>
 *   <li>memberships of T's groups, and <b>everything those groups fanned out to the person in EVERY tenant</b> —
 *       their {@code group_origin} assignment rows and role rows, in another company U as well (owner decision
 *       2026-10-13 UTC «U 역할 행도 함께 지움»: no «role without membership» state, D-1). The person's own rows in U
 *       (direct, or from a U group) are untouched;</li>
 *   <li>participant bindings in partnerships whose partner is T (784 AC-0 F18);</li>
 *   <li>every role row living in T, whatever its origin (direct · group · org-node row recorded at T).</li>
 * </ol>
 *
 * <p>Before anything is written, every OTHER company whose {@code TENANT_ADMIN} the leaving groups' fan-out held
 * must keep an administrator ({@link TenantLastAdminGuard}, same 409). The caller checks T itself.
 *
 * <p>Nothing else of another company is touched, nor the operator row (status · home · identity — D-10, R1), nor
 * the person's account (ADR-080 D5 · ADR-082 D3). Joins the caller's transaction; the caller deletes the
 * membership row, audits and drops the permission cache.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class OperatorCompanyExitCascade {

    private final AdminOperatorPort operatorPort;
    private final OperatorGroupMemberJpaRepository groupMembers;
    private final OperatorTenantAssignmentPort assignmentPort;
    private final TenantPartnershipPort partnershipPort;
    private final TenantLastAdminGuard lastAdminGuard;

    /** How many rows of each kind left with the person (the audit {@code detail}). */
    public record ExitCounts(int roleRows, int groupMemberships, int participations, int groupAssignments) {
        public String detail() {
            return "roleRows=" + roleRows + ",groupMemberships=" + groupMemberships
                    + ",participations=" + participations + ",groupAssignments=" + groupAssignments;
        }
    }

    public ExitCounts exit(AdminOperatorPort.OperatorView operator, String tenantId) {
        long operatorId = operator.internalId();
        List<OperatorGroupMemberJpaEntity> leaving = groupMembers.findByOperatorIdAndGroupTenantId(operatorId, tenantId);
        Set<Long> leavingGroups = new TreeSet<>();
        for (OperatorGroupMemberJpaEntity m : leaving) {
            leavingGroups.add(m.getGroupId());
        }

        // Guard first, write after: another company U whose TENANT_ADMIN came to this person through a leaving
        // group must keep an administrator.
        if (!leavingGroups.isEmpty()) {
            Set<String> otherTenants = new TreeSet<>();
            for (AdminOperatorPort.RoleRowView row : operatorPort.findRoleRowsOfOperator(operatorId)) {
                if (row.groupOrigin() != null && leavingGroups.contains(row.groupOrigin())
                        && !tenantId.equals(row.tenantId())) {
                    otherTenants.add(row.tenantId());
                }
            }
            for (String other : otherTenants) {
                lastAdminGuard.requireAdminRemains(other,
                        row -> row.operatorInternalId() == operatorId
                                && row.groupOrigin() != null && leavingGroups.contains(row.groupOrigin()),
                        null);
            }
        }

        int roleRows = 0;
        int groupAssignments = 0;
        for (OperatorGroupMemberJpaEntity m : leaving) {
            roleRows += operatorPort.deleteOperatorRolesByGroupOrigin(operatorId, m.getGroupId());
            groupAssignments += assignmentPort.deleteGroupOriginAssignments(operatorId, m.getGroupId());
            groupMembers.delete(m);
        }
        int participations = partnershipPort.removeParticipationsWithPartner(operatorId, tenantId);
        roleRows += operatorPort.deleteOperatorRolesInTenant(operatorId, tenantId);
        return new ExitCounts(roleRows, leaving.size(), participations, groupAssignments);
    }
}
