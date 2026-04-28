package com.wms.inventory.domain.model;

/**
 * Closed v1 enum for {@link StockTransfer} reason codes. Distinct from
 * {@link ReasonCode} on {@link InventoryMovement}: the Movement's
 * {@code reasonCode} for the two transfer legs is always
 * {@link ReasonCode#TRANSFER_INTERNAL}; this enum classifies the parent
 * {@code StockTransfer} aggregate at a higher granularity for reporting.
 *
 * <p>Authoritative reference:
 * {@code specs/services/inventory-service/domain-model.md} §5.
 */
public enum TransferReasonCode {
    TRANSFER_INTERNAL,
    REPLENISHMENT,
    CONSOLIDATION
}
