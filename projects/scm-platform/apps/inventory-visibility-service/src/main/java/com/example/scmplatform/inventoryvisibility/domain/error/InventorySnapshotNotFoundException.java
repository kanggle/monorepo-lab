package com.example.scmplatform.inventoryvisibility.domain.error;

/**
 * Raised by {@code applyInventoryConfirmed} (TASK-MONO-762 AC-0 ⓐ) when
 * {@code wms.inventory.confirmed.v1} references a node/SKU pair this read-model has no
 * snapshot row for — either the node was never auto-registered (no
 * {@code wms.inventory.received.v1} has landed yet for it) or a row exists for the node but
 * not for this SKU.
 *
 * <p>Deliberately NOT auto-created: AC-0 chose retry→DLT over creating a negative/zero
 * row, because this case is the out-of-order symptom (confirmed arriving before received,
 * e.g. partition skew) — silently creating a zero/negative row would hide exactly the
 * discrepancy this ticket exists to surface. The consumer lets this propagate as an
 * unchecked exception so {@code @RetryableTopic} retries 3× before routing to the DLT,
 * giving the out-of-order received event a window to land first.
 */
public class InventorySnapshotNotFoundException extends RuntimeException {
    public InventorySnapshotNotFoundException(String nodeExternalId, String sku) {
        super("No inventory snapshot for nodeExternalId=" + nodeExternalId + " sku=" + sku
                + " — confirmation cannot decrement a row that does not exist "
                + "(out-of-order: confirmed arrived before received)");
    }
}
