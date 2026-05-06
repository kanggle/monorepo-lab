package com.example.scmplatform.inventoryvisibility.domain.error;

/**
 * S5 — raised when a node cannot be reached (no events ever received).
 * Error code: NODE_UNREACHABLE (scm.md Standard Error Codes).
 */
public class NodeUnreachableException extends RuntimeException {
    public NodeUnreachableException(String nodeId) {
        super("Inventory node unreachable (no events received): " + nodeId);
    }
}
