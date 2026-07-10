package com.example.account.domain.orgnode;

/**
 * TASK-BE-491 (ADR-MONO-047 § D4, invariant I2): the requested depth exceeds
 * {@link OrgNode#MAX_DEPTH} (root = 1). Surfaces as 422 {@code ORG_NODE_DEPTH_EXCEEDED}.
 *
 * <p>Enforced on <b>both</b> create and re-parent: a deep chain can otherwise be
 * assembled by moving subtrees rather than by creating nodes one level at a time.
 */
public class OrgNodeDepthExceededException extends RuntimeException {

    private final int requestedDepth;
    private final int maxDepth;

    public OrgNodeDepthExceededException(int requestedDepth, int maxDepth) {
        super("org node depth " + requestedDepth + " exceeds the maximum of " + maxDepth);
        this.requestedDepth = requestedDepth;
        this.maxDepth = maxDepth;
    }

    public int getRequestedDepth() {
        return requestedDepth;
    }

    public int getMaxDepth() {
        return maxDepth;
    }
}
