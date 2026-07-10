package com.example.account.domain.orgnode;

/**
 * TASK-BE-491 (ADR-MONO-047 § D2, invariant I3): a write would leave some node's ceiling
 * wider than its parent's. Surfaces as 422 {@code ORG_NODE_CEILING_NOT_SUBSET}.
 *
 * <p>Checked on create-with-parent, set-ceiling, <b>and</b> re-parent. The re-parent case
 * must validate every descendant, not just the moved node: moving a subtree under a
 * narrower ancestor would otherwise break the subset property retroactively for nodes the
 * caller never mentioned.
 *
 * <p>Note that an {@code UNBOUNDED} child under a {@code BOUNDED} parent is a violation —
 * {@code UNBOUNDED} is the top element of the subset lattice.
 */
public class OrgNodeCeilingNotSubsetException extends RuntimeException {

    public OrgNodeCeilingNotSubsetException(String nodeId, String parentId) {
        super("org node " + nodeId + "'s ceiling is not a subset of its parent " + parentId + "'s ceiling");
    }
}
