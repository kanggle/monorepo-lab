package com.wms.inventory.domain.model;

public enum ReleasedReason {
    /** Caller-initiated release (e.g., outbound saga cancels picking). */
    CANCELLED,
    /** Set by the {@code ReservationExpiryJob} when the TTL elapses. */
    EXPIRED,
    /** {@code INVENTORY_ADMIN} manual release via REST. */
    MANUAL
}
