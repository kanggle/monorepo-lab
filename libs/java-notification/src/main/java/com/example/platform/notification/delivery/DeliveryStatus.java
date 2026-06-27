package com.example.platform.notification.delivery;

/**
 * Lifecycle state of a {@link DeliveryRecord}. Lifted from the wms Category-C
 * reference ({@code com.wms.notification.domain.delivery.DeliveryStatus}).
 *
 * <p>Terminal states are {@link #SUCCEEDED} and {@link #FAILED}; once terminal a
 * record is immutable (any further transition raises
 * {@link DeliveryStateTransitionInvalidException}).
 */
public enum DeliveryStatus {

    /** Awaiting first attempt or a scheduled retry. */
    PENDING,
    /** Vendor accepted the message. */
    SUCCEEDED,
    /** Permanent failure — retry budget exhausted, or a do-not-retry vendor response. */
    FAILED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED;
    }
}
