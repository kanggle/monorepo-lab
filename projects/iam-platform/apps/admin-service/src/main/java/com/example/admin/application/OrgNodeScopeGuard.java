package com.example.admin.application;

import com.example.admin.application.exception.OrgNodeNotFoundException;
import com.example.admin.application.exception.OrgNodeSelfCeilingDeniedException;
import com.example.admin.application.orgnode.OrgNodeView;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.persistence.rbac.AdminGrantScopeEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TASK-BE-492 (ADR-MONO-047 D5) — the <b>fourth confinement axis</b>: mutations of the
 * org-node tree itself (node CRUD, ceiling edits, {@code ORG_ADMIN} grant/revoke). Distinct
 * from the D2 {@link TenantScopeGuard} (administration surface), the BE-467 account-data
 * axis, and the ADR-045 cross-org axis.
 *
 * <p>Two predicates, both derived from the actor's node-scoped {@code org.manage} grants:
 * <pre>
 *   administers(actor, N)         = SUPER_ADMIN, or ORG_ADMIN at some node in ancestors(N) ∪ {N}
 *   strictlyAdministers(actor, N) = SUPER_ADMIN, or ORG_ADMIN at a STRICT ancestor of N
 * </pre>
 *
 * <p><b>Why the strict variant exists.</b> An {@code ORG_ADMIN @ N} may not edit {@code N}'s
 * own ceiling, delete {@code N}, or re-parent it: those are the bounds on its own authority.
 * Exact AWS Organizations parity — you cannot detach, from inside an OU, the SCP attached to
 * that OU. Only a strict ancestor (or {@code SUPER_ADMIN}) may.
 *
 * <p><b>Cross-scope is 404, never 403.</b> A 403 confirms the existence of a node outside the
 * actor's subtree. The single exception is the self-ceiling case, which is 403
 * {@code ORG_NODE_SELF_CEILING_DENIED}: there the actor demonstrably administers the node, so
 * nothing is leaked by admitting it exists.
 *
 * <p>Reach is computed from the authority's <b>flat node list</b> (one round-trip), never by
 * expanding subtrees per node. The evaluator's node-grant lookup is pure DB.
 */
@Component
@RequiredArgsConstructor
public class OrgNodeScopeGuard {

    private final AdminGrantScopeEvaluator grantScopeEvaluator;
    private final AdminActionAuditor auditor;

    /**
     * The actor's org-node reach, resolved once per request from a flat node snapshot.
     *
     * @param platform     {@code true} for a platform actor ({@code SUPER_ADMIN}); reaches every node
     * @param grantNodeIds the nodes at which the actor holds {@code ORG_ADMIN}
     * @param nodesById    the flat snapshot, keyed by node id
     */
    public record Reach(boolean platform, Set<String> grantNodeIds, Map<String, OrgNodeView> nodesById) {

        /** {@code ancestors(N) ∪ {N}}, ordered N → root. Empty when {@code N} is unknown. */
        List<String> chainInclusive(String orgNodeId) {
            List<String> chain = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            String cursor = orgNodeId;
            // The authority forbids cycles and caps depth at 5; the visited-set is a belt-and-braces
            // guard so a corrupt snapshot can never spin this loop forever.
            while (cursor != null && seen.add(cursor)) {
                OrgNodeView node = nodesById.get(cursor);
                if (node == null) {
                    return List.of();
                }
                chain.add(cursor);
                cursor = node.parentId();
            }
            return chain;
        }

        boolean exists(String orgNodeId) {
            return orgNodeId != null && nodesById.containsKey(orgNodeId);
        }

        boolean administers(String orgNodeId) {
            if (platform) {
                return exists(orgNodeId);
            }
            List<String> chain = chainInclusive(orgNodeId);
            return chain.stream().anyMatch(grantNodeIds::contains);
        }

        boolean strictlyAdministers(String orgNodeId) {
            if (platform) {
                return exists(orgNodeId);
            }
            List<String> chain = chainInclusive(orgNodeId);
            // Drop the node itself: only a STRICT ancestor qualifies.
            return chain.stream().skip(1).anyMatch(grantNodeIds::contains);
        }

        /** The nodes visible to the actor: everything for a platform actor, else the union of its grant subtrees. */
        public List<OrgNodeView> visibleNodes() {
            if (platform) {
                return List.copyOf(nodesById.values());
            }
            Map<String, List<String>> childrenByParent = new HashMap<>();
            for (OrgNodeView node : nodesById.values()) {
                childrenByParent.computeIfAbsent(node.parentId(), k -> new ArrayList<>()).add(node.orgNodeId());
            }
            Set<String> visible = new LinkedHashSet<>();
            Deque<String> queue = new ArrayDeque<>(grantNodeIds);
            while (!queue.isEmpty()) {
                String id = queue.poll();
                if (!nodesById.containsKey(id) || !visible.add(id)) {
                    continue;
                }
                queue.addAll(childrenByParent.getOrDefault(id, List.of()));
            }
            return visible.stream().map(nodesById::get).toList();
        }
    }

    /** Resolves the actor's reach against a flat node snapshot (one authority round-trip). */
    public Reach resolveReach(OperatorContext actor, List<OrgNodeView> allNodes) {
        String actorId = actor == null ? null : actor.operatorId();
        Map<String, OrgNodeView> byId = new HashMap<>();
        for (OrgNodeView node : allNodes) {
            byId.put(node.orgNodeId(), node);
        }
        return new Reach(
                grantScopeEvaluator.isPlatformScope(actorId, Permission.ORG_MANAGE),
                grantScopeEvaluator.grantedOrgNodeIds(actorId, Permission.ORG_MANAGE),
                Map.copyOf(byId));
    }

    /**
     * ROOT node creation ({@code parentId = null}) is {@code SUPER_ADMIN}-only. An
     * {@code ORG_ADMIN} has no ancestor to authorize it, so there is nothing to confine
     * against — the platform is the only principal above a root.
     */
    public void requirePlatformForRoot(OperatorContext actor, Reach reach) {
        if (!reach.platform()) {
            denyAudited(actor, ActionCode.ORG_NODE_CREATE, null);
            throw new com.example.admin.application.exception.PermissionDeniedException(
                    "Only SUPER_ADMIN (platform scope) may create a ROOT org-node");
        }
    }

    /** @throws OrgNodeNotFoundException when the node is unknown OR outside the actor's reach */
    public void requireAdministers(OperatorContext actor, Reach reach, String orgNodeId, ActionCode actionCode) {
        if (reach.administers(orgNodeId)) {
            return;
        }
        denyAudited(actor, actionCode, orgNodeId);
        throw new OrgNodeNotFoundException("Org node not found: " + orgNodeId);
    }

    /**
     * The strict variant, with the two outcomes kept distinct:
     * <ul>
     *   <li>the actor does not administer {@code N} at all → 404 (existence not leaked);</li>
     *   <li>the actor administers {@code N} but only <em>at</em> {@code N} → 403
     *       {@code ORG_NODE_SELF_CEILING_DENIED} (self-escalation).</li>
     * </ul>
     */
    public void requireStrictlyAdministers(OperatorContext actor, Reach reach, String orgNodeId,
                                           ActionCode actionCode) {
        if (reach.strictlyAdministers(orgNodeId)) {
            return;
        }
        denyAudited(actor, actionCode, orgNodeId);
        if (reach.administers(orgNodeId)) {
            throw new OrgNodeSelfCeilingDeniedException(
                    "An ORG_ADMIN may not alter the bounds of its own node '" + orgNodeId
                            + "'; only a strict ancestor or SUPER_ADMIN may");
        }
        throw new OrgNodeNotFoundException("Org node not found: " + orgNodeId);
    }

    /** Best-effort DENIED {@code admin_actions} row; the denial itself always stands (A10 override). */
    private void denyAudited(OperatorContext actor, ActionCode actionCode, String orgNodeId) {
        if (actor == null) {
            return;
        }
        auditor.recordOrgNodeScopeDenied(actor, actionCode, orgNodeId);
    }
}
