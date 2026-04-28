package com.wms.inventory.application.query;

import com.wms.inventory.domain.exception.InventoryValidationException;
import com.wms.inventory.domain.model.Bucket;
import com.wms.inventory.domain.model.MovementType;
import com.wms.inventory.domain.model.ReasonCode;
import java.time.Instant;
import java.util.UUID;

/**
 * Filter parameters for {@code GET /api/v1/inventory/{id}/movements} and
 * {@code GET /api/v1/inventory/movements}.
 *
 * <p>For the cross-row variant ({@link #inventoryId} = null), the application
 * service requires {@link #occurredAfter} to prevent unbounded scans — the
 * factory guard surfaces a {@code 400 VALIDATION_ERROR} early.
 */
public record MovementListCriteria(
        UUID inventoryId,
        UUID locationId,
        UUID skuId,
        MovementType movementType,
        Bucket bucket,
        ReasonCode reasonCode,
        Instant occurredAfter,
        Instant occurredBefore,
        int page,
        int size
) {
    public MovementListCriteria {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size <= 0 || size > 100) {
            throw new IllegalArgumentException("size must be in (0, 100]");
        }
        if (inventoryId == null && occurredAfter == null) {
            throw new InventoryValidationException(
                    "occurredAfter is required when inventoryId is absent");
        }
    }
}
