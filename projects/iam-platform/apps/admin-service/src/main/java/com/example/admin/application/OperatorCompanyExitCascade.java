package com.example.admin.application;

import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.TenantPartnershipPort;
import com.example.admin.infrastructure.persistence.rbac.OperatorGroupMemberJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.OperatorGroupMemberJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * TASK-MONO-784 S4 (ADR-MONO-082 b · D3 · decision D-9) — what «이 회사에서 내보내기» takes away besides the
 * membership row itself: every side of the person that lives in company T (admin-api.md § DELETE
 * …/assignments/{tenantId}).
 *
 * <ol>
 *   <li>memberships of T's groups, with those groups' fan-out rows for this person (784 Edge Case 3) — first,
 *       so the fan-out rows go through the group engine's strict {@code group_origin} filter;</li>
 *   <li>participant bindings in partnerships whose partner is T (784 AC-0 F18 — a person who later rejoins T
 *       must not find an old participation alive again);</li>
 *   <li>every role row living in T, whatever its origin (direct · group · org-node row recorded at T).</li>
 * </ol>
 *
 * <p>Nothing of another company is touched, nor the operator row (status · home · identity — D-10, R1), nor the
 * person's account (no store / fan sign-in change — ADR-080 D5 · ADR-082 D3). Joins the caller's transaction;
 * the caller deletes the membership row and audits.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class OperatorCompanyExitCascade {

    private final AdminOperatorPort operatorPort;
    private final OperatorGroupMemberJpaRepository groupMembers;
    private final GroupFanOutService fanOut;
    private final TenantPartnershipPort partnershipPort;

    /** How many rows of each kind left company T with the person (the audit {@code detail}). */
    public record ExitCounts(int roleRows, int groupMemberships, int participations) {
        public String detail() {
            return "roleRows=" + roleRows + ",groupMemberships=" + groupMemberships
                    + ",participations=" + participations;
        }
    }

    public ExitCounts exit(AdminOperatorPort.OperatorView operator, String tenantId) {
        long operatorId = operator.internalId();
        int groupRoleRows = 0;
        int memberships = 0;
        for (OperatorGroupMemberJpaEntity m : groupMembers.findByOperatorIdAndGroupTenantId(operatorId, tenantId)) {
            groupRoleRows += fanOut.revokeMemberFanOut(m.getGroupId(),
                    new GroupFanOutService.Member(operatorId, operator.operatorId(), tenantId));
            groupMembers.delete(m);
            memberships++;
        }
        int participations = partnershipPort.removeParticipationsWithPartner(operatorId, tenantId);
        int roleRows = groupRoleRows + operatorPort.deleteOperatorRolesInTenant(operatorId, tenantId);
        return new ExitCounts(roleRows, memberships, participations);
    }
}
