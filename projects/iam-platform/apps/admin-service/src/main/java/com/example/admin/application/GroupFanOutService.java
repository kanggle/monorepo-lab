package com.example.admin.application;

import com.example.admin.domain.rbac.DomainGrantSource;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorRoleJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorRoleJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.CachingPermissionEvaluator;
import com.example.admin.infrastructure.persistence.rbac.OperatorDomainSetGrantJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.OperatorGroupGrantJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.OperatorTenantAssignmentJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.OperatorTenantAssignmentJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

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
 *   <li><b>A TENANT_ASSIGNMENT row has one recorded owner, which is HANDED OVER on revoke</b> (TASK-BE-636 ①) — a
 *       member who already holds the {@code (operator, tenant)} membership (direct or another group's) is a no-op
 *       SKIP at fan-out, as before. When the owning group's source goes (its TENANT_ASSIGNMENT revoked, the person
 *       leaving it, the group deleted), the row is re-owned by another group the person is still a member of that
 *       gives the same tenant — derived from templates × members, so no provenance needs recording.</li>
 *   <li><b>Strict cascade-revoke</b> — every revoke filters on {@code group_origin = groupId},
 *       so a direct grant ({@code group_origin IS NULL}) is <b>never</b> destroyed — and, since TASK-BE-633
 *       (AC-3 · TASK-MONO-784 D-1) / TASK-BE-636 ②, a fan-out membership no other group takes over is never deleted
 *       from under a role row OR a domain permission set row the person still holds in that tenant: it is kept as a
 *       DIRECT membership instead (the same rule «이 회사에서 내보내기» applies to a leaving group's memberships —
 *       owner decision 2026-10-13 UTC «U 소속을 직접 소속으로 남김»).</li>
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

    /** A {@code source_key} no set row carries — «exclude no source» in the backing check. */
    private static final String NO_SOURCE = "-";

    private final AdminOperatorRoleJpaRepository operatorRoles;
    private final OperatorTenantAssignmentJpaRepository assignments;
    private final OperatorDomainSetGrantJpaRepository domainSetGrants;
    private final CachingPermissionEvaluator cachingPermissionEvaluator;

    /**
     * A fan-out target: the member's internal id, external UUID (cache key), and the tenant its fan-out ROLE rows
     * live in — the GROUP's tenant (TASK-MONO-784 S4 · D-11; before S4 the member's home, which the old member
     * invariant «home == group tenant» made the same value).
     */
    public record Member(long internalId, String externalId, String tenantId) {}

    /**
     * What a group revoke did to the fan-out memberships it owned, besides deleting them — recorded in the audit
     * detail, never silent.
     *
     * @param reownedToGroup memberships handed to another group that still gives the same tenant (TASK-BE-636 ①)
     * @param keptAsDirect   memberships turned DIRECT because a role or set row still backs them (BE-633 · BE-636 ②)
     */
    public record RevokeOutcome(int reownedToGroup, int keptAsDirect) {
        public static final RevokeOutcome NONE = new RevokeOutcome(0, 0);

        /**
         * {@code reownedToGroup=N,keptAsDirect=N} (each part only when non-zero), or {@code null} when neither
         * happened — the usual audit detail stays byte-unchanged.
         */
        public String auditDetail() {
            List<String> parts = new ArrayList<>();
            if (reownedToGroup > 0) {
                parts.add("reownedToGroup=" + reownedToGroup);
            }
            if (keptAsDirect > 0) {
                parts.add("keptAsDirect=" + keptAsDirect);
            }
            return parts.isEmpty() ? null : String.join(",", parts);
        }
    }

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
     * @return how many of this group's fan-out memberships were RE-OWNED by another group or KEPT as direct
     *         memberships (see {@link #settleGroupMemberships}) — the caller records it in the audit detail
     */
    public RevokeOutcome revokeMemberFanOut(Long groupInternalId, Member member) {
        operatorRoles.deleteByGroupOriginAndOperatorId(groupInternalId, member.internalId());
        RevokeOutcome outcome = settleGroupMemberships(groupInternalId,
                row -> member.internalId() == row.getOperatorId(), true);
        assignments.deleteByGroupOriginAndOperatorId(groupInternalId, member.internalId());
        invalidate(member.externalId());
        return outcome;
    }

    /**
     * Delete-group: revoke ALL of the group's {@code group_origin=groupId} rows (direct grants untouched).
     *
     * @return memberships re-owned / kept as direct (see {@link #revokeMemberFanOut})
     */
    public RevokeOutcome revokeGroupFanOut(Long groupInternalId, List<Member> members) {
        operatorRoles.deleteByGroupOrigin(groupInternalId);
        RevokeOutcome outcome = settleGroupMemberships(groupInternalId, row -> true, true);
        assignments.deleteByGroupOrigin(groupInternalId);
        for (Member m : members) {
            invalidate(m.externalId());
        }
        return outcome;
    }

    /**
     * Revoke-grant: revoke the {@code group_origin=groupId} rows of ONE grant template only.
     *
     * @return memberships re-owned / kept as direct (see {@link #revokeMemberFanOut}); {@link RevokeOutcome#NONE}
     *         for a ROLE grant
     */
    public RevokeOutcome revokeGrantFanOut(Long groupInternalId, OperatorGroupGrantJpaEntity grant,
                                           List<Member> members) {
        RevokeOutcome outcome = RevokeOutcome.NONE;
        if (grant.isRole()) {
            operatorRoles.deleteByGroupOriginAndRoleId(groupInternalId, grant.getRoleId());
        } else {
            String tenantId = grant.getTenantId();
            // Only the TENANT_ASSIGNMENT goes: the group's own set rows stay, so they still back the membership.
            outcome = settleGroupMemberships(groupInternalId,
                    row -> tenantId != null && tenantId.equals(row.getTenantId()), false);
            assignments.deleteByGroupOriginAndTenantId(groupInternalId, tenantId);
        }
        for (Member m : members) {
            invalidate(m.externalId());
        }
        return outcome;
    }

    // ---- internals -------------------------------------------------------------

    /**
     * Before a group's fan-out memberships are deleted, each selected one is settled in this order:
     * <ol>
     *   <li><b>re-owned</b> (TASK-BE-636 ①) — when another group G′ the person is still a member of gives the same
     *       tenant by its own TENANT_ASSIGNMENT template, the row's {@code group_origin} becomes G′ (the SMALLEST
     *       such {@code operator_group.id} — deterministic). Before, the membership was single-owner: G2's fan-out
     *       skipped because G1 already held the row, so G1's revoke took U away although G2 still gave it.
     *       <br>Only {@code group_origin} changes. A TENANT_ASSIGNMENT template carries no membership attribute (it
     *       is {@code tenant_id} only, and every fan-out row is written whole-tenant with no permission set —
     *       {@link OperatorTenantAssignmentJpaEntity#createGroupScoped}), so G′ would have written exactly what G
     *       wrote. {@code org_scope} · {@code permission_set_id} · {@code granted_at} · {@code granted_by} are what
     *       an administrator later set on THIS membership and are kept — resetting {@code org_scope} to NULL would
     *       silently widen the person's data scope;</li>
     *   <li><b>kept as DIRECT</b> (TASK-BE-633 AC-3 · TASK-BE-636 ② — TASK-MONO-784 D-1 «a non-'*' row's tenant is
     *       one of the person's memberships») — when the person still holds a role row OR a domain permission set
     *       row in that tenant, {@code group_origin} → NULL (other columns kept). Before BE-636 only role rows
     *       counted, so a person holding only set rows lost the membership and kept the set rows. Same rule, same
     *       owner decision (2026-10-13 UTC «U 소속을 직접 소속으로 남김») as {@link OperatorCompanyExitCascade};</li>
     *   <li>otherwise left for the delete that follows.</li>
     * </ol>
     *
     * <p>Called AFTER this write's own role-row deletes, so the group's own fan-out role rows never keep its
     * membership alive; with {@code excludeOwnSetRows} neither do its own {@code GROUP:<id>} set rows (removed right
     * after by {@code GroupDomainSetFanOut} on member removal and group deletion).
     */
    private RevokeOutcome settleGroupMemberships(Long groupInternalId,
                                                 Predicate<OperatorTenantAssignmentJpaEntity> rows,
                                                 boolean excludeOwnSetRows) {
        int reowned = 0;
        int kept = 0;
        String ignoredSetSource = excludeOwnSetRows ? DomainGrantSource.group(groupInternalId) : NO_SOURCE;
        for (OperatorTenantAssignmentJpaEntity row : assignments.findByGroupOrigin(groupInternalId)) {
            if (!rows.test(row)) {
                continue;
            }
            java.util.Optional<Long> nextOwner = assignments.findLowestOtherGroupGivingTenant(
                    row.getOperatorId(), row.getTenantId(), List.of(groupInternalId));
            if (nextOwner.isPresent()) {
                row.setGroupOrigin(nextOwner.get());
                assignments.saveAndFlush(row); // BE-335: explicit flush, before the bulk delete that follows
                reowned++;
            } else if (operatorRoles.existsByOperatorIdAndTenantId(row.getOperatorId(), row.getTenantId())
                    || domainSetGrants.existsByOperatorIdAndTenantIdAndSourceKeyNot(
                            row.getOperatorId(), row.getTenantId(), ignoredSetSource)) {
                row.setGroupOrigin(null);
                assignments.saveAndFlush(row);
                kept++;
            }
        }
        return new RevokeOutcome(reowned, kept);
    }

    /**
     * Materialise one grant onto one member. Returns {@code true} when the grant is NEW to the member.
     *
     * <p>ROLE (TASK-BE-633 · V0053): this group's own row {@code (member, role, group tenant, 'GROUP:<id>')} is
     * written unless it already exists (re-add idempotence); it is written EVEN when the member holds the role from
     * another source (direct · node · another group) — one row per source, so revoking one source never takes away
     * the role another source still grants. Counted only when no source held the role before.
     *
     * <p>TENANT_ASSIGNMENT: single-owner skip on the {@code (operator, tenant)} key — the skipped group's source is
     * not lost: it is re-derived from templates × members when the owner's source goes ({@link #settleGroupMemberships}).
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
