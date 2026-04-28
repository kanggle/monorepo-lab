package com.wms.inventory.application.result;

import java.util.UUID;

/**
 * Outcome of a successful stock transfer. Carries both endpoint snapshots
 * for the REST response.
 */
public record TransferResult(
        TransferView transfer,
        Endpoint source,
        Endpoint target
) {

    public record Endpoint(
            UUID inventoryId,
            int availableQty,
            int reservedQty,
            int damagedQty,
            long version,
            boolean wasCreated
    ) {
    }
}
