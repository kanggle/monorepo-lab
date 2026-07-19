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
 * <p>Two invariants are load-bearing (data-model.md § group_origin):
 * <ul>
 *   <li><b>Idempotent materialise</b> — a member who already holds the {@code (operator,role)}
 *       / {@code (operator,tenant)} PK row (whether a DIRECT grant or another group's fan-out)
 *       is a no-op SKIP; never a duplicate, never an overwrite. Grant ⇒ at most one row.</li>
 *   <li><b>Strict cascade-revoke</b> — every revoke filters on {@code group_origin = groupId},
 *       so a direct grant ({@code group_origin IS NULL}) is <b>never</b> destroyed.</li>
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

    /** A fan-out target: the member's internal id, external UUID (cache key), and home tenant. */
    public record Member(long internalId, String externalId, String tenantId) {}

    /**
     * Grant-to-group / grant-add: materialise one grant template across all current members.
     *
     * @return the number of rows actually materialised (idempotent skips are not counted)
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
     * @return the number of rows actually materialised (idempotent skips are not counted)
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

    /** Remove-member: revoke ONLY this member's {@code group_origin=groupId} rows (direct grants untouched). */
    public void revokeMemberFanOut(Long groupInternalId, Member member) {
        operatorRoles.deleteByGroupOriginAndOperatorId(groupInternalId, member.internalId());
        assignments.deleteByGroupOriginAndOperatorId(groupInternalId, member.internalId());
        invalidate(member.externalId());
    }

    /** Delete-group: revoke ALL of the group's {@code group_origin=groupId} rows (direct grants untouched). */
    public void revokeGroupFanOut(Long groupInternalId, List<Member> members) {
        operatorRoles.deleteByGroupOrigin(groupInternalId);
        assignments.deleteByGroupOrigin(groupInternalId);
        for (Member m : members) {
            invalidate(m.externalId());
        }
    }

    /** Revoke-grant: revoke the {@code group_origin=groupId} rows of ONE grant template only. */
    public void revokeGrantFanOut(Long groupInternalId, OperatorGroupGrantJpaEntity grant, List<Member> members) {
        if (grant.isRole()) {
            operatorRoles.deleteByGroupOriginAndRoleId(groupInternalId, grant.getRoleId());
        } else {
            assignments.deleteByGroupOriginAndTenantId(groupInternalId, grant.getTenantId());
        }
        for (Member m : members) {
            invalidate(m.externalId());
        }
    }

    // ---- internals -------------------------------------------------------------

    /**
     * Materialise one grant onto one member. Returns {@code false} (no-op skip) when the
     * member already holds the equal PK row (direct OR another group's fan-out) — grant ⇒ at
     * most one row; the first writer owns the {@code group_origin} marker (single-owner v1).
     */
    private boolean materialise(Long groupInternalId, OperatorGroupGrantJpaEntity grant,
                                Member member, Long grantedBy, Instant now) {
        if (grant.isRole()) {
            var pk = new AdminOperatorRoleJpaEntity.PK(member.internalId(), grant.getRoleId());
            if (operatorRoles.existsById(pk)) {
                return false;
            }
            operatorRoles.save(AdminOperatorRoleJpaEntity.createGroupScoped(
                    member.internalId(), grant.getRoleId(), now, grantedBy, member.tenantId(), groupInternalId));
            return true;
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
