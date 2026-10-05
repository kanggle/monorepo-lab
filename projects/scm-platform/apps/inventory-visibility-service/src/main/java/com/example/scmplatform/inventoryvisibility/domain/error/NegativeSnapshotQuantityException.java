package com.example.scmplatform.inventoryvisibility.domain.error;

import java.math.BigDecimal;

/**
 * Raised by {@link com.example.scmplatform.inventoryvisibility.domain.snapshot.InventorySnapshot#applyConfirmedDecrement}
 * (TASK-MONO-762 AC-0 ⓐ) when an outbound-confirmation decrement would make the stored
 * on-hand quantity negative.
 *
 * <p>Unlike {@code InventorySnapshot#applyDelta} (used by {@code wms.inventory.adjusted.v1},
 * which floors a subtraction at zero), AC-0 deliberately does NOT clamp here: a confirmed
 * decrement exceeding the stored quantity means this read-model's view is already wrong
 * (duplicate/out-of-order delivery, or a missed upstream event), and clamping would quietly
 * paper over that instead of surfacing it. The consumer lets this propagate so
 * {@code @RetryableTopic} retries 3× before routing to the DLT — same disposition as
 * {@link InventorySnapshotNotFoundException}.
 */
public class NegativeSnapshotQuantityException extends RuntimeException {
    public NegativeSnapshotQuantityException(String nodeId, String sku,
                                               BigDecimal currentQuantity, BigDecimal decrement) {
        super("Outbound confirmation decrement would go negative: nodeId=" + nodeId
                + " sku=" + sku + " currentQuantity=" + currentQuantity
                + " decrement=" + decrement);
    }
}
