package com.example.account.domain.orgnode;

import lombok.Getter;

import java.time.Instant;

/**
 * TASK-BE-491 (ADR-MONO-047 § D1): a data-less grouping node above {@code tenant}.
 *
 * <p>A company is an {@code org_node}; a service is a {@code tenant}. The tree
 * <b>groups</b> tenants — it never <b>nests</b> them, so {@code tenant_id} remains the
 * single flat isolation key (M1) and every row-isolation guard is byte-unchanged. Nodes
 * carry policy ({@link EntitlementCeiling}) and structure only; never rows, never data,
 * never isolation.
 *
 * <p>Invariants I1–I5 span more than one node (a cycle check needs the ancestor chain, a
 * subset check needs the parent), so they are enforced by {@code OrgNodeCommandUseCase}
 * with repository access. This aggregate enforces only what it can see locally: a
 * non-blank bounded name and the depth cap.
 */
@Getter
public class OrgNode {

    /** D4: root = depth 1; cap 5 (AWS OU parity). */
    public static final int MAX_DEPTH = 5;

    private static final int NAME_MAX = 100;

    private OrgNodeId id;
    /** {@code null} for a root node. */
    private OrgNodeId parentId;
    private String name;
    private EntitlementCeiling ceiling;
    private int depth;
    private Instant createdAt;
    private Instant updatedAt;

    private OrgNode() {
    }

    /** Factory for a brand-new node. {@code parentId == null} ⟹ a root (depth 1). */
    public static OrgNode create(OrgNodeId id, OrgNodeId parentId, String name,
                                 EntitlementCeiling ceiling, int depth, Instant now) {
        OrgNode node = new OrgNode();
        node.id = id;
        node.parentId = parentId;
        node.name = validateName(name);
        node.ceiling = (ceiling == null) ? EntitlementCeiling.unbounded() : ceiling;
        node.depth = validateDepth(depth);
        node.createdAt = now;
        node.updatedAt = now;
        return node;
    }

    /** Factory used by infrastructure mappers when reconstituting from persistence. */
    public static OrgNode reconstitute(OrgNodeId id, OrgNodeId parentId, String name,
                                       EntitlementCeiling ceiling, int depth,
                                       Instant createdAt, Instant updatedAt) {
        OrgNode node = new OrgNode();
        node.id = id;
        node.parentId = parentId;
        node.name = name;
        node.ceiling = ceiling;
        node.depth = depth;
        node.createdAt = createdAt;
        node.updatedAt = updatedAt;
        return node;
    }

    public boolean isRoot() {
        return parentId == null;
    }

    public void rename(String newName, Instant now) {
        this.name = validateName(newName);
        this.updatedAt = now;
    }

    /**
     * Re-parents this node. The caller ({@code OrgNodeCommandUseCase}) has already
     * verified that the move introduces no cycle, that the new depth keeps this node
     * <i>and every descendant</i> within {@link #MAX_DEPTH}, and that this node's ceiling
     * (and every descendant's) stays a subset of the new ancestor chain.
     */
    public void reparent(OrgNodeId newParentId, int newDepth, Instant now) {
        this.parentId = newParentId;
        this.depth = validateDepth(newDepth);
        this.updatedAt = now;
    }

    /**
     * Replaces this node's ceiling. The caller has already verified
     * {@code newCeiling ⊆ parent.effectiveCeiling} and
     * {@code child.ceiling ⊆ newCeiling} for every child (D2/I3).
     */
    public void setCeiling(EntitlementCeiling newCeiling, Instant now) {
        this.ceiling = (newCeiling == null) ? EntitlementCeiling.unbounded() : newCeiling;
        this.updatedAt = now;
    }

    /** Used when a re-parent shifts a whole subtree's depth. */
    public void relocateDepth(int newDepth, Instant now) {
        this.depth = validateDepth(newDepth);
        this.updatedAt = now;
    }

    private static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("org node name must not be blank");
        }
        String trimmed = name.trim();
        if (trimmed.length() > NAME_MAX) {
            throw new IllegalArgumentException("org node name must not exceed " + NAME_MAX + " characters");
        }
        return trimmed;
    }

    private static int validateDepth(int depth) {
        if (depth < 1 || depth > MAX_DEPTH) {
            throw new OrgNodeDepthExceededException(depth, MAX_DEPTH);
        }
        return depth;
    }
}
