package com.wms.notification.domain.delivery;

/**
 * State of a {@link NotificationDelivery}. Terminal: {@link #SUCCEEDED} and
 * {@link #FAILED}. Architecture.md § State Machine.
 */
public enum DeliveryStatus {

    /** Awaiting first attempt or scheduled retry. */
    PENDING,
    /** Vendor accepted (Slack 2xx). */
    SUCCEEDED,
    /** Permanent failure — retry budget exhausted or 4xx terminal. */
    FAILED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED;
    }
}
