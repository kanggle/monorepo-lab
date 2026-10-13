package com.example.admin.application;

import com.example.admin.application.exception.DomainPermissionSetAlreadyGrantedException;
import com.example.admin.application.exception.DomainPermissionSetNotFoundException;
import com.example.admin.application.exception.GroupNotFoundException;
import com.example.admin.application.exception.IdempotencyKeyConflictException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.DomainPermissionSetPort;
import com.example.admin.application.port.DomainPermissionSetPort.GroupSetGrantView;
import com.example.admin.application.port.DomainPermissionSetPort.SetView;
import com.example.admin.domain.rbac.DomainPermissionSetPresets;
import com.example.admin.domain.rbac.DomainRoleCatalog;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.persistence.rbac.OperatorGroupJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.OperatorGroupJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.OperatorGroupMemberJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.OperatorGroupMemberJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * TASK-BE-631 slice B (ADR-MONO-083 D4 · D5) — the domain permission sets of a GROUP (admin-api.md § 도메인 업무 권한
 * 세트 — {@code GET|POST /groups/{groupId}/domain-permission-sets} · {@code DELETE …/{setId}}; § 행동 테넌트 row 27).
 *
 * <p><b>Who.</b> {@code domain_access.manage} is the endpoint gate ({@code @RequiresPermission}). The acting tenant is
 * the GROUP's {@code tenant_id} and must be in that key's grant scope: the read answers an out-of-scope group with
 * {@code 404 GROUP_NOT_FOUND} (enumeration-safe, as {@code GET /groups/{groupId}}); the writes with
 * {@code 403 TENANT_SCOPE_DENIED} + a best-effort DENIED row ({@link TenantScopeGuard}).
 *
 * <p><b>Which sets.</b> A preset or a set of the group's own tenant (another tenant's set → {@code 404}); never
 * {@code legacy-compat} ({@code 400} — MIGRATION only); every domain of the set inside the group tenant's entitled
 * domains ({@link DomainSetEntitlementGuard} — {@code 422} / {@code 503}, nothing written). The same set twice on one
 * group → {@code 409 DOMAIN_PERMISSION_SET_ALREADY_GRANTED}.
 *
 * <p><b>Fan-out</b> ({@link GroupDomainSetFanOut}): granting writes the template and one {@code GROUP:<id>} row per
 * CURRENT member in the group's tenant — never skipped because the member holds the set from another source. Revoking
 * deletes the template and this group's rows of that set only. Template + rows + audit row are one transaction; the
 * audit write is fail-closed (an audit failure rolls the change back).
 */
@Service
@RequiredArgsConstructor
public class GroupDomainPermissionSetUseCase {

    static final int MAX_IDEMPOTENCY_KEY = 100;

    private final OperatorGroupJpaRepository operatorGroups;
    private final OperatorGroupMemberJpaRepository groupMembers;
    private final DomainPermissionSetPort setPort;
    private final GroupDomainSetFanOut fanOut;
    private final TenantScopeGuard tenantScopeGuard;
    private final DomainSetEntitlementGuard entitlementGuard;
    private final AdminOperatorPort operatorPort;
    private final AdminActionAuditor auditor;

    /** {@code GET} — the group's sets, oldest first. No audit row; out of scope → 404 (existence not disclosed). */
    @Transactional(readOnly = true)
    public List<GroupDomainPermissionSetResult> list(OperatorContext actor, String groupId) {
        OperatorGroupJpaEntity group = requireGroup(groupId);
        if (!tenantScopeGuard.isTenantInScope(actor, Permission.DOMAIN_ACCESS_MANAGE, group.getTenantId())) {
            throw new GroupNotFoundException("Group not found: " + groupId);
        }
        List<GroupDomainPermissionSetResult> out = new ArrayList<>();
        for (GroupSetGrantView g : setPort.findGroupGrants(group.getId())) {
            out.add(toResult(g.set(), g.grantedAt(), g.grantedByOperatorId()));
        }
        return out;
    }

    @Transactional
    public GroupDomainPermissionSetResult.Granted grant(GrantGroupDomainPermissionSetCommand cmd) {
        AuditReasons.require(cmd.reason());
        String key = cmd.idempotencyKey();
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }
        if (key.length() > MAX_IDEMPOTENCY_KEY) {
            throw new IllegalArgumentException("Idempotency-Key must be at most " + MAX_IDEMPOTENCY_KEY + " characters");
        }
        if (cmd.setId() == null || cmd.setId().isBlank()) {
            throw new IllegalArgumentException("setId is required");
        }
        OperatorGroupJpaEntity group = requireGroup(cmd.groupId());
        String tenantId = group.getTenantId();
        tenantScopeGuard.requireTenantInScope(cmd.actor(), Permission.DOMAIN_ACCESS_MANAGE, tenantId,
                ActionCode.GROUP_DOMAIN_SET_GRANT);

        SetView set = setPort.findBySetId(cmd.setId()).orElseThrow(() -> setNotFound(cmd.setId()));
        if (!set.isPreset() && !set.tenantId().equals(tenantId)) {
            throw setNotFound(cmd.setId());
        }
        if (DomainPermissionSetPresets.LEGACY_COMPAT.equals(set.systemKey())) {
            throw new IllegalArgumentException(
                    "'legacy-compat' is granted by the migration only (MIGRATION source) — choose another set");
        }
        // A re-sent key → 409 before anything is written (its audit row would otherwise die on idx_admin_actions_idemp).
        if (auditor.isIdempotencyKeyUsed(cmd.actor().operatorId(), ActionCode.GROUP_DOMAIN_SET_GRANT, key)) {
            throw new IdempotencyKeyConflictException(
                    "Idempotency-Key already used for GROUP_DOMAIN_SET_GRANT by this operator; retry with a new key");
        }
        // Entitlement of the GROUP's tenant — 422 / 503, nothing written.
        entitlementGuard.requireEntitled(tenantId, DomainRoleCatalog.domainsOf(set.roles()));
        if (setPort.groupGrantExists(group.getId(), set.internalId())) {
            throw new DomainPermissionSetAlreadyGrantedException(
                    "Set '" + set.setId() + "' is already granted to group '" + group.getGroupId() + "'");
        }

        Instant now = Instant.now();
        Long actorInternalId = operatorPort.resolveActorInternalId(cmd.actor().operatorId());
        List<Long> members = new ArrayList<>();
        for (OperatorGroupMemberJpaEntity m : groupMembers.findByGroupId(group.getId())) {
            members.add(m.getOperatorId());
        }
        setPort.insertGroupGrant(group.getId(), set.internalId(), actorInternalId, now);
        int fanned = fanOut.fanOutSetToMembers(group.getId(), tenantId, set.internalId(), members,
                actorInternalId, now);
        boolean selfGrant = actorInternalId != null && members.contains(actorInternalId);
        audit(ActionCode.GROUP_DOMAIN_SET_GRANT, cmd.actor(), group.getGroupId(), tenantId, cmd.reason(), key,
                "setId=" + set.setId() + " fannedOutRows=" + fanned + " selfGrant=" + selfGrant, now);
        return new GroupDomainPermissionSetResult.Granted(toResult(set, now, cmd.actor().operatorId()), fanned);
    }

    @Transactional
    public void revoke(OperatorContext actor, String groupId, String setId, String reason) {
        AuditReasons.require(reason);
        OperatorGroupJpaEntity group = requireGroup(groupId);
        String tenantId = group.getTenantId();
        tenantScopeGuard.requireTenantInScope(actor, Permission.DOMAIN_ACCESS_MANAGE, tenantId,
                ActionCode.GROUP_DOMAIN_SET_REVOKE);
        SetView set = setPort.findBySetId(setId).orElseThrow(() -> setNotFound(setId));
        if (!setPort.groupGrantExists(group.getId(), set.internalId())) {
            throw new DomainPermissionSetNotFoundException(
                    "Set '" + setId + "' is not granted to group '" + groupId + "'");
        }
        Instant now = Instant.now();
        // This group's rows of this set only — DIRECT · MIGRATION · other groups' rows of the same set stay.
        int removed = setPort.deleteGroupSourceRowsOfSet(group.getId(), set.internalId());
        setPort.deleteGroupGrant(group.getId(), set.internalId());
        audit(ActionCode.GROUP_DOMAIN_SET_REVOKE, actor, group.getGroupId(), tenantId, reason, null,
                "setId=" + set.setId() + " removedRows=" + removed, now);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private OperatorGroupJpaEntity requireGroup(String groupId) {
        if (groupId == null || groupId.isBlank()) {
            throw new GroupNotFoundException("Group not found");
        }
        return operatorGroups.findByGroupId(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Group not found: " + groupId));
    }

    private static GroupDomainPermissionSetResult toResult(SetView set, Instant grantedAt, String grantedBy) {
        return new GroupDomainPermissionSetResult(set.setId(), set.name(), set.systemKey(), grantedAt, grantedBy);
    }

    private static DomainPermissionSetNotFoundException setNotFound(String setId) {
        return new DomainPermissionSetNotFoundException("Domain permission set '" + setId + "' not found");
    }

    private void audit(ActionCode code, OperatorContext actor, String groupId, String tenantId, String reason,
                       String idempotencyKey, String detail, Instant startedAt) {
        String auditId = auditor.newAuditId();
        auditor.recordWithPermission(new AdminActionAuditor.AuditRecord(
                auditId,
                code,
                actor,
                "GROUP",
                groupId,
                AuditReasons.normalize(reason),
                null,
                idempotencyKey != null ? idempotencyKey : "group-domain-set:" + code.name() + ":" + auditId,
                Outcome.SUCCESS,
                detail,
                startedAt,
                Instant.now(),
                tenantId), Permission.DOMAIN_ACCESS_MANAGE);
    }
}
