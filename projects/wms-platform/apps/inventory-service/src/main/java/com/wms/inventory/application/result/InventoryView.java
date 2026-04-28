package com.wms.inventory.application.result;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-side projection of an {@link com.wms.inventory.domain.model.Inventory}
 * row, enriched with display fields from {@link com.wms.inventory.domain.model.masterref.LocationSnapshot}
 * and friends. Returned by the query services and rendered into the REST
 * response shape declared in
 * {@code specs/contracts/http/inventory-service-api.md} §1.
 *
 * <p>{@code locationCode}, {@code skuCode}, and {@code lotNo} are nullable to
 * tolerate a master-snapshot startup race — the consumer eventually
 * back-fills them, and the REST layer will surface the null until then.
 */
public record InventoryView(
        UUID id,
        UUID warehouseId,
        UUID locationId,
        String locationCode,
        UUID skuId,
        String skuCode,
        UUID lotId,
        String lotNo,
        int availableQty,
        int reservedQty,
        int damagedQty,
        int onHandQty,
        Instant lastMovementAt,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
}
