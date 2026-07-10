package com.example.account.application.service;

import com.example.account.application.exception.OrgNodeNotFoundException;
import com.example.account.domain.orgnode.EntitlementCeiling;
import com.example.account.domain.orgnode.OrgNode;
import com.example.account.domain.orgnode.OrgNodeCeilingNotSubsetException;
import com.example.account.domain.orgnode.OrgNodeCycleException;
import com.example.account.domain.orgnode.OrgNodeDepthExceededException;
import com.example.account.domain.orgnode.OrgNodeId;
import com.example.account.domain.orgnode.OrgNodeNotEmptyException;
import com.example.account.domain.repository.OrgNodeRepository;
import com.example.account.domain.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * TASK-BE-491 (ADR-MONO-047 § D1/D2/D4): the write side of the org-node tree.
 *
 * <p>account-service owns {@code tenants}, therefore it owns {@code org_node} and every
 * write invariant. admin-service is a thin command gateway (it authorizes, audits, and
 * forwards); it must never re-implement these rules.
 *
 * <ul>
 *   <li><b>I1</b> no self-parent, no cycle.</li>
 *   <li><b>I2</b> {@code depth = parent.depth + 1}, root = 1, {@code depth ≤ MAX_DEPTH} —
 *       enforced on create <b>and</b> re-parent, since a deep chain can otherwise be
 *       assembled by moving subtrees.</li>
 *   <li><b>I3</b> {@code child.ceiling ⊆ parent.ceiling} — on create, set-ceiling, and
 *       re-parent (the latter must check every descendant).</li>
 *   <li><b>I4</b> a node with children or tenants may not be deleted.</li>
 *   <li><b>I5</b> name 1..100 chars (enforced by the aggregate).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class OrgNodeCommandUseCase {

    private final OrgNodeRepository orgNodeRepository;
    private final TenantRepository tenantRepository;
    private final OrgNodeQueryUseCase queryUseCase;

    /**
     * Creates a node. {@code parentId == null} ⟹ a root node (depth 1).
     *
     * @throws OrgNodeNotFoundException          the parent does not exist
     * @throws OrgNodeDepthExceededException     the parent already sits at MAX_DEPTH
     * @throws OrgNodeCeilingNotSubsetException  the new ceiling is wider than the parent's
     */
    @Transactional
    public OrgNode create(String name, OrgNodeId parentId, EntitlementCeiling ceiling) {
        Instant now = Instant.now();
        EntitlementCeiling effective = (ceiling == null) ? EntitlementCeiling.unbounded() : ceiling;

        int depth = 1;
        if (parentId != null) {
            OrgNode parent = requireNode(parentId);
            depth = parent.getDepth() + 1;
            if (depth > OrgNode.MAX_DEPTH) {
                throw new OrgNodeDepthExceededException(depth, OrgNode.MAX_DEPTH);
            }
            // I3 against the parent's EFFECTIVE ceiling (the chain intersection), not merely
            // its own row: an ancestor may bound the parent more tightly than the parent's
            // own ceiling column suggests.
            if (!effective.isSubsetOf(queryUseCase.effectiveCeiling(parentId))) {
                throw new OrgNodeCeilingNotSubsetException("<new>", parentId.value());
            }
        }

        OrgNode node = OrgNode.create(OrgNodeId.generate(), parentId, name, effective, depth, now);
        return orgNodeRepository.save(node);
    }

    @Transactional
    public OrgNode rename(OrgNodeId id, String newName) {
        OrgNode node = requireNode(id);
        node.rename(newName, Instant.now());
        return orgNodeRepository.save(node);
    }

    /**
     * Replaces a node's ceiling.
     *
     * <p>Both directions are checked: the new ceiling must fit inside the parent chain's
     * effective ceiling, and every direct child's ceiling must still fit inside the new
     * one. Narrowing a node whose child is wider would otherwise leave the tree in a state
     * that violates I3 for a node the caller never named.
     */
    @Transactional
    public OrgNode setCeiling(OrgNodeId id, EntitlementCeiling newCeiling) {
        OrgNode node = requireNode(id);
        EntitlementCeiling effective = (newCeiling == null) ? EntitlementCeiling.unbounded() : newCeiling;

        if (node.getParentId() != null
                && !effective.isSubsetOf(queryUseCase.effectiveCeiling(node.getParentId()))) {
            throw new OrgNodeCeilingNotSubsetException(id.value(), node.getParentId().value());
        }
        for (OrgNode child : orgNodeRepository.findByParentId(id)) {
            if (!child.getCeiling().isSubsetOf(effective)) {
                throw new OrgNodeCeilingNotSubsetException(child.getId().value(), id.value());
            }
        }

        node.setCeiling(effective, Instant.now());
        return orgNodeRepository.save(node);
    }

    /**
     * Moves a subtree under a new parent ({@code newParentId == null} promotes it to root).
     *
     * <p>The three ways this goes wrong, all checked:
     * <ol>
     *   <li><b>Cycle</b> — the new parent is the node itself or one of its descendants.</li>
     *   <li><b>Depth</b> — the deepest descendant, not the moved node, is what may breach
     *       the cap.</li>
     *   <li><b>Ceiling</b> — every node in the moved subtree must still be a subset of the
     *       new ancestor chain, not just the moved node.</li>
     * </ol>
     */
    @Transactional
    public OrgNode reparent(OrgNodeId id, OrgNodeId newParentId) {
        OrgNode node = requireNode(id);

        if (newParentId == null) {
            return applyReparent(node, null, 1);
        }
        if (id.equals(newParentId)) {
            throw new OrgNodeCycleException(id.value(), newParentId.value());
        }
        requireNode(newParentId);

        // (1) cycle — the new parent must not live inside the subtree being moved.
        List<OrgNodeId> subtree = queryUseCase.subtreeNodeIds(id);
        if (subtree.contains(newParentId)) {
            throw new OrgNodeCycleException(id.value(), newParentId.value());
        }

        OrgNode newParent = requireNode(newParentId);
        int newDepth = newParent.getDepth() + 1;
        int shift = newDepth - node.getDepth();

        // (2) depth — the DEEPEST descendant decides, not the moved node.
        int deepest = subtree.stream()
                .map(this::requireNode)
                .mapToInt(OrgNode::getDepth)
                .max()
                .orElse(node.getDepth());
        if (deepest + shift > OrgNode.MAX_DEPTH) {
            throw new OrgNodeDepthExceededException(deepest + shift, OrgNode.MAX_DEPTH);
        }

        // (3) ceiling — every moved node must still be a subset of the new ancestor chain.
        EntitlementCeiling newParentEffective = queryUseCase.effectiveCeiling(newParentId);
        for (OrgNodeId descendantId : subtree) {
            OrgNode descendant = requireNode(descendantId);
            if (!descendant.getCeiling().isSubsetOf(newParentEffective)) {
                throw new OrgNodeCeilingNotSubsetException(descendantId.value(), newParentId.value());
            }
        }

        return applyReparent(node, newParentId, newDepth);
    }

    /**
     * Deletes an empty node (I4). A node holding children or tenants is refused rather than
     * cascaded: cascading would orphan the FK and silently strand service-tenants.
     */
    @Transactional
    public void delete(OrgNodeId id) {
        requireNode(id);
        boolean hasChildren = orgNodeRepository.hasChildren(id);
        long tenantCount = tenantRepository.countByOrgNodeId(id);
        if (hasChildren || tenantCount > 0) {
            throw new OrgNodeNotEmptyException(id.value(), hasChildren ? 1 : 0, tenantCount);
        }
        orgNodeRepository.deleteById(id);
    }

    // -- internals ------------------------------------------------------------------

    /**
     * Applies a validated move, shifting the whole subtree's depth by the same delta so the
     * stored {@code depth} column stays consistent with the tree.
     */
    private OrgNode applyReparent(OrgNode node, OrgNodeId newParentId, int newDepth) {
        Instant now = Instant.now();
        int shift = newDepth - node.getDepth();
        node.reparent(newParentId, newDepth, now);
        OrgNode saved = orgNodeRepository.save(node);

        if (shift != 0) {
            Deque<OrgNodeId> queue = new ArrayDeque<>();
            queue.add(node.getId());
            while (!queue.isEmpty()) {
                for (OrgNode child : orgNodeRepository.findByParentId(queue.removeFirst())) {
                    child.relocateDepth(child.getDepth() + shift, now);
                    orgNodeRepository.save(child);
                    queue.addLast(child.getId());
                }
            }
        }
        return saved;
    }

    private OrgNode requireNode(OrgNodeId id) {
        return orgNodeRepository.findById(id)
                .orElseThrow(() -> new OrgNodeNotFoundException(id.value()));
    }
}
