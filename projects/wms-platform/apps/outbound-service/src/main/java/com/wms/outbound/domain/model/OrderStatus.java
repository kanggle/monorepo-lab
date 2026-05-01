package com.wms.outbound.domain.model;

/**
 * State enum for the {@code Order} aggregate root.
 *
 * <p>Authoritative reference:
 * {@code specs/services/outbound-service/state-machines/order-status.md}.
 *
 * <p>Terminal states: {@link #SHIPPED}, {@link #CANCELLED}, {@link #BACKORDERED}.
 */
public enum OrderStatus {
    RECEIVED,
    PICKING,
    PICKED,
    PACKING,
    PACKED,
    SHIPPED,
    CANCELLED,
    BACKORDERED
}
