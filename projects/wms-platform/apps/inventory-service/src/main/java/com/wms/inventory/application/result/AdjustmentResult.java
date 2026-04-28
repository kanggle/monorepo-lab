package com.wms.inventory.application.result;

/**
 * Outcome of an adjustment / mark-damaged / write-off-damaged. Carries the
 * persisted {@link AdjustmentView} plus the post-mutation Inventory snapshot
 * so the REST layer can render the contract response shape (which embeds
 * both).
 */
public record AdjustmentResult(
        AdjustmentView adjustment,
        InventorySnapshot inventory
) {

    public record InventorySnapshot(
            java.util.UUID id,
            int availableQty,
            int reservedQty,
            int damagedQty,
            int onHandQty,
            long version
    ) {
    }
}
