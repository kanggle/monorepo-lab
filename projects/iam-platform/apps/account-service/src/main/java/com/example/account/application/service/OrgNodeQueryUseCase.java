package com.example.account.application.service;

import com.example.account.application.exception.OrgNodeNotFoundException;
import com.example.account.application.exception.TenantNotFoundException;
import com.example.account.domain.orgnode.EntitlementCeiling;
import com.example.account.domain.orgnode.OrgNode;
import com.example.account.domain.orgnode.OrgNodeId;
import com.example.account.domain.repository.OrgNodeRepository;
import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.tenant.Tenant;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * TASK-BE-491 (ADR-MONO-047 § D2/D5/D6): read side of the org-node tree — chain resolution,
 * effective-ceiling intersection, and subtree expansion.
 *
 * <p>Read-only; no audit row (consistent with the other tenant read paths).
 */
@Service
@RequiredArgsConstructor
public class OrgNodeQueryUseCase {

    private final OrgNodeRepository orgNodeRepository;
    private final TenantRepository tenantRepository;

    @Transactional(readOnly = true)
    public List<OrgNode> tree() {
        return orgNodeRepository.findAll();
    }

    @Transactional(readOnly = true)
    public OrgNode get(OrgNodeId id) {
        return orgNodeRepository.findById(id)
                .orElseThrow(() -> new OrgNodeNotFoundException(id.value()));
    }

    /**
     * The ancestor chain {@code root → … → node}, root first.
     *
     * <p>Bounded by {@link OrgNode#MAX_DEPTH}; a malformed chain (a cycle that somehow
     * bypassed the write guards) would otherwise loop forever, so the walk is hard-capped
     * and fails loudly rather than spinning.
     */
    @Transactional(readOnly = true)
    public List<OrgNode> chain(OrgNodeId id) {
        Deque<OrgNode> reversed = new ArrayDeque<>();
        OrgNodeId cursor = id;
        int guard = 0;
        while (cursor != null) {
            if (++guard > OrgNode.MAX_DEPTH) {
                throw new IllegalStateException(
                        "org node ancestor chain exceeds MAX_DEPTH starting at " + id.value()
                                + " — the tree is corrupt (a cycle bypassed the write guards)");
            }
            OrgNode node = orgNodeRepository.findById(cursor)
                    .orElseThrow(() -> new OrgNodeNotFoundException(id.value()));
            reversed.addFirst(node);
            cursor = node.getParentId();
        }
        return new ArrayList<>(reversed);
    }

    /**
     * {@code effectiveCeiling(node)} = the intersection of every ceiling on the chain
     * {@code root → … → node} (D2-A).
     *
     * <p>The {@code child ⊆ parent} write invariant (I3) already makes this equal to the
     * node's own ceiling, but the intersection is computed anyway: it is the definition,
     * it is cheap (depth ≤ 5), and it stays correct even if a row were hand-edited.
     */
    @Transactional(readOnly = true)
    public EntitlementCeiling effectiveCeiling(OrgNodeId id) {
        EntitlementCeiling acc = EntitlementCeiling.unbounded();
        for (OrgNode node : chain(id)) {
            acc = acc.intersect(node.getCeiling());
        }
        return acc;
    }

    /**
     * {@code effectiveCeiling(tenant)} — the ceiling a tenant is actually bound by.
     *
     * <p><b>An ungrouped tenant ({@code org_node_id = NULL}) is UNBOUNDED, not empty</b>
     * (D7). This single branch is what makes ADR-047 a behavioural no-op for every
     * pre-existing row and keeps a lazy migration legal.
     */
    @Transactional(readOnly = true)
    public EntitlementCeiling effectiveCeilingForTenant(String tenantId) {
        Tenant tenant = tenantRepository.findById(new TenantId(tenantId))
                .orElseThrow(() -> new TenantNotFoundException(tenantId));
        if (tenant.getOrgNodeId() == null) {
            return EntitlementCeiling.unbounded();
        }
        return effectiveCeiling(tenant.getOrgNodeId());
    }

    /** Every node in the subtree rooted at {@code id}, including {@code id} itself. */
    @Transactional(readOnly = true)
    public List<OrgNodeId> subtreeNodeIds(OrgNodeId id) {
        if (orgNodeRepository.findById(id).isEmpty()) {
            throw new OrgNodeNotFoundException(id.value());
        }
        Set<OrgNodeId> visited = new LinkedHashSet<>();
        Deque<OrgNodeId> queue = new ArrayDeque<>();
        queue.add(id);
        while (!queue.isEmpty()) {
            OrgNodeId current = queue.removeFirst();
            // `visited` doubles as the cycle guard: a corrupt tree cannot spin this loop.
            if (!visited.add(current)) {
                continue;
            }
            for (OrgNode child : orgNodeRepository.findByParentId(current)) {
                queue.addLast(child.getId());
            }
        }
        return new ArrayList<>(visited);
    }

    /**
     * The tenant ids under {@code id}'s subtree (self + descendants) — the expansion
     * admin-service's {@code AdminGrantScopeEvaluator} needs to resolve an
     * {@code ORG_ADMIN @ node} grant into an effective admin scope (D5).
     */
    @Transactional(readOnly = true)
    public List<String> subtreeTenantIds(OrgNodeId id) {
        List<String> nodeIds = subtreeNodeIds(id).stream().map(OrgNodeId::value).toList();
        return tenantRepository.findTenantIdsByOrgNodeIdIn(nodeIds);
    }
}
