package com.example.account.domain.orgnode;

/**
 * TASK-BE-491 (ADR-MONO-047 § D4, invariant I4): a node with children or with attached
 * tenants may not be deleted — doing so would orphan the FK / strand service-tenants.
 * Surfaces as 422 {@code ORG_NODE_NOT_EMPTY}.
 */
public class OrgNodeNotEmptyException extends RuntimeException {

    public OrgNodeNotEmptyException(String nodeId, long childCount, long tenantCount) {
        super("org node " + nodeId + " is not empty: " + childCount + " child node(s), "
                + tenantCount + " tenant(s)");
    }
}
