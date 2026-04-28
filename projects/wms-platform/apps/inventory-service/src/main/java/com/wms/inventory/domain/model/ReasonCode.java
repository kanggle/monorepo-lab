package com.wms.inventory.domain.model;

/**
 * Closed catalog of {@link InventoryMovement} reason codes for v1.
 *
 * <p>Modelled as a Java enum because v1 ships with a fixed set; future ops-time
 * additions move this to a Flyway-managed reference table per
 * {@code specs/services/inventory-service/domain-model.md} §2.
 */
public enum ReasonCode {
    PUTAWAY,
    PICKING,
    PICKING_CANCELLED,
    PICKING_EXPIRED,
    SHIPPING_CONFIRMED,
    ADJUSTMENT_CYCLE_COUNT,
    ADJUSTMENT_DAMAGE,
    ADJUSTMENT_LOSS,
    ADJUSTMENT_FOUND,
    ADJUSTMENT_RECLASSIFY,
    TRANSFER_INTERNAL,
    DAMAGE_WRITE_OFF
}
