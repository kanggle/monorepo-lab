package com.wms.inventory.application.query;

import java.util.UUID;

/**
 * Filter parameters for {@code GET /api/v1/inventory}. All fields optional.
 */
public record InventoryListCriteria(
        UUID warehouseId,
        UUID locationId,
        UUID skuId,
        UUID lotId,
        Boolean hasStock,
        Integer minAvailable,
        int page,
        int size,
        String sort
) {
    public InventoryListCriteria {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size <= 0 || size > 100) {
            throw new IllegalArgumentException("size must be in (0, 100], got: " + size);
        }
        if (sort == null || sort.isBlank()) {
            sort = "updatedAt,desc";
        }
    }
}
