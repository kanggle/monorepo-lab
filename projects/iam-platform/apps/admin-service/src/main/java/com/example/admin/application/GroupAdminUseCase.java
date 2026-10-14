package com.example.admin.application;

import com.example.admin.application.exception.GroupGrantAlreadyExistsException;
import com.example.admin.application.exception.GroupGrantNoEscalationException;
import com.example.admin.application.exception.GroupGrantNotFoundException;
import com.example.admin.application.exception.GroupMemberAlreadyExistsException;
import com.example.admin.application.exception.GroupMemberNotFoundException;
import com.example.admin.application.exception.GroupMemberTenantMismatchException;
import com.example.admin.application.exception.GroupNameConflictException;
import com.example.admin.application.exception.GroupNotFoundException;
import com.example.admin.application.exception.OperatorNotFoundException;
import com.example.admin.application.exception.PermissionDeniedException;
import com.example.admin.application.exception.RoleNotFoundException;
import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.OperatorTenantAssignmentPort;
import com.example.admin.domain.rbac.AdminOperator;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.persistence.rbac.AdminGrantScopeEvaluator;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.AdminRoleJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminRoleJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.OperatorGroupGrantJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.OperatorGroupGrantJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.OperatorGroupJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.OperatorGroupJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.OperatorGroupMemberJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.OperatorGroupMemberJpaRepository;
import com.example.common.id.UuidV7;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * TASK-BE-520 (ADR-MONO-046 D1–D6) — the operator-group command gateway.
 *
 * <p>A thin gateway (the shape {@link OrgNodeAdminUseCase} / {@link TenantScopeGuard} callers
 * already use). It composes the <b>reused</b> guards — it never reinvents them:
 * <ul>
 *   <li>{@link TenantScopeGuard#requireTenantInScope} first on every mutation (D3) — a
 *       {@code TENANT_ADMIN @ acme} manages only acme's groups; {@code SUPER_ADMIN} ({@code '*'})
 *       net-zero. The target tenant is the group's own {@code tenant_id}.</li>
 *   <li>{@link RoleGrantGuard#requireGrantable} (ADR-024 D3 ≤-own) at grant-to-group AND
 *       add-member fan-out time (D4) for ROLE grants; the {@code operator.manage} effective
 *       admin scope caps TENANT_ASSIGNMENT grants — so a group can never be a bypass.</li>
 *   <li>{@link GroupFanOutService} materialises / cascade-revokes the flat substrate rows
 *       (D5). The evaluator / cache / confinement axes stay byte-unchanged (AC-6).</li>
 * </ul>
 *
 * <p>Every mutation is audited SUCCESS via {@link AdminActionAuditor}; reads write no audit
 * row on success (BE-486 read-path convention). Mutations are {@code @Transactional} so the
 * grant/member change + fan-out + audit are one unit (there is no remote call in this
 * use-case, unlike {@link OrgNodeAdminUseCase}).
 */
@Service
@RequiredArgsConstructor
public class GroupAdminUseCase {

    private final OperatorGroupJpaRepository operatorGroups;
    private final OperatorGroupMemberJpaRepository groupMembers;
    private final OperatorGroupGrantJpaRepository groupGrants;
    private final AdminOperatorJpaRepository adminOperators;
    private final AdminRoleJpaRepository adminRoles;
    private final TenantScopeGuard tenantScopeGuard;
    private final RoleGrantGuard roleGrantGuard;
    private final AdminGrantScopeEvaluator grantScopeEvaluator;
    private final GroupFanOutService fanOut;
    private final AdminActionAuditor auditor;
    private final OperatorTenantAssignmentPort assignmentPort;
    private final TenantLastAdminGuard lastAdminGuard;
    private final GroupDomainSetFanOut domainSetFanOut;

    // ==== Group CRUD ===========================================================

    @Transactional
    public GroupView createGroup(OperatorContext actor, String tenantId, String name,
                                 String description, String reason) {
        String trimmedName = validateName(name);
        requireConcreteTenant(tenantId);
        tenantScopeGuard.requireTenantInScope(actor, Permission.GROUP_MANAGE, tenantId, ActionCode.GROUP_CREATE);
        if (operatorGroups.existsByTenantIdAndName(tenantId, trimmedName)) {
            throw new GroupNameConflictException(
                    "Group '" + trimmedName + "' already exists in tenant '" + tenantId + "'");
        }
        Instant now = Instant.now();
        OperatorGroupJpaEntity saved = operatorGroups.save(OperatorGroupJpaEntity.create(
                UuidV7.randomString(), tenantId, trimmedName, description, actorInternalId(actor), now));
        audit(ActionCode.GROUP_CREATE, saved.getGroupId(), tenantId, actor, reason, null);
        return toView(saved, 0, 0);
    }

    /** D3 read-confine: only groups whose tenant is in the actor's {@code group.manage} scope. */
    public GroupPage listGroups(OperatorContext actor, String tenantIdFilter, int page, int size) {
        Set<String> scope = grantScopeEvaluator.effectiveAdminScope(actorId(actor), Permission.GROUP_MANAGE);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        boolean hasFilter = tenantIdFilter != null && !tenantIdFilter.isBlank();

        Page<OperatorGroupJpaEntity> result;
        if (scope.contains(AdminOperator.PLATFORM_TENANT_ID)) {
            result = hasFilter ? operatorGroups.findByTenantId(tenantIdFilter, pageable)
                    : operatorGroups.findAll(pageable);
        } else {
            Set<String> tenants = scope;
            if (hasFilter) {
                tenants = scope.contains(tenantIdFilter) ? Set.of(tenantIdFilter) : Set.of();
            }
            result = tenants.isEmpty() ? Page.empty(pageable)
                    : operatorGroups.findByTenantIdIn(tenants, pageable);
        }
        return toPage(result);
    }

    public GroupView getGroup(OperatorContext actor, String groupId) {
        return toView(requireGroupReadableEnumSafe(actor, groupId));
    }

    @Transactional
    public GroupView updateGroup(OperatorContext actor, String groupId, String name,
                                 String description, String reason) {
        OperatorGroupJpaEntity group = requireGroupForMutation(actor, groupId, ActionCode.GROUP_UPDATE);
        String trimmedName = name == null ? null : validateName(name);
        if (trimmedName != null && !trimmedName.equals(group.getName())
                && operatorGroups.existsByTenantIdAndName(group.getTenantId(), trimmedName)) {
            throw new GroupNameConflictException(
                    "Group '" + trimmedName + "' already exists in tenant '" + group.getTenantId() + "'");
        }
        group.applyUpdate(trimmedName, description, Instant.now());
        operatorGroups.save(group);
        audit(ActionCode.GROUP_UPDATE, group.getGroupId(), group.getTenantId(), actor, reason, null);
        return toView(group);
    }

    @Transactional
    public void deleteGroup(OperatorContext actor, String groupId, String reason) {
        OperatorGroupJpaEntity group = requireGroupForMutation(actor, groupId, ActionCode.GROUP_DELETE);
        // 784 S4 (owner decision 2026-10-11 UTC): disbanding the group revokes every role it fanned out — the same
        // «last administrator» predicate, or deleting the group would route around the member / grant checks.
        Long groupInternalId = group.getId();
        lastAdminGuard.requireAdminRemains(group.getTenantId(),
                row -> groupInternalId.equals(row.groupOrigin()), null);
        // Cascade-revoke every group_origin fan-out row (audit/outbox atomicity, D5/D6) BEFORE
        // deleting the group. Direct grants (group_origin IS NULL) are untouched. The FK
        // ON DELETE CASCADE is only the DB backstop; the application does it explicitly.
        int keptAsDirect = fanOut.revokeGroupFanOut(group.getId(), resolveMembers(group));
        // ADR-083 D4: every GROUP:<this group> set row and the group's set templates, in this transaction (the
        // FK ON DELETE CASCADE on group_origin / group_id is only the safety net).
        domainSetFanOut.revokeGroup(group.getId());
        operatorGroups.delete(group); // membership + grant templates cascade via FK
        audit(ActionCode.GROUP_DELETE, group.getGroupId(), group.getTenantId(), actor, reason,
                keptAsDirect > 0 ? keptAsDirectDetail(keptAsDirect) : null);
    }

    // ==== Members ==============================================================

    public List<MemberView> listMembers(OperatorContext actor, String groupId) {
        OperatorGroupJpaEntity group = requireGroupReadable403(actor, groupId);
        return groupMembers.findByGroupId(group.getId()).stream()
                .map(m -> {
                    AdminOperatorJpaEntity op = adminOperators.findById(m.getOperatorId()).orElse(null);
                    return new MemberView(
                            op == null ? null : op.getOperatorId(),
                            op == null ? null : op.getDisplayName(),
                            m.getAddedAt());
                })
                .toList();
    }

    @Transactional
    public MemberAddResult addMember(OperatorContext actor, String groupId,
                                     String operatorPublicId, String reason) {
        OperatorGroupJpaEntity group = requireGroupForMutation(actor, groupId, ActionCode.GROUP_MEMBER_ADD);
        AdminOperatorJpaEntity target = adminOperators.findByOperatorId(operatorPublicId)
                .orElseThrow(() -> new OperatorNotFoundException(
                        "Operator not found for operatorId=" + operatorPublicId));
        // TASK-MONO-784 S4 (ADR-MONO-082 D2 · D-14): a platform operator takes no company membership.
        if (AdminOperator.PLATFORM_TENANT_ID.equals(target.getTenantId())) {
            throw new IllegalArgumentException(
                    "A platform ('*') operator cannot be a member of a company group");
        }
        // D3 · 784 S4 (D-11): a group holds only its own tenant's operators — the group's tenant must be one of
        // the target's MEMBERSHIPS (before S4: the target's home). A «member, no role» operator qualifies: the
        // group is how that person gets a role in this company (784 AC-0 F10).
        if (!assignmentPort.assignmentExists(target.getId(), group.getTenantId())) {
            throw new GroupMemberTenantMismatchException(
                    "Operator '" + operatorPublicId + "' does not belong to group tenant '"
                            + group.getTenantId() + "'");
        }
        if (groupMembers.existsById(new OperatorGroupMemberJpaEntity.PK(group.getId(), target.getId()))) {
            throw new GroupMemberAlreadyExistsException(
                    "Operator '" + operatorPublicId + "' is already a member of group '" + groupId + "'");
        }
        // D4 re-check: fanning the group's current grants onto the new member must not exceed
        // the actor's own holdings (no group-as-bypass).
        List<OperatorGroupGrantJpaEntity> currentGrants = groupGrants.findByGroupId(group.getId());
        requireGrantsWithinActorHoldings(actor, currentGrants, ActionCode.GROUP_MEMBER_ADD, group.getTenantId());
        // TASK-BE-631 (ADR-MONO-083 D5 · admin-api.md POST …/members): adding a member to a group that carries
        // domain permission sets GIVES that person work roles — so it also needs domain_access.manage in the group
        // tenant's grant scope, or group.manage alone (e.g. ORG_ADMIN) would route around D5. Refused before any
        // write: 403 PERMISSION_DENIED + DENIED row, no membership / role / set row. A group with no set: unchanged.
        if (domainSetFanOut.groupHasSets(group.getId())
                && !grantScopeEvaluator.isTenantInAdminScope(
                        actorId(actor), Permission.DOMAIN_ACCESS_MANAGE, group.getTenantId())) {
            auditor.recordDenied(ActionCode.GROUP_MEMBER_ADD, Permission.DOMAIN_ACCESS_MANAGE,
                    "/api/admin/groups/" + groupId + "/members", "POST", group.getGroupId());
            throw new PermissionDeniedException("Group '" + groupId + "' carries domain permission sets — adding a "
                    + "member also requires domain_access.manage in tenant '" + group.getTenantId() + "'");
        }

        Instant now = Instant.now();
        Long actorInternalId = actorInternalId(actor);
        groupMembers.save(OperatorGroupMemberJpaEntity.create(group.getId(), target.getId(), now, actorInternalId));
        int fanned = fanOut.fanOutGrantsToMember(group.getId(), currentGrants,
                new GroupFanOutService.Member(target.getId(), target.getOperatorId(), group.getTenantId()),
                actorInternalId, now);
        // ADR-083 D4: every set of the group onto the new member, as this group's own rows (never skipped).
        domainSetFanOut.fanOutSetsToMember(group.getId(), group.getTenantId(), target.getId(), actorInternalId, now);
        audit(ActionCode.GROUP_MEMBER_ADD, group.getGroupId(), group.getTenantId(), actor, reason,
                "operator=" + operatorPublicId);
        return new MemberAddResult(target.getOperatorId(), target.getDisplayName(), now, fanned);
    }

    @Transactional
    public void removeMember(OperatorContext actor, String groupId, String operatorPublicId, String reason) {
        OperatorGroupJpaEntity group = requireGroupForMutation(actor, groupId, ActionCode.GROUP_MEMBER_REMOVE);
        // Enumeration-safe: an unknown operator is indistinguishable from a non-member.
        AdminOperatorJpaEntity target = adminOperators.findByOperatorId(operatorPublicId)
                .orElseThrow(() -> new GroupMemberNotFoundException(
                        "Operator '" + operatorPublicId + "' is not a member of group '" + groupId + "'"));
        OperatorGroupMemberJpaEntity.PK pk = new OperatorGroupMemberJpaEntity.PK(group.getId(), target.getId());
        if (!groupMembers.existsById(pk)) {
            throw new GroupMemberNotFoundException(
                    "Operator '" + operatorPublicId + "' is not a member of group '" + groupId + "'");
        }
        // 784 S4 (owner decision 2026-10-11 UTC): the group's tenant keeps an administrator after this member's
        // fan-out rows go.
        Long groupInternalId = group.getId();
        long memberInternalId = target.getId();
        lastAdminGuard.requireAdminRemains(group.getTenantId(),
                row -> row.operatorInternalId() == memberInternalId
                        && groupInternalId.equals(row.groupOrigin()),
                null);
        // Revoke ONLY this member's group_origin rows for this group; direct grants untouched.
        int keptAsDirect = fanOut.revokeMemberFanOut(group.getId(),
                new GroupFanOutService.Member(target.getId(), target.getOperatorId(), group.getTenantId()));
        // ADR-083 D4: this member's GROUP:<this group> set rows only — DIRECT / MIGRATION / other groups' stay.
        domainSetFanOut.revokeMember(group.getId(), target.getId());
        groupMembers.deleteById(pk);
        audit(ActionCode.GROUP_MEMBER_REMOVE, group.getGroupId(), group.getTenantId(), actor, reason,
                "operator=" + operatorPublicId + (keptAsDirect > 0 ? "," + keptAsDirectDetail(keptAsDirect) : ""));
    }

    // ==== Grants ===============================================================

    public List<GrantView> listGrants(OperatorContext actor, String groupId) {
        OperatorGroupJpaEntity group = requireGroupReadable403(actor, groupId);
        return groupGrants.findByGroupId(group.getId()).stream().map(this::toGrantView).toList();
    }

    @Transactional
    public GrantAddResult addGrants(OperatorContext actor, String groupId, List<String> roleNames,
                                    List<String> tenantIds, String reason) {
        OperatorGroupJpaEntity group = requireGroupForMutation(actor, groupId, ActionCode.GROUP_GRANT_ADD);
        boolean hasRoles = roleNames != null && !roleNames.isEmpty();
        boolean hasTenants = tenantIds != null && !tenantIds.isEmpty();
        if (!hasRoles && !hasTenants) {
            throw new IllegalArgumentException("At least one of 'roles' or 'tenantAssignments' must be supplied");
        }

        // Resolve roles + D4 no-escalation (ROLE → RoleGrantGuard ≤-own).
        List<AdminRoleJpaEntity> roleEntities = new ArrayList<>();
        if (hasRoles) {
            for (String roleName : roleNames) {
                roleEntities.add(adminRoles.findByName(roleName)
                        .orElseThrow(() -> new RoleNotFoundException("Role not found: " + roleName)));
            }
            roleGrantGuard.requireGrantable(actor, roleEntities.stream().map(this::roleView).toList(),
                    ActionCode.GROUP_GRANT_ADD, group.getTenantId());
        }
        // D4 no-escalation for tenant assignments (target ∈ actor's operator.manage scope).
        if (hasTenants) {
            for (String tenantId : tenantIds) {
                requireConcreteTenant(tenantId);
                if (!grantScopeEvaluator.isTenantInAdminScope(actorId(actor), Permission.OPERATOR_MANAGE, tenantId)) {
                    throw new GroupGrantNoEscalationException(
                            "Actor may not grant the group an assignment to tenant '" + tenantId
                                    + "' (outside its operator.manage scope)");
                }
            }
        }

        Instant now = Instant.now();
        Long actorInternalId = actorInternalId(actor);
        List<GroupFanOutService.Member> members = resolveMembers(group);
        List<GrantView> created = new ArrayList<>();
        int fanned = 0;

        for (AdminRoleJpaEntity role : roleEntities) {
            if (groupGrants.existsByGroupIdAndGrantTypeAndRoleId(
                    group.getId(), OperatorGroupGrantJpaEntity.TYPE_ROLE, role.getId())) {
                throw new GroupGrantAlreadyExistsException(
                        "Group '" + groupId + "' already grants role '" + role.getName() + "'");
            }
            OperatorGroupGrantJpaEntity template = groupGrants.save(OperatorGroupGrantJpaEntity.role(
                    UuidV7.randomString(), group.getId(), role.getId(), actorInternalId, now));
            fanned += fanOut.fanOutGrantToMembers(group.getId(), template, members, actorInternalId, now);
            created.add(new GrantView(template.getGrantId(), OperatorGroupGrantJpaEntity.TYPE_ROLE,
                    role.getName(), null, now));
        }
        if (hasTenants) {
            for (String tenantId : tenantIds) {
                if (groupGrants.existsByGroupIdAndGrantTypeAndTenantId(
                        group.getId(), OperatorGroupGrantJpaEntity.TYPE_TENANT_ASSIGNMENT, tenantId)) {
                    throw new GroupGrantAlreadyExistsException(
                            "Group '" + groupId + "' already grants an assignment to tenant '" + tenantId + "'");
                }
                OperatorGroupGrantJpaEntity template = groupGrants.save(OperatorGroupGrantJpaEntity.tenantAssignment(
                        UuidV7.randomString(), group.getId(), tenantId, actorInternalId, now));
                fanned += fanOut.fanOutGrantToMembers(group.getId(), template, members, actorInternalId, now);
                created.add(new GrantView(template.getGrantId(),
                        OperatorGroupGrantJpaEntity.TYPE_TENANT_ASSIGNMENT, null, tenantId, now));
            }
        }
        audit(ActionCode.GROUP_GRANT_ADD, group.getGroupId(), group.getTenantId(), actor, reason,
                "grants=" + created.size());
        return new GrantAddResult(created, fanned);
    }

    @Transactional
    public void revokeGrant(OperatorContext actor, String groupId, String grantId, String reason) {
        OperatorGroupJpaEntity group = requireGroupForMutation(actor, groupId, ActionCode.GROUP_GRANT_REVOKE);
        OperatorGroupGrantJpaEntity grant = groupGrants.findByGrantId(grantId)
                .filter(g -> g.getGroupId().equals(group.getId()))
                .orElseThrow(() -> new GroupGrantNotFoundException(
                        "Grant '" + grantId + "' not found in group '" + groupId + "'"));
        if (grant.isRole()) {
            // 784 S4 (owner decision 2026-10-11 UTC): revoking the group's role (e.g. TENANT_ADMIN) must leave the
            // group's tenant an administrator.
            Long groupInternalId = group.getId();
            Long roleId = grant.getRoleId();
            lastAdminGuard.requireAdminRemains(group.getTenantId(),
                    row -> groupInternalId.equals(row.groupOrigin()) && roleId != null && roleId == row.roleId(),
                    null);
        }
        int keptAsDirect = fanOut.revokeGrantFanOut(group.getId(), grant, resolveMembers(group));
        groupGrants.delete(grant);
        audit(ActionCode.GROUP_GRANT_REVOKE, group.getGroupId(), group.getTenantId(), actor, reason,
                "grant=" + grantId + (keptAsDirect > 0 ? "," + keptAsDirectDetail(keptAsDirect) : ""));
    }

    /**
     * TASK-BE-633 AC-3 — the group's fan-out memberships that were kept as DIRECT memberships because the person
     * still holds a role in that company (784 D-1). The source change is never silent: it is in the audit detail,
     * same key as «이 회사에서 내보내기» ({@code keptAsDirect=}).
     */
    private static String keptAsDirectDetail(int keptAsDirect) {
        return "keptAsDirect=" + keptAsDirect;
    }

    // ==== Guards / helpers =====================================================

    /** Load a group for a mutation: 404 if absent, then the D3 TenantScopeGuard (403 + DENIED audit). */
    private OperatorGroupJpaEntity requireGroupForMutation(OperatorContext actor, String groupId,
                                                           ActionCode actionCode) {
        OperatorGroupJpaEntity group = requireGroup(groupId);
        tenantScopeGuard.requireTenantInScope(actor, Permission.GROUP_MANAGE, group.getTenantId(), actionCode);
        return group;
    }

    /** Single-group read: enumeration-safe — an out-of-scope group is 404, never a 403 leak. */
    private OperatorGroupJpaEntity requireGroupReadableEnumSafe(OperatorContext actor, String groupId) {
        OperatorGroupJpaEntity group = requireGroup(groupId);
        if (!grantScopeEvaluator.isTenantInAdminScope(actorId(actor), Permission.GROUP_MANAGE, group.getTenantId())) {
            throw new GroupNotFoundException("Group not found: " + groupId);
        }
        return group;
    }

    /** Sub-resource read (members / grants): 404 if absent, 403 TENANT_SCOPE_DENIED if out of scope. */
    private OperatorGroupJpaEntity requireGroupReadable403(OperatorContext actor, String groupId) {
        OperatorGroupJpaEntity group = requireGroup(groupId);
        if (!grantScopeEvaluator.isTenantInAdminScope(actorId(actor), Permission.GROUP_MANAGE, group.getTenantId())) {
            throw new TenantScopeDeniedException(
                    "Operator is not scoped to group tenant '" + group.getTenantId() + "'");
        }
        return group;
    }

    private OperatorGroupJpaEntity requireGroup(String groupId) {
        return operatorGroups.findByGroupId(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Group not found: " + groupId));
    }

    private void requireGrantsWithinActorHoldings(OperatorContext actor,
                                                  List<OperatorGroupGrantJpaEntity> grants,
                                                  ActionCode actionCode,
                                                  String groupTenantId) {
        List<AdminOperatorPort.RoleView> roleViews = new ArrayList<>();
        for (OperatorGroupGrantJpaEntity grant : grants) {
            if (grant.isRole()) {
                AdminRoleJpaEntity role = adminRoles.findById(grant.getRoleId())
                        .orElseThrow(() -> new RoleNotFoundException("Role not found: id=" + grant.getRoleId()));
                roleViews.add(roleView(role));
            } else if (!grantScopeEvaluator.isTenantInAdminScope(
                    actorId(actor), Permission.OPERATOR_MANAGE, grant.getTenantId())) {
                throw new GroupGrantNoEscalationException(
                        "Actor may not fan out an assignment to tenant '" + grant.getTenantId()
                                + "' (outside its operator.manage scope)");
            }
        }
        // 784 S3 (D-6): ≤-own in the group's tenant — where the fanned-out rows live.
        roleGrantGuard.requireGrantable(actor, roleViews, actionCode, groupTenantId);
    }

    /** 784 S4 (D-11): fan-out rows of a group live in the GROUP's tenant, whatever the member's home. */
    private List<GroupFanOutService.Member> resolveMembers(OperatorGroupJpaEntity group) {
        List<GroupFanOutService.Member> out = new ArrayList<>();
        for (OperatorGroupMemberJpaEntity m : groupMembers.findByGroupId(group.getId())) {
            adminOperators.findById(m.getOperatorId()).ifPresent(op ->
                    out.add(new GroupFanOutService.Member(op.getId(), op.getOperatorId(), group.getTenantId())));
        }
        return out;
    }

    private String validateName(String name) {
        String trimmed = name == null ? null : name.trim();
        if (trimmed == null || trimmed.isEmpty() || trimmed.length() > 120) {
            throw new IllegalArgumentException("Group name must be 1..120 characters");
        }
        return trimmed;
    }

    private void requireConcreteTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank() || AdminOperator.PLATFORM_TENANT_ID.equals(tenantId)) {
            throw new IllegalArgumentException("tenantId is required and may not be the platform sentinel '*'");
        }
    }

    private AdminOperatorPort.RoleView roleView(AdminRoleJpaEntity role) {
        return new AdminOperatorPort.RoleView(role.getId(), role.getName(),
                role.getDescription(), role.isRequire2fa());
    }

    private GrantView toGrantView(OperatorGroupGrantJpaEntity g) {
        if (g.isRole()) {
            String roleName = adminRoles.findById(g.getRoleId())
                    .map(AdminRoleJpaEntity::getName).orElse(null);
            return new GrantView(g.getGrantId(), OperatorGroupGrantJpaEntity.TYPE_ROLE, roleName, null, g.getGrantedAt());
        }
        return new GrantView(g.getGrantId(), OperatorGroupGrantJpaEntity.TYPE_TENANT_ASSIGNMENT,
                null, g.getTenantId(), g.getGrantedAt());
    }

    private String actorId(OperatorContext actor) {
        return actor == null ? null : actor.operatorId();
    }

    private Long actorInternalId(OperatorContext actor) {
        if (actor == null) {
            return null;
        }
        return adminOperators.findByOperatorId(actor.operatorId())
                .map(AdminOperatorJpaEntity::getId)
                .orElse(null);
    }

    private GroupView toView(OperatorGroupJpaEntity g) {
        return toView(g, groupMembers.countByGroupId(g.getId()), groupGrants.countByGroupId(g.getId()));
    }

    private GroupView toView(OperatorGroupJpaEntity g, long memberCount, long grantCount) {
        return new GroupView(g.getGroupId(), g.getTenantId(), g.getName(), g.getDescription(),
                memberCount, grantCount, g.getCreatedAt(), g.getUpdatedAt());
    }

    private GroupPage toPage(Page<OperatorGroupJpaEntity> page) {
        List<GroupView> items = page.getContent().stream().map(this::toView).toList();
        return new GroupPage(items, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    private void audit(ActionCode code, String groupId, String tenantId, OperatorContext actor,
                       String reason, String detail) {
        String auditId = auditor.newAuditId();
        Instant now = Instant.now();
        auditor.record(new AdminActionAuditor.AuditRecord(
                auditId,
                code,
                actor,
                "GROUP",
                groupId,
                AuditReasons.normalize(reason),
                null,
                "group:" + auditId,
                Outcome.SUCCESS,
                detail,
                now,
                now,
                tenantId));
    }

    // ==== Views ================================================================

    public record GroupView(String groupId, String tenantId, String name, String description,
                            long memberCount, long grantCount, Instant createdAt, Instant updatedAt) {}

    public record GroupPage(List<GroupView> items, int page, int size,
                            long totalElements, int totalPages) {}

    public record MemberView(String operatorId, String displayName, Instant addedAt) {}

    public record MemberAddResult(String operatorId, String displayName, Instant addedAt, int fannedOutGrants) {}

    public record GrantView(String grantId, String type, String roleName, String tenantId, Instant grantedAt) {}

    public record GrantAddResult(List<GrantView> items, int fannedOutRows) {}
}
