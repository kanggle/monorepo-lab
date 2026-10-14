package com.example.admin.application;

import com.example.admin.infrastructure.persistence.rbac.AdminOperatorRoleJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorRoleJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.CachingPermissionEvaluator;
import com.example.admin.infrastructure.persistence.rbac.OperatorGroupGrantJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.OperatorTenantAssignmentJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.OperatorTenantAssignmentJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * TASK-BE-520 / ADR-MONO-046 D5 — the operator-group <b>fan-out / cascade engine</b>.
 *
 * <p>v1 semantics are fan-out (D2-A), not inheritance: a group grant materialises an
 * <b>ordinary flat per-operator row</b> in the SAME substrate a direct grant uses
 * ({@code admin_operator_roles} for ROLE, {@code operator_tenant_assignment} for
 * TENANT_ASSIGNMENT), tagged with the {@code group_origin} marker. Evaluation /
 * {@code PermissionEvaluator} / the perm-cache never learn about groups — this service only
 * writes and deletes flat rows (rbac.md § Operator Group Fan-Out). It touches NONE of the
 * evaluation, cache, or confinement logic (AC-6).
 *
 * <p>Three invariants are load-bearing (data-model.md § group_origin):
 * <ul>
 *   <li><b>ROLE rows are per source</b> (TASK-BE-633 · V0053) — a ROLE fan-out writes this group's OWN row
 *       {@code (member, role, group tenant, 'GROUP:<id>')} even when the member already holds the role directly,
 *       from a node grant or from another group; only this group's own existing row is an idempotent skip. Before
 *       V0053 the key had no source, the first writer owned the row, and G1's revoke took away the role G2 still
 *       granted (data-model.md «단일-소유 마커(v1 규약)», now retired for roles).</li>
 *   <li><b>TENANT_ASSIGNMENT rows stay single-owner</b> — a member who already holds the
 *       {@code (operator, tenant)} membership (direct or another group's) is a no-op SKIP. (Per-source
 *       memberships are not part of TASK-BE-633.)</li>
 *   <li><b>Strict cascade-revoke</b> — every revoke filters on {@code group_origin = groupId},
 *       so a direct grant ({@code group_origin IS NULL}) is <b>never</b> destroyed — and, since TASK-BE-633
 *       (AC-3 · TASK-MONO-784 D-1), a fan-out membership is never deleted from under a role row the person still
 *       holds in that tenant: it is kept as a DIRECT membership instead (the same rule «이 회사에서 내보내기» applies
 *       to a leaving group's memberships — owner decision 2026-10-13 UTC «U 소속을 직접 소속으로 남김»).</li>
 * </ul>
 *
 * <p>Each method affects the fan-out member(s) and invalidates their perm-cache (best-effort,
 * mirroring {@code PatchOperatorRoleUseCase}). The methods join the caller's transaction
 * (propagation REQUIRED) so the fan-out + membership/grant change + audit are one unit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class GroupFanOutService {

    private final AdminOperatorRoleJpaRepository operatorRoles;
    private final OperatorTenantAssignmentJpaRepository assignments;
    private final CachingPermissionEvaluator cachingPermissionEvaluator;

    /**
     * A fan-out target: the member's internal id, external UUID (cache key), and the tenant its fan-out ROLE rows
     * live in — the GROUP's tenant (TASK-MONO-784 S4 · D-11; before S4 the member's home, which the old member
     * invariant «home == group tenant» made the same value).
     */
    public record Member(long internalId, String externalId, String tenantId) {}

    /**
     * Grant-to-group / grant-add: materialise one grant template across all current members.
     *
     * @return the number of grants that are NEW to a member (admin-api.md {@code fannedOutRows}): a ROLE row
     *         written for a member who already held that role from another source is not counted (its source row
     *         is still written — TASK-BE-633), nor is an idempotent skip
     */
    public int fanOutGrantToMembers(Long groupInternalId, OperatorGroupGrantJpaEntity grant,
                                    List<Member> members, Long grantedBy, Instant now) {
        int created = 0;
        for (Member m : members) {
            if (materialise(groupInternalId, grant, m, grantedBy, now)) {
                created++;
                invalidate(m.externalId());
            }
        }
        return created;
    }

    /**
     * Add-member: materialise the group's current grants onto the single new member.
     *
     * @return the number of grants NEW to the member (admin-api.md {@code fannedOutGrants} — see
     *         {@link #fanOutGrantToMembers})
     */
    public int fanOutGrantsToMember(Long groupInternalId, List<OperatorGroupGrantJpaEntity> grants,
                                    Member member, Long grantedBy, Instant now) {
        int created = 0;
        for (OperatorGroupGrantJpaEntity grant : grants) {
            if (materialise(groupInternalId, grant, member, grantedBy, now)) {
                created++;
            }
        }
        if (created > 0) {
            invalidate(member.externalId());
        }
        return created;
    }

    /**
     * Remove-member: revoke ONLY this member's {@code group_origin=groupId} rows (direct grants and other groups'
     * rows untouched — TASK-BE-633: they are separate rows now).
     *
     * @return how many of this group's fan-out memberships were KEPT as direct memberships because the person
     *         still holds a role row in that tenant (AC-3 · 784 D-1) — the caller records it in the audit detail
     */
    public int revokeMemberFanOut(Long groupInternalId, Member member) {
        operatorRoles.deleteByGroupOriginAndOperatorId(groupInternalId, member.internalId());
        int keptAsDirect = keepAssignmentsStillBackingRoles(groupInternalId,
                row -> member.internalId() == row.getOperatorId());
        assignments.deleteByGroupOriginAndOperatorId(groupInternalId, member.internalId());
        invalidate(member.externalId());
        return keptAsDirect;
    }

    /**
     * Delete-group: revoke ALL of the group's {@code group_origin=groupId} rows (direct grants untouched).
     *
     * @return memberships kept as direct (see {@link #revokeMemberFanOut})
     */
    public int revokeGroupFanOut(Long groupInternalId, List<Member> members) {
        operatorRoles.deleteByGroupOrigin(groupInternalId);
        int keptAsDirect = keepAssignmentsStillBackingRoles(groupInternalId, row -> true);
        assignments.deleteByGroupOrigin(groupInternalId);
        for (Member m : members) {
            invalidate(m.externalId());
        }
        return keptAsDirect;
    }

    /**
     * Revoke-grant: revoke the {@code group_origin=groupId} rows of ONE grant template only.
     *
     * @return memberships kept as direct (see {@link #revokeMemberFanOut}); always 0 for a ROLE grant
     */
    public int revokeGrantFanOut(Long groupInternalId, OperatorGroupGrantJpaEntity grant, List<Member> members) {
        int keptAsDirect = 0;
        if (grant.isRole()) {
            operatorRoles.deleteByGroupOriginAndRoleId(groupInternalId, grant.getRoleId());
        } else {
            String tenantId = grant.getTenantId();
            keptAsDirect = keepAssignmentsStillBackingRoles(groupInternalId,
                    row -> tenantId != null && tenantId.equals(row.getTenantId()));
            assignments.deleteByGroupOriginAndTenantId(groupInternalId, tenantId);
        }
        for (Member m : members) {
            invalidate(m.externalId());
        }
        return keptAsDirect;
    }

    // ---- internals -------------------------------------------------------------

    /**
     * TASK-BE-633 AC-3 (TASK-MONO-784 D-1 «a non-'*' role row's tenant is one of the person's memberships»). Before a
     * group's fan-out memberships are deleted, each one whose person still holds a role row in that tenant — direct,
     * a node grant, or another group's fan-out — is turned into a DIRECT membership ({@code group_origin} → NULL
     * only; granted_at · granted_by · org_scope · permission_set_id kept) and so survives the delete that follows.
     * Without it, revoking a group's TENANT_ASSIGNMENT (or the person leaving the group, or the group being deleted)
     * left role rows in a company the person no longer belonged to. Same rule, same owner decision (2026-10-13 UTC
     * «U 소속을 직접 소속으로 남김») as {@link OperatorCompanyExitCascade} applies to a leaving group's memberships.
     *
     * <p>Called AFTER this write's own role-row deletes, so the group's own fan-out role rows never keep its
     * membership alive.
     *
     * @return the number of memberships kept as direct
     */
    private int keepAssignmentsStillBackingRoles(Long groupInternalId,
                                                 java.util.function.Predicate<OperatorTenantAssignmentJpaEntity> rows) {
        int kept = 0;
        for (OperatorTenantAssignmentJpaEntity row : assignments.findByGroupOrigin(groupInternalId)) {
            if (!rows.test(row)) {
                continue;
            }
            if (operatorRoles.existsByOperatorIdAndTenantId(row.getOperatorId(), row.getTenantId())) {
                row.setGroupOrigin(null);
                assignments.saveAndFlush(row); // BE-335: explicit flush, before the bulk delete that follows
                kept++;
            }
        }
        return kept;
    }

    /**
     * Materialise one grant onto one member. Returns {@code true} when the grant is NEW to the member.
     *
     * <p>ROLE (TASK-BE-633 · V0053): this group's own row {@code (member, role, group tenant, 'GROUP:<id>')} is
     * written unless it already exists (re-add idempotence); it is written EVEN when the member holds the role from
     * another source (direct · node · another group) — one row per source, so revoking one source never takes away
     * the role another source still grants. Counted only when no source held the role before.
     *
     * <p>TENANT_ASSIGNMENT: unchanged single-owner skip on the {@code (operator, tenant)} key.
     */
    private boolean materialise(Long groupInternalId, OperatorGroupGrantJpaEntity grant,
                                Member member, Long grantedBy, Instant now) {
        if (grant.isRole()) {
            // TASK-MONO-784 S2 (V0051) · S4 (D-11): the key carries the row's tenant, and both the skip key and
            // the written row use member.tenantId() = the GROUP's tenant. TASK-BE-633 (V0053): and the group's own
            // source — the key and the written row must name the same tenant AND source, or the skip check would
            // miss its own row (a duplicate-key INSERT instead of an idempotent skip).
            String source = AdminOperatorRoleJpaEntity.groupSource(groupInternalId);
            var pk = new AdminOperatorRoleJpaEntity.PK(
                    member.internalId(), grant.getRoleId(), member.tenantId(), source);
            if (operatorRoles.existsById(pk)) {
                return false;
            }
            boolean heldBefore = operatorRoles.existsByOperatorIdAndRoleIdAndTenantId(
                    member.internalId(), grant.getRoleId(), member.tenantId());
            operatorRoles.save(AdminOperatorRoleJpaEntity.createGroupScoped(
                    member.internalId(), grant.getRoleId(), now, grantedBy, member.tenantId(), groupInternalId));
            return !heldBefore;
        }
        var pk = new OperatorTenantAssignmentJpaEntity.PK(member.internalId(), grant.getTenantId());
        if (assignments.existsById(pk)) {
            return false;
        }
        assignments.save(OperatorTenantAssignmentJpaEntity.createGroupScoped(
                member.internalId(), grant.getTenantId(), now, grantedBy, groupInternalId));
        return true;
    }

    private void invalidate(String operatorExternalId) {
        if (cachingPermissionEvaluator == null || operatorExternalId == null) {
            return;
        }
        try {
            cachingPermissionEvaluator.invalidate(operatorExternalId);
        } catch (RuntimeException ex) {
            log.warn("Permission cache invalidate failed for operatorId={} cause={}",
                    operatorExternalId, ex.getClass().getSimpleName());
        }
    }
}
