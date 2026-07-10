package com.example.account.application.exception;

/** TASK-BE-491 (ADR-MONO-047): no org node with this id. Surfaces as 404 {@code ORG_NODE_NOT_FOUND}. */
public class OrgNodeNotFoundException extends RuntimeException {

    private final String orgNodeId;

    public OrgNodeNotFoundException(String orgNodeId) {
        super("Org node not found: " + orgNodeId);
        this.orgNodeId = orgNodeId;
    }

    public String getOrgNodeId() {
        return orgNodeId;
    }
}
