package com.example.scmplatform.inventoryvisibility.domain.error;

import com.example.scmplatform.inventoryvisibility.domain.node.NodeType;

/**
 * Raised when a caller attempts to register a node type onto an external id that
 * is already registered under a **different** {@link NodeType} — e.g. re-registering
 * a wms-auto-registered warehouse's {@code nodeExternalId} as THIRD_PARTY_LOGISTICS
 * (ADR-MONO-054 §D2 / TASK-SCM-BE-046). A repeat registration of the *same* type is
 * NOT this exception — that path is idempotent (find-or-register, no-op).
 *
 * <p>Error code: NODE_TYPE_CONFLICT (scm.md Standard Error Codes) — 409.
 */
public class NodeTypeConflictException extends RuntimeException {
    public NodeTypeConflictException(String nodeExternalId, NodeType existingType, NodeType requestedType) {
        super("Inventory node externalId=" + nodeExternalId + " is already registered as "
                + existingType + "; cannot re-register as " + requestedType);
    }
}
