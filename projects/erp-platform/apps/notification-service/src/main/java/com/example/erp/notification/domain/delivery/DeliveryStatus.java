package com.example.erp.notification.domain.delivery;

/**
 * State of a {@link NotificationDelivery}. Terminal: {@link #DELIVERED} and
 * {@link #FAILED} (architecture.md § Delivery model). Renamed from the wms
 * notification-service {@code SUCCEEDED} to {@code DELIVERED} for the in-app
 * increment.
 */
public enum DeliveryStatus {

    /** Awaiting first attempt or a scheduled retry (v2 external path). */
    PENDING,
    /** Delivered (v1 IN_APP: the persist itself; v2 external: vendor accepted). */
    DELIVERED,
    /** Permanent failure — retry budget exhausted or permanent error (v2). */
    FAILED;

    public boolean isTerminal() {
        return this == DELIVERED || this == FAILED;
    }
}
