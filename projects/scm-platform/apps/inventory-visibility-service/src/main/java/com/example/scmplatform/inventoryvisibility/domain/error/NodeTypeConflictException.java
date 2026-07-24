package com.example.scmplatform.inventoryvisibility.domain.error;

import com.example.scmplatform.inventoryvisibility.domain.node.NodeType;

/**
 * Raised when a caller attempts to register a node type onto an external id that
 * is already registered under a **different** {@link NodeType} — e.g. re-registering
 * a wms-auto-registered warehouse's {@code nodeExternalId} as THIRD_PARTY_LOGISTICS
 * (ADR-MONO-054 §D2 / TASK-SCM-BE-046). A repeat registration of the *same* type is
 * NOT this exception — that path is idempotent (find-or-register, no-op).
 *
 * <p>Reused (via the plain-message constructor) by
 * {@code InventoryVisibilityApplicationService#applyThirdPartyObservedStock} for the
 * analogous conflict when an existing node targeted by an observation is not
 * THIRD_PARTY_LOGISTICS, or belongs to a different tenant (ADR-MONO-054 §D4 /
 * TASK-SCM-BE-047) — same NODE_TYPE_CONFLICT semantics (an operation requires a
 * specific node identity/type and the resolved node does not match), same 409.
 *
 * <p>Error code: NODE_TYPE_CONFLICT (scm.md Standard Error Codes) — 409.
 */
public class NodeTypeConflictException extends RuntimeException {
    public NodeTypeConflictException(String nodeExternalId, NodeType existingType, NodeType requestedType) {
        super("Inventory node externalId=" + nodeExternalId + " is already registered as "
                + existingType + "; cannot re-register as " + requestedType);
    }

    /** Plain-message constructor for conflict scenarios that don't fit the register-time shape above. */
    public NodeTypeConflictException(String message) {
        super(message);
    }
}
