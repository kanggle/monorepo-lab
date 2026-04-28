package com.wms.inventory.domain.model;

/**
 * {@link InventoryMovement} types. Authoritative reference:
 * {@code specs/services/inventory-service/domain-model.md} §2.
 */
public enum MovementType {
    RECEIVE,
    RESERVE,
    RELEASE,
    CONFIRM,
    ADJUSTMENT,
    TRANSFER_OUT,
    TRANSFER_IN,
    DAMAGE_MARK,
    DAMAGE_WRITE_OFF
}
