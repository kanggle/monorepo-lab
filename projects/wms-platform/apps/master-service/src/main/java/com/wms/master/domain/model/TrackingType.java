package com.wms.master.domain.model;

/**
 * Inventory tracking strategy for an SKU. Immutable post-create because
 * downstream services (inventory, ops) cache this and the {@code NONE → LOT}
 * transition would break invariants for existing stock.
 *
 * <ul>
 *   <li>{@code NONE} — aggregate stock only, no lot/batch breakdown
 *   <li>{@code LOT} — stock is tracked per lot, enabling expiry / recall
 * </ul>
 */
public enum TrackingType {
    NONE,
    LOT
}
