package com.wms.inventory.application.query;

import com.wms.inventory.domain.model.ReasonCode;
import java.time.Instant;
import java.util.UUID;

/**
 * Query criteria for listing stock adjustments. Authoritative reference:
 * {@code specs/contracts/http/inventory-service-api.md} §2.5 / §2.6.
 *
 * <p>If {@code inventoryId} is {@code null}, {@code createdAfter} must be
 * non-null — the controller enforces this and surfaces a
 * {@code VALIDATION_ERROR} otherwise.
 */
public record AdjustmentListCriteria(
        UUID inventoryId,
        ReasonCode reasonCode,
        Instant createdAfter,
        Instant createdBefore,
        int page,
        int size
) {
}
