package com.wms.inventory.domain.model;

/**
 * Quantity bucket on an {@link Inventory} row. Authoritative reference:
 * {@code specs/services/inventory-service/domain-model.md} §1.
 */
public enum Bucket {
    AVAILABLE,
    RESERVED,
    DAMAGED
}
