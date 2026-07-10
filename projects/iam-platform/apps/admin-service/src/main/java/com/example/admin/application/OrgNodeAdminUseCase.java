package com.example.admin.application;

import com.example.admin.application.exception.OrgAdminGrantOutOfCeilingException;
import com.example.admin.application.exception.OrgNodeNotFoundException;
import com.example.admin.application.exception.RoleNotFoundException;
import com.example.admin.application.orgnode.CeilingView;
import com.example.admin.application.orgnode.OrgNodeView;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.OrgNodePort;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorRoleJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorRoleJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.AdminRoleJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminRoleJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.OrgNodeSubtreeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * TASK-BE-492 (ADR-MONO-047 D5) — the org-node command gateway.
 *
 * <p>admin-service <b>does not store the tree</b>. It authorizes (the fourth confinement
 * axis, {@link OrgNodeScopeGuard}), caps the grant (ADR-024 D3 {@link RoleGrantGuard},
 * <em>reused</em> not reinvented, plus the node's effective ceiling), audits, and forwards
 * to account-service, which owns {@code tenants} and therefore {@code org_node} (D6). The
 * cycle / depth / {@code child ⊆ parent} invariants are enforced there and their 422 is
 * passed through — a duplicate check here would inevitably drift from the enforcing one.
 *
 * <p>The one piece of state admin-service does own is the <b>grant row</b>
 * ({@code admin_operator_roles.org_node_id}, V0042) — that lives in the IAM plane.
 *
 * <p><b>No {@code @Transactional} anywhere here.</b> Every method makes at least one remote
 * call (reach resolution reads the flat node list; the grant cap reads the effective
 * ceiling), and a remote call must never hold a DB transaction open across network I/O —
 * same reasoning as {@link ManageSubscriptionUseCase}. The grant/revoke row writes are
 * single-statement repository calls, each atomic in its own implicit transaction, and the
 * auditor manages its own {@code REQUIRES_NEW} persistence.
 */
@Service
@RequiredArgsConstructor
public class OrgNodeAdminUseCase {

    /**
     * The node-scoped role. Only this role is grantable at a node in v1 — a tenant-scoped
     * role such as {@code TENANT_ADMIN} carries no meaning at a grouping node.
     */
    public static final String ORG_ADMIN_ROLE = "ORG_ADMIN";

    private final OrgNodePort orgNodePort;
    private final OrgNodeScopeGuard orgNodeScopeGuard;
    private final RoleGrantGuard roleGrantGuard;
    private final OrgNodeSubtreeResolver subtreeResolver;
    private final AdminActionAuditor auditor;
    private final AdminOperatorJpaRepository operators;
    private final AdminRoleJpaRepository roles;
    private final AdminOperatorRoleJpaRepository operatorRoles;

    // ---- Reads (no audit row — BE-486 read-path convention) --------------------

    /** The nodes the actor may see: everything for a platform actor, else its grant subtrees. */
    public List<OrgNodeView> listNodes(OperatorContext actor) {
        return reach(actor).visibleNodes();
    }

    public OrgNodeView getNode(OperatorContext actor, String orgNodeId) {
        OrgNodeScopeGuard.Reach reach = reach(actor);
        orgNodeScopeGuard.requireAdministers(actor, reach, orgNodeId, ActionCode.ORG_NODE_UPDATE);
        return orgNodePort.get(orgNodeId);
    }

    public List<String> listSubtreeTenants(OperatorContext actor, String orgNodeId) {
        OrgNodeScopeGuard.Reach reach = reach(actor);
        orgNodeScopeGuard.requireAdministers(actor, reach, orgNodeId, ActionCode.ORG_NODE_UPDATE);
        return orgNodePort.subtreeTenantIds(orgNodeId);
    }

    public List<OrgAdminGrant> listNodeAdmins(OperatorContext actor, String orgNodeId) {
        OrgNodeScopeGuard.Reach reach = reach(actor);
        orgNodeScopeGuard.requireAdministers(actor, reach, orgNodeId, ActionCode.ORG_ADMIN_GRANT);
        AdminRoleJpaEntity orgAdmin = requireRole(ORG_ADMIN_ROLE);
        return operatorRoles.findByOrgNodeId(orgNodeId).stream()
                .filter(row -> row.getRoleId().equals(orgAdmin.getId()))
                .map(row -> new OrgAdminGrant(
                        externalOperatorId(row.getOperatorId()), ORG_ADMIN_ROLE, row.getGrantedAt()))
                .toList();
    }

    // ---- Tree mutations (delegated; audited on success) -------------------------

    public OrgNodeView createNode(OperatorContext actor, String name, String parentId,
                                  CeilingView ceiling, String reason) {
        OrgNodeScopeGuard.Reach reach = reach(actor);
        if (parentId == null) {
            orgNodeScopeGuard.requirePlatformForRoot(actor, reach);
        } else {
            orgNodeScopeGuard.requireAdministers(actor, reach, parentId, ActionCode.ORG_NODE_CREATE);
        }
        OrgNodeView created = orgNodePort.create(name, parentId, ceiling);
        audit(ActionCode.ORG_NODE_CREATE, created.orgNodeId(), actor, reason, null);
        return created;
    }

    /**
     * Rename requires {@code administers(N)}; a re-parent additionally requires
     * {@code strictlyAdministers(N)} (moving your own node is altering your own bounds) AND
     * {@code administers(newParent)} (you cannot fling a subtree somewhere you do not manage).
     */
    public OrgNodeView updateNode(OperatorContext actor, String orgNodeId, String name,
                                  String newParentId, String reason) {
        OrgNodeScopeGuard.Reach reach = reach(actor);
        if (newParentId == null) {
            orgNodeScopeGuard.requireAdministers(actor, reach, orgNodeId, ActionCode.ORG_NODE_UPDATE);
        } else {
            orgNodeScopeGuard.requireStrictlyAdministers(actor, reach, orgNodeId, ActionCode.ORG_NODE_UPDATE);
            orgNodeScopeGuard.requireAdministers(actor, reach, newParentId, ActionCode.ORG_NODE_UPDATE);
        }
        OrgNodeView updated = orgNodePort.update(orgNodeId, name, newParentId);
        audit(ActionCode.ORG_NODE_UPDATE, orgNodeId, actor, reason, null);
        return updated;
    }

    public void deleteNode(OperatorContext actor, String orgNodeId, String reason) {
        OrgNodeScopeGuard.Reach reach = reach(actor);
        orgNodeScopeGuard.requireStrictlyAdministers(actor, reach, orgNodeId, ActionCode.ORG_NODE_DELETE);
        orgNodePort.delete(orgNodeId);
        audit(ActionCode.ORG_NODE_DELETE, orgNodeId, actor, reason, null);
    }

    /**
     * {@code strictlyAdministers} — an {@code ORG_ADMIN @ N} may not edit {@code N}'s own
     * ceiling (403 {@code ORG_NODE_SELF_CEILING_DENIED}); that ceiling is the bound on its
     * own authority. AWS SCP-attach parity.
     */
    public OrgNodeView setCeiling(OperatorContext actor, String orgNodeId, CeilingView ceiling, String reason) {
        OrgNodeScopeGuard.Reach reach = reach(actor);
        orgNodeScopeGuard.requireStrictlyAdministers(actor, reach, orgNodeId, ActionCode.ORG_NODE_CEILING_SET);
        OrgNodeView updated = orgNodePort.setCeiling(orgNodeId, ceiling);
        audit(ActionCode.ORG_NODE_CEILING_SET, orgNodeId, actor, reason, null);
        return updated;
    }

    // ---- ORG_ADMIN grant / revoke (IAM plane — admin-service owns the row) ------

    /**
     * Grants a node-scoped role. Three caps, composed:
     * <ol>
     *   <li>{@code administers(actor, N)} — else 404 (existence not leaked);</li>
     *   <li>{@link RoleGrantGuard} — the ADR-024 D3 ≤-own rule, reused unchanged:
     *       {@code SUPER_ADMIN} is never mintable, and the actor may not grant a role whose
     *       permissions it does not itself hold (403);</li>
     *   <li>{@link #requireGrantWithinCeiling} — the node's effective ceiling (422).</li>
     * </ol>
     *
     * <p>The grant row's {@code tenant_id} mirrors the <b>target operator's own tenant</b>
     * (BE-289 WI-2 — the audit-routing column), and its {@code org_node_id} drives the scope.
     *
     * <p>{@code admin_operator_roles} is keyed by {@code (operator_id, role_id)}, so an
     * operator holds {@code ORG_ADMIN} at <b>at most one node</b>. Re-granting the same role
     * at a different node therefore <em>moves</em> the grant rather than adding a second one;
     * both are audited as {@code ORG_ADMIN_GRANT}. Widening this to multiple nodes would need
     * a PK change and is out of scope.
     */
    public OrgAdminGrant grantNodeAdmin(OperatorContext actor, String orgNodeId,
                                        String targetOperatorId, String roleName, String reason) {
        OrgNodeScopeGuard.Reach reach = reach(actor);
        orgNodeScopeGuard.requireAdministers(actor, reach, orgNodeId, ActionCode.ORG_ADMIN_GRANT);

        AdminOperatorJpaEntity target = operators.findByOperatorId(targetOperatorId)
                // Enumeration-safe: an unknown/unreachable operator is indistinguishable from
                // an unknown node on this surface (admin-api.md: 404 ORG_NODE_NOT_FOUND).
                .orElseThrow(() -> new OrgNodeNotFoundException(
                        "Org node grant target not found: " + targetOperatorId));

        AdminRoleJpaEntity role = requireRole(roleName);

        // (2) ADR-024 D3 no-escalation — reused verbatim, never re-implemented here.
        roleGrantGuard.requireGrantable(
                actor,
                List.of(new AdminOperatorPort.RoleView(
                        role.getId(), role.getName(), role.getDescription(), role.isRequire2fa())),
                ActionCode.ORG_ADMIN_GRANT);

        // (3) the node's ceiling caps the grant (fail-closed).
        requireGrantWithinCeiling(orgNodeId, roleName);

        Instant now = Instant.now();
        AdminOperatorRoleJpaEntity row = AdminOperatorRoleJpaEntity.createNodeScoped(
                target.getId(), role.getId(), now, actorInternalId(actor), target.getTenantId(), orgNodeId);
        operatorRoles.save(row);
        subtreeResolver.invalidateAll();

        audit(ActionCode.ORG_ADMIN_GRANT, orgNodeId, actor, reason,
                "granted_operator_id=" + targetOperatorId + " role=" + roleName);
        return new OrgAdminGrant(targetOperatorId, roleName, now);
    }

    /**
     * Revokes a node-scoped grant. A missing grant is a 404 (enumeration-safe: we do not
     * disclose whether the operator exists but holds nothing, or does not exist).
     */
    public void revokeNodeAdmin(OperatorContext actor, String orgNodeId, String targetOperatorId, String reason) {
        OrgNodeScopeGuard.Reach reach = reach(actor);
        orgNodeScopeGuard.requireAdministers(actor, reach, orgNodeId, ActionCode.ORG_ADMIN_REVOKE);

        AdminRoleJpaEntity role = requireRole(ORG_ADMIN_ROLE);
        AdminOperatorJpaEntity target = operators.findByOperatorId(targetOperatorId)
                .orElseThrow(() -> new OrgNodeNotFoundException(
                        "Org node grant not found for operator: " + targetOperatorId));

        AdminOperatorRoleJpaEntity row = operatorRoles
                .findByOperatorIdAndRoleIdAndOrgNodeId(target.getId(), role.getId(), orgNodeId)
                .orElseThrow(() -> new OrgNodeNotFoundException(
                        "Org node grant not found for operator: " + targetOperatorId));

        operatorRoles.delete(row);
        subtreeResolver.invalidateAll();
        audit(ActionCode.ORG_ADMIN_REVOKE, orgNodeId, actor, reason,
                "revoked_operator_id=" + targetOperatorId + " role=" + ORG_ADMIN_ROLE);
    }

    // ---- Internals -------------------------------------------------------------

    /**
     * The entitlement-plane cap on a node-scoped grant (ADR-047 D5 / ADR-023).
     *
     * <p>The rule is {@code domainFootprint(role) ⊆ effectiveCeiling(N)}, plus: a ceiling
     * that permits <em>nothing</em> ({@code BOUNDED([])}) admits no node administrator at
     * all. That second clause is what the internal contract's fail-closed note demands —
     * an unresolvable ceiling resolves to {@code BOUNDED([])} and must <b>deny</b> the grant,
     * never fall back to {@code UNBOUNDED}.
     *
     * <p><b>The footprint of every role currently in {@code admin_roles} is empty.</b> Those
     * are cross-domain admin-tier roles ({@code ORG_ADMIN}, {@code TENANT_ADMIN}, …); none
     * mints a domain entitlement, and {@code admin_role_permissions} carries no domain axis.
     * So the subset test is vacuously true today and the deny path is the empty-ceiling one.
     * This is deliberate: a per-role domain footprint would be a new decision (which domain
     * does an admin role "reach"?) that ADR-047 does not make. The cap is written so that
     * when a domain-scoped grantable role does arrive, the check is already in the right place
     * — and until then it can never over-grant, only refuse.
     */
    private void requireGrantWithinCeiling(String orgNodeId, String roleName) {
        CeilingView ceiling = subtreeResolver.effectiveCeilingFailClosed(orgNodeId);
        if (ceiling.permitsNothing()) {
            throw new OrgAdminGrantOutOfCeilingException(
                    "Org node '" + orgNodeId + "' permits no entitled domain (BOUNDED([]) — possibly "
                            + "because its effective ceiling could not be resolved); role '" + roleName
                            + "' may not be granted there");
        }
        // domainFootprint(role) ⊆ ceiling. Empty for every admin-tier role (see javadoc).
        for (String domainKey : domainFootprint(roleName)) {
            if (!ceiling.permits(domainKey)) {
                throw new OrgAdminGrantOutOfCeilingException(
                        "Role '" + roleName + "' reaches domain '" + domainKey
                                + "', which is outside the effective ceiling of org node '" + orgNodeId + "'");
            }
        }
    }

    /**
     * The domain keys a grantable admin role reaches. Empty for every role in
     * {@code admin_roles} — they are cross-domain by construction. Kept as a named seam so
     * the ceiling cap has an unambiguous input rather than an implied one.
     */
    private List<String> domainFootprint(String roleName) {
        return List.of();
    }

    private OrgNodeScopeGuard.Reach reach(OperatorContext actor) {
        return orgNodeScopeGuard.resolveReach(actor, orgNodePort.list());
    }

    private AdminRoleJpaEntity requireRole(String roleName) {
        return roles.findByName(roleName)
                .orElseThrow(() -> new RoleNotFoundException("Role not found: " + roleName));
    }

    private Long actorInternalId(OperatorContext actor) {
        if (actor == null) {
            return null;
        }
        return operators.findByOperatorId(actor.operatorId())
                .map(AdminOperatorJpaEntity::getId)
                .orElse(null);
    }

    private String externalOperatorId(Long internalId) {
        return operators.findById(internalId)
                .map(AdminOperatorJpaEntity::getOperatorId)
                .orElse(null);
    }

    private void audit(ActionCode code, String orgNodeId, OperatorContext actor,
                       String reason, String detail) {
        String auditId = auditor.newAuditId();
        Instant now = Instant.now();
        auditor.record(new AdminActionAuditor.AuditRecord(
                auditId,
                code,
                actor,
                "ORG_NODE",
                orgNodeId,
                AuditReasons.normalize(reason),
                null,
                "org-node:" + auditId,
                Outcome.SUCCESS,
                detail,
                now,
                Instant.now()));
    }

    /** A node-scoped role grant as the operator surface sees it. */
    public record OrgAdminGrant(String operatorId, String roleName, Instant grantedAt) {}
}
