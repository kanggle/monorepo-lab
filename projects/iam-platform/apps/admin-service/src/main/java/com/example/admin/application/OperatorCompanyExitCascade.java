package com.example.admin.application;

import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.DomainPermissionSetPort;
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
 *       (direct, or from a U group) are untouched. A leaving group's U membership is first handed to a group the
 *       person stays in that also gives U (TASK-BE-636 ①), else kept as direct when a role or set row still backs
 *       it (owner decision 2026-10-13 · TASK-BE-636 ②), else deleted;</li>
 *   <li>participant bindings in partnerships whose partner is T (784 AC-0 F18);</li>
 *   <li>every role row living in T, whatever its origin (direct · group · org-node row recorded at T);</li>
 *   <li>(TASK-BE-631 · ADR-MONO-083 D4) every domain permission set row of (person, T), every source.</li>
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
    private final DomainPermissionSetPort domainSetPort;

    /** How many rows of each kind left with the person (the audit {@code detail}). */
    public record ExitCounts(int roleRows, int groupMemberships, int participations, int groupAssignments,
                             int keptAsDirect, int domainSetGrants, int reownedToGroup) {
        /**
         * TASK-BE-636: {@code ,reownedToGroup=N} is appended only when a leaving group's membership into another
         * company was handed to a group the person stays in — the usual detail is byte-unchanged.
         */
        public String detail() {
            return "roleRows=" + roleRows + ",groupMemberships=" + groupMemberships
                    + ",participations=" + participations + ",groupAssignments=" + groupAssignments
                    + ",keptAsDirect=" + keptAsDirect + ",domainSetGrants=" + domainSetGrants
                    + (reownedToGroup > 0 ? ",reownedToGroup=" + reownedToGroup : "");
        }
    }

    public ExitCounts exit(AdminOperatorPort.OperatorView operator, String tenantId) {
        long operatorId = operator.internalId();
        List<OperatorGroupMemberJpaEntity> leaving = groupMembers.findByOperatorIdAndGroupTenantId(operatorId, tenantId);
        Set<Long> leavingGroups = new TreeSet<>();
        for (OperatorGroupMemberJpaEntity m : leaving) {
            leavingGroups.add(m.getGroupId());
        }

        // Owner decision 2026-10-13 UTC «U 소속을 직접 소속으로 남김»: the companies (other than T) where the person
        // keeps a work grant NOT produced by a leaving group — a role row (direct, another group's, or a node grant
        // there) or, since TASK-BE-636 ②, a domain permission set row of any source (a group gives sets only in its
        // own tenant, so a leaving group's set rows are all in T and never counted here). A leaving group's fan-out
        // assignment into such a company becomes a direct membership instead of being deleted: no «grant without
        // membership» (D-1), and no deliberate grant there is lost.
        List<AdminOperatorPort.RoleRowView> roleRowsBefore = leavingGroups.isEmpty()
                ? List.of() : operatorPort.findRoleRowsOfOperator(operatorId);
        Set<String> keepsGrantsIn = new TreeSet<>();
        for (AdminOperatorPort.RoleRowView row : roleRowsBefore) {
            boolean fromLeavingGroup = row.groupOrigin() != null && leavingGroups.contains(row.groupOrigin());
            if (!fromLeavingGroup && row.tenantId() != null && !tenantId.equals(row.tenantId())) {
                keepsGrantsIn.add(row.tenantId());
            }
        }
        if (!leavingGroups.isEmpty()) {
            for (String setTenant : domainSetPort.findTenantsWithGrants(operatorId)) {
                if (!tenantId.equals(setTenant)) {
                    keepsGrantsIn.add(setTenant);
                }
            }
        }

        // Guard first, write after: another company U whose TENANT_ADMIN came to this person through a leaving
        // group must keep an administrator.
        if (!leavingGroups.isEmpty()) {
            Set<String> otherTenants = new TreeSet<>();
            for (AdminOperatorPort.RoleRowView row : roleRowsBefore) {
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
        int keptAsDirect = 0;
        int reownedToGroup = 0;
        for (OperatorGroupMemberJpaEntity m : leaving) {
            roleRows += operatorPort.deleteOperatorRolesByGroupOrigin(operatorId, m.getGroupId());
            for (String assigned : assignmentPort.findGroupOriginAssignmentTenants(operatorId, m.getGroupId())) {
                if (tenantId.equals(assigned)) {
                    continue; // the T membership itself goes — that is the exit
                }
                // TASK-BE-636 ①: a group the person stays in (none of T's) still gives U → it takes the membership
                // over (smallest group id); else a remaining work grant keeps it as direct; else it goes.
                java.util.Optional<Long> nextOwner = assignmentPort.findReowningGroup(operatorId, assigned, leavingGroups);
                if (nextOwner.isPresent()) {
                    assignmentPort.reassignGroupOrigin(operatorId, assigned, nextOwner.get());
                    reownedToGroup++;
                } else if (keepsGrantsIn.contains(assigned)) {
                    assignmentPort.convertToDirectAssignment(operatorId, assigned);
                    keptAsDirect++;
                }
            }
            groupAssignments += assignmentPort.deleteGroupOriginAssignments(operatorId, m.getGroupId());
            groupMembers.delete(m);
        }
        int participations = partnershipPort.removeParticipationsWithPartner(operatorId, tenantId);
        roleRows += operatorPort.deleteOperatorRolesInTenant(operatorId, tenantId);
        // TASK-BE-631 (ADR-MONO-083 D4): the person's domain permission set rows in T, EVERY source (DIRECT ·
        // MIGRATION · GROUP:<id> — a group gives sets in its own tenant only, so the leaving groups' rows are all
        // here). Other companies' set rows are byte-unchanged; no «work role without membership» survives.
        int domainSetGrants = domainSetPort.deleteAllGrantsInTenant(operatorId, tenantId);
        return new ExitCounts(roleRows, leaving.size(), participations, groupAssignments, keptAsDirect,
                domainSetGrants, reownedToGroup);
    }
}
