package com.example.account.domain.orgnode;

/**
 * TASK-BE-491 (ADR-MONO-047 § D4, invariant I1): the requested parent would introduce a
 * cycle — either self-parent, or a parent that is a descendant of the node being moved.
 * Surfaces as 422 {@code ORG_NODE_CYCLE}.
 */
public class OrgNodeCycleException extends RuntimeException {

    public OrgNodeCycleException(String nodeId, String parentId) {
        super("org node " + nodeId + " cannot be parented to " + parentId + ": introduces a cycle");
    }
}
